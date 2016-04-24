(ns myshelf.worker
  (:require [cheshire.core :as json]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.core :as lc]
            [langohr.consumers :as lcs]
            [langohr.queue :as lq]
            [myshelf.auth :refer [find-approval-uri
                                  get-access-token
                                  get-consumer
                                  get-request-token
                                  get-user-id]]
            [myshelf.books :refer [find-book-by-title]]
            [myshelf.db :as db]
            [myshelf.friends :refer [get-user-friends]]
            [myshelf.rank :refer [rank-books]]
            [myshelf.shelves :refer [add-book-to-shelf
                                     get-all-books-on-shelf]])
  (:gen-class))

(def default-exchange "")
(def connection-params (if-let [uri (System/getenv "CLOUDAMQP_URL")]
                         {:uri uri}
                         {:host "localhost"
                          :port 5672
                          :username "guest"
                          :password "guest"
                          :vhost "/"}))
(def worker-queue "myshelf.worker")
(def reply-queue "myshelf.reply")
(def goodreads-creds (atom {}))
(def goodreads-key (System/getenv "GOODREADS_KEY"))
(def goodreads-secret (System/getenv "GOODREADS_SECRET"))
(def goodreads-token (System/getenv "GOODREADS_TOKEN"))
(def goodreads-token-secret (System/getenv "GOODREADS_TOKEN_SECRET"))

(defn add-access-token
  [user-handle {:keys [consumer request-token] :as creds}]
  (try
    (let [access-token (or (db/get-access-token user-handle)
                           (get-access-token consumer
                                             request-token))
          user-id (get-user-id consumer access-token)
          new-creds (merge creds {:user-id user-id
                                  :access-token access-token})]
      (when-not (db/find-by-id user-id)
        (db/insert-user user-id user-handle access-token))
      (swap! goodreads-creds merge {(keyword user-handle) new-creds}))
    (catch Exception ex
      false)))

(defn goodreads-access-for-user?
  [user-handle]
  (let [creds ((keyword user-handle) @goodreads-creds)]
    (or (:access-token creds)
        (add-access-token user-handle creds))))

(defn request-goodreads-access
  [channel user-handle]
  (let [creds ((keyword user-handle) @goodreads-creds)
        consumer (or (:consumer creds)
                     (get-consumer goodreads-key goodreads-secret))
        request-token (or (:request-token creds)
                          (get-request-token consumer))
        approval-uri (find-approval-uri consumer request-token)]
    (swap! goodreads-creds merge {(keyword user-handle)
                                  {:consumer consumer
                                   :request-token request-token}})
    (lb/publish channel default-exchange reply-queue
                (json/generate-string
                 {:user-handle user-handle
                  :msg "Please hit the following url in your browser"
                  :url approval-uri}))))

(defn find-book
  [channel user-handle consumer access-token title]
  (let [books (find-book-by-title consumer access-token title)]
    (lb/publish channel default-exchange reply-queue
                (json/generate-string
                 {:user-handle user-handle
                  :sent-cmd "find-book"
                  :results books}))))

(defn add-book
  [channel user-handle consumer access-token book-id shelf]
  (let [result (add-book-to-shelf consumer access-token book-id shelf)]
    (lb/publish channel default-exchange reply-queue
                (json/generate-string
                 {:user-handle user-handle
                  :sent-cmd "add-book"
                  :sent-args [book-id shelf]
                  :results result}))))

(defn rank-books-on-shelf
  [consumer access-token user-id shelf]
  (let [books (get-all-books-on-shelf consumer access-token
                                      user-id shelf)
        friends (get-user-friends consumer access-token user-id)]
    (rank-books consumer access-token books friends)))

(defn rank-to-read-books
  [channel user-handle consumer access-token user-id]
  (let [results (take 10 (rank-books-on-shelf consumer access-token
                                              user-id "to-read"))]
    (lb/publish channel default-exchange reply-queue
                (json/generate-string
                 {:user-handle user-handle
                  :sent-cmd "rank-books"
                  :results results}))))

(defn process-command
  [channel user-handle cmd [arg-1 arg-2]]
  (let [handle (keyword user-handle)
        {:keys [consumer access-token user-id]} (handle @goodreads-creds)]
    (cond
      (= "find" cmd) (find-book channel
                                user-handle
                                consumer
                                access-token
                                arg-1)
      (= "rank" cmd) (rank-to-read-books channel user-handle
                                         consumer access-token
                                         user-id)
      (= "add" cmd) (add-book channel user-handle
                              consumer access-token
                              arg-1 "to-read"))))

(defn handle-message
  [channel metadata body]
  (let [{:keys [user-handle cmd args]} (json/parse-string
                                        (String. body "UTF-8")
                                        true)]
    (if (goodreads-access-for-user? user-handle)
      (process-command channel user-handle cmd args)
      (request-goodreads-access channel user-handle))))

(defn declare-reply-queue
  [channel]
  (lq/declare channel reply-queue {:auto-delete false}))

(defn get-reply
  "Pull a single message off of the reply queue"
  [channel]
  (declare-reply-queue channel)
  (when-let [reply (lb/get channel reply-queue)]
    (-> reply
        second
        (String. "UTF-8")
        (json/parse-string true))))

(defn -main [& args]
  (let [conn (lc/connect connection-params)
        channel (lch/open conn)]
    (lq/declare channel worker-queue {:auto-delete false})
    (lcs/subscribe channel worker-queue handle-message {:auto-ack true})))
