(ns myshelf.models.user
  (:require [clojure.java.jdbc :as sql]
            [myshelf.db :refer [db-spec
                                now-timestamp]]))

(defn find-by-goodreads-id
  ([goodreads-id]
   (find-by-goodreads-id db-spec goodreads-id))
  ([db-conn goodreads-id]
   (first (sql/query db-conn
                     ["SELECT * FROM users WHERE goodreads_id = ?"
                      goodreads-id]))))

(defn find-by-handle
  ([handle]
   (find-by-handle db-spec handle))
  ([db-conn handle]
   (first (sql/query db-conn
                     ["SELECT * FROM users WHERE handle = ?"
                      handle]))))

(defn create-user
  [& {:keys [db-conn handle last-tweet goodreads-id
             oauth-token oauth-token-secret]}]
  (sql/with-db-transaction [conn (or db-conn db-spec)]
    (when (find-by-handle conn handle)
      (throw (Exception. "User with that handle already exists")))
    (when (find-by-goodreads-id conn goodreads-id)
      (throw (Exception. "User with that goodreads-id already exists")))
    (when (and (empty? handle)
               (empty? goodreads-id))
      (throw (Exception. "Must pass goodreads-id or handle")))
    (sql/insert! conn
                 :users
                 {:goodreads_id goodreads-id
                  :handle handle
                  :last_tweet last-tweet
                  :oauth_token oauth-token
                  :oauth_token_secret oauth-token-secret})))

(defn update-user
  ([user-map]
   (update-user db-spec user-map))
  ([db-conn user-map]
   (sql/update! db-conn
                :users
                (merge user-map
                       {:updated_at (now-timestamp)})
                ["user_id = ?" (:user_id user-map)])))

(defn update-last-tweet
  [handle last-tweet]
  (sql/with-db-transaction [db-conn db-spec]
    (if-let [current-user (find-by-handle db-conn handle)]
      (update-user db-conn (merge current-user
                                  {:last_tweet last-tweet}))
      (create-user :db-conn db-conn
                   :handle handle
                   :last-tweet last-tweet))))
