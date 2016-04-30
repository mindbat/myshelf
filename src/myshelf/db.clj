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
