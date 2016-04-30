(ns myshelf.models.user
  (:require [clojure.java.jdbc :as sql]
            [myshelf.db :refer [db-spec]]))

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
  [& {:keys [handle last-tweet goodreads-id
             oauth-token oauth-token-secret]}]
  (sql/with-db-transaction [conn db-spec]
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
  [user-map]
  (sql/update! db-spec
               :users
               user-map
               ["user_id = ?" (:user_id user-map)]))
