(ns myshelf.test.db
  (:require [clojure.test :refer :all]
            [myshelf.db :refer :all]))

(use-fixtures :once
  migrate-db-fixture
  clean-db-fixture)

(deftest t-friends-crud
  ;; insert a new friends list
  (let [user-id "42"
        friends ["1337" "255"]]
    ;; should have returned the inserted data
    (is (= {:user-id user-id
            :friends friends}
           (insert-friends user-id friends)))
    ;; should be able to pull the friends back out
    (is (= friends
           (pull-friends user-id)))
    ;; update our friends list
    (let [new-friends ["1337" "255" "13"]]
      ;; should have gotten back the updated list
      (is (= {:user-id user-id
              :friends new-friends}
             (update-friends user-id new-friends)))
      ;; should be able to fetch the updated list back out
      (is (= new-friends
             (pull-friends user-id))))))
