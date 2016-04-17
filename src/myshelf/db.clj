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
  {:user-handle (:user_handle result)
   :user-id (:user_id result)
   :friends (pgarray->vec (:friends result))})

(defn find-by-handle
  [user-handle]
  (sql/query db-spec
             ["SELECT * FROM user_friends WHERE user_handle = ?"
              user-handle]
             :row-fn fix-friends))

(defn pull-friends
  [user-handle]
  (-> (find-by-handle user-handle)
      first
      :friends))

(defn update-friends
  [user-handle user-id friends-list]
  (sql/with-db-connection [conn db-spec]
    (when (sql/update! conn :user_friends
                       {:friends (vec->pgarray conn friends-list)}
                       ["user_handle = ?" user-handle])
      (first (find-by-handle user-handle)))))

(defn insert-friends
  [user-handle user-id friends-list]
  (sql/with-db-connection [conn db-spec]
    (-> (sql/insert! conn :user_friends
                     {:user_handle user-handle :user_id user-id
                      :friends (vec->pgarray conn friends-list)})
        first
        fix-friends)))
