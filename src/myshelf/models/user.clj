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

(defn find-by-user-id
  ([user-id]
   (find-by-user-id db-spec user-id))
  ([db-conn user-id]
   (first (sql/query db-conn
                     ["SELECT * FROM users WHERE user_id = ?"
                      user-id]))))

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
   (when-not (:user_id user-map)
     (throw (Exception. "Must pass user-id")))
   (let [user-map (select-keys user-map [:handle
                                         :user_id
                                         :goodreads_id
                                         :oauth_token
                                         :oauth_token_secret
                                         :last_tweet])]
     (sql/with-db-transaction [t db-conn]
       (when-not (find-by-user-id t (:user_id user-map))
         (throw (Exception. "User does not exist")))
       (when (:handle user-map)
         (let [existing (find-by-handle t (:handle user-map))]
           (when (and existing
                      (not= (:user_id existing)
                            (:user_id user-map)))
             (throw (Exception. "User with that handle already exists")))))
       (when (:goodreads_id user-map)
         (let [existing (find-by-goodreads-id t (:goodreads_id user-map))]
           (when (and existing
                      (not= (:user_id existing)
                            (:user_id user-map)))
             (throw (Exception.
                     "User with that goodreads-id already exists")))))
       (sql/update! t
                    :users
                    (merge user-map
                           {:updated_at (now-timestamp)})
                    ["user_id = ?" (:user_id user-map)])))))

(defn update-last-tweet
  [handle last-tweet]
  (sql/with-db-transaction [db-conn db-spec]
    (if-let [current-user (find-by-handle db-conn handle)]
      (update-user db-conn (merge current-user
                                  {:last_tweet last-tweet}))
      (create-user :db-conn db-conn
                   :handle handle
                   :last-tweet last-tweet))))

(defn update-oauth
  [handle goodreads-id oauth-token oauth-token-secret]
  (sql/with-db-transaction [db-conn db-spec]
    (if-let [current-user (find-by-handle db-conn handle)]
      (update-user db-conn (merge current-user
                                  {:goodreads_id goodreads-id
                                   :oauth_token oauth-token
                                   :oauth_token_secret oauth-token-secret}))
      (create-user :db-conn db-conn
                   :goodreads-id goodreads-id
                   :handle handle
                   :oauth-token oauth-token
                   :oauth-token-secret oauth-token-secret))))
