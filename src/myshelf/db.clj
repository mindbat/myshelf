(ns myshelf.db
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :as str])
  (:import (org.postgresql.util PGobject)))

(def db-spec (or (System/getenv "DATABASE_URL")
                 "postgresql://localhost:5432/myshelf"))

(defn vec->pgarray
  [conn v]
  (.createArrayOf
   (sql/db-find-connection conn)
   "text"
   (into-array String v)))

(defn pgarray->vec
  [pga]
  (vec (.getArray pga)))

(defn fix-friends
  [result]
  {:user-id (:user_id result)
   :friends (pgarray->vec (:friends result))})

(defn find-by-id
  [user-id]
  (sql/query db-spec
             ["SELECT * FROM user_friends WHERE user_id = ?"
              user-id]
             :row-fn fix-friends))

(defn pull-friends
  [user-id]
  (-> (find-by-id user-id)
      first
      :friends))

(defn update-friends
  [user-id friends-list]
  (sql/with-db-connection [conn db-spec]
    (when (sql/update! conn :user_friends
                       {:friends (vec->pgarray conn friends-list)}
                       ["user_id = ?" user-id])
      (first (find-by-id user-id)))))

(defn insert-friends
  [user-id friends-list]
  (sql/with-db-connection [conn db-spec]
    (-> (sql/insert! conn :user_friends
                     {:user_id user-id
                      :friends (vec->pgarray conn friends-list)})
        first
        fix-friends)))

(defn clean-db-fixture
  [f]
  (sql/execute! db-spec ["TRUNCATE TABLE user_friends"])
  (f))
