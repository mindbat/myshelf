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
            [myshelf.books :refer [find-book-by-title-and-author]]
            [myshelf.db :as db]
            [myshelf.friends :refer [get-user-friends]]
            [myshelf.models.user :as user]
            [myshelf.rank :refer [rank-books]]
            [myshelf.shelves :refer [add-book-to-shelf
                                     get-all-books-on-shelf]]))

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
(def goodreads-key (System/getenv "GOODREADS_KEY"))
(def goodreads-secret (System/getenv "GOODREADS_SECRET"))
(def goodreads-token (System/getenv "GOODREADS_TOKEN"))
(def goodreads-token-secret (System/getenv "GOODREADS_TOKEN_SECRET"))

(defn add-access-token
  [user-handle goodreads-creds]
  (try
    (let [{:keys [goodreads_id oauth_token oauth_token_secret]}
          (user/find-by-handle user-handle)]
      (if (and goodreads_id oauth_token oauth_token_secret)
        (let [old-creds ((keyword user-handle) @goodreads-creds)
              access-token {:oauth_token oauth_token
                            :oauth_token_secret oauth_token_secret}
              new-creds (merge old-creds {:user-id goodreads_id
                                          :access-token access-token})]
          (swap! goodreads-creds merge {(keyword user-handle) new-creds}))
        (let [{:keys [consumer request-token] :as old-creds}
              ((keyword user-handle) @goodreads-creds)
              access-token (get-access-token consumer
                                             request-token)
              user-id (get-user-id consumer access-token)
              new-creds (merge old-creds {:user-id user-id
                                          :access-token access-token})]
          (user/update-oauth user-handle
                             user-id
                             (:oauth_token access-token)
                             (:oauth_token_secret access-token))
          (swap! goodreads-creds merge {(keyword user-handle) new-creds}))))
    (catch Exception ex
      false)))

(defn goodreads-access-for-user?
  [goodreads-creds user-handle]
  (let [creds ((keyword user-handle) @goodreads-creds)]
    (or (:access-token creds)
        (add-access-token user-handle goodreads-creds))))

(defn request-goodreads-access
  [channel goodreads-creds user-handle]
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

(defn add-book
  [channel user-handle consumer access-token title author shelf]
  (let [found-book (first (find-book-by-title-and-author consumer
                                                         access-token
                                                         title author))
        result (when found-book
                 (add-book-to-shelf consumer access-token
                                    (:id found-book)
                                    shelf))]
    (lb/publish channel default-exchange reply-queue
                (json/generate-string
                 {:user-handle user-handle
                  :sent-cmd "add-book"
                  :sent-args [author title shelf]
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
  [channel goodreads-creds user-handle cmd [arg-1 arg-2]]
  (let [user (user/find-by-handle user-handle)
        consumer (get-consumer goodreads-key goodreads-secret)
        access-token (select-keys user
                                  [:oauth_token :oauth_token_secret])]
    (cond
      (= "rank" cmd) (rank-to-read-books channel user-handle
                                         consumer access-token
                                         (:goodreads_id user))
      (= "add" cmd) (add-book channel user-handle
                              consumer access-token
                              arg-1 arg-2 "to-read"))))

(defn handle-message
  [goodreads-creds channel metadata body]
  (let [{:keys [user-handle cmd args]} (json/parse-string
                                        (String. body "UTF-8")
                                        true)]
    (if (goodreads-access-for-user? goodreads-creds user-handle)
      (process-command channel goodreads-creds user-handle cmd args)
      (request-goodreads-access channel goodreads-creds user-handle))))

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
