(ns myshelf.test.friends
  (:require [clojure.test :refer :all]
            [myshelf.db :refer [clean-db-fixture
                                insert-friends
                                migrate-db-fixture]]
            [myshelf.friends :refer :all]))

(use-fixtures :once
  migrate-db-fixture
  clean-db-fixture)

(deftest t-user-friends-from-db
  ;; we should use the db values if we have them
  (let [friends ["42" "57" "1337"]
        user-id "7"]
    (insert-friends user-id friends)
    (is (= (set (for [id friends]
                  {:id id}))
           (set (get-user-friends nil nil user-id))))))
