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
            [myshelf.books :refer [find-book-by-title]])
  (:gen-class))

(def default-exchange "")
(def worker-queue "myshelf.worker")
(def reply-queue "myshelf.reply")
(def goodreads-creds (atom {}))
(def goodreads-key (atom nil))
(def goodreads-secret (atom nil))

(defn add-access-token
  [user-handle creds]
  (try
    (let [access-token (get-access-token (:consumer creds)
                                         (:request-token creds))
          user-id (get-user-id (:consumer creds) access-token)
          new-creds (merge creds {:user-id user-id
                                  :access-token access-token})]
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
                     (get-consumer @goodreads-key @goodreads-secret))
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

(defn process-command
  [channel user-handle cmd [arg-1 arg-2]]
  (let [handle (keyword user-handle)
        {:keys [consumer access-token user-id]} (handle @goodreads-creds)]
    (cond
      (= "find" cmd) (find-book channel
                                user-handle
                                consumer
                                access-token
                                arg-1))))

(defn handle-message
  [channel metadata body]
  (let [{:keys [user-handle cmd args]} (json/parse-string
                                        (String. body "UTF-8")
                                        true)]
    (if (goodreads-access-for-user? user-handle)
      (process-command channel user-handle cmd args)
      (request-goodreads-access channel user-handle))))

(defn -main [& [key secret]]
  (let [conn (lc/connect)
        channel (lch/open conn)]
    (compare-and-set! goodreads-key nil key)
    (compare-and-set! goodreads-secret nil secret)
    (lq/declare channel worker-queue
                {:auto-delete false})
    (lcs/subscribe channel worker-queue
                   handle-message {:auto-ack true})))
