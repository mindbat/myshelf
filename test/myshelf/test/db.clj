(ns myshelf.test.db
  (:require [clojure.test :refer :all]
            [myshelf.db :refer :all]))

(use-fixtures :once
  migrate-db-fixture
  clean-db-fixture)
