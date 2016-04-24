(ns myshelf.db
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :as str]
            [migratus.core :as migratus])
  (:import (org.postgresql.util PGobject)))

(def db-spec (or (System/getenv "DATABASE_URL")
                 "postgresql://localhost:5432/myshelf"))

(def migratus-config {:store :database
                      :migration-dir "migrations"
                      :db db-spec})

(defn migrate-db!
  []
  (migratus/migrate migratus-config))

(defn clean-db-fixture
  [f]
  (sql/execute! db-spec ["TRUNCATE TABLE users"])
  (f))

(defn migrate-db-fixture
  [f]
  (migrate-db!)
  (f))

(defn vec->pgarray
  [conn v]
  (.createArrayOf
   (sql/db-find-connection conn)
   "text"
   (into-array String v)))

(defn pgarray->vec
  [pga]
  (when pga
    (vec (.getArray pga))))

(defn fix-friends
  [result]
  (merge result
         {:user-id (:user_id result)
          :friends (pgarray->vec (:friends result))}))

(defn find-by-id
  [user-id]
  (first (sql/query db-spec
                    ["SELECT * FROM users WHERE user_id = ?"
                     user-id]
                    :row-fn fix-friends)))

(defn find-by-handle
  [user-handle]
  (first (sql/query db-spec
                    ["SELECT * FROM users WHERE handle = ?"
                     user-handle]
                    :row-fn fix-friends)))

(defn pull-friends
  [user-id]
  (-> (find-by-id user-id)
      :friends))

(defn update-friends
  [user-id friends-list]
  (sql/with-db-connection [conn db-spec]
    (when (sql/update! conn :users
                       {:friends (vec->pgarray conn friends-list)}
                       ["user_id = ?" user-id])
      (find-by-id user-id))))

(defn find-last-tweet
  [screen-name]
  (first (sql/query db-spec
                    ["SELECT * FROM user_tweets WHERE screen_name = ?"
                     screen-name])))

(defn update-last-tweet
  [screen-name tweet-id]
  (if (find-last-tweet screen-name)
    (sql/update! db-spec :user_tweets
                 {:last_tweet tweet-id}
                 ["screen_name = ?" screen-name])
    (sql/insert! db-spec :user_tweets
                 {:screen_name screen-name
                  :last_tweet tweet-id})))

(defn insert-user
  [user-id user-handle {:keys [oauth_token oauth_token_secret]}]
  (-> (sql/insert! db-spec
                   :users
                   {:user_id user-id
                    :handle user-handle
                    :access_token oauth_token
                    :access_token_secret oauth_token_secret})
      first))

(defn get-access-token
  [user-handle]
  (let [{:keys [access_token access_token_secret]} (find-by-handle
                                                    user-handle)]
    {:oauth_token access_token
     :oauth_token_secret access_token_secret}))
