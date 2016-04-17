(ns myshelf.test.db
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as sql]
            [myshelf.db :refer :all]))

(defn clean-db-fixture
  [f]
  (sql/execute! db-spec ["TRUNCATE TABLE user_friends"])
  (f))

(use-fixtures :once
  clean-db-fixture)

(deftest t-friends-crud
  ;; insert a new friends list
  (let [user-handle "laforge"
        user-id "42"
        friends ["1337" "255"]]
    ;; should have returned the inserted data
    (is (= {:user-handle user-handle
            :user-id user-id
            :friends friends}
           (insert-friends user-handle user-id friends)))
    ;; should be able to pull the friends back out
    (is (= friends
           (pull-friends user-handle)))
    ;; update our friends list
    (let [new-friends ["1337" "255" "13"]]
      ;; should have gotten back the updated list
      (is (= {:user-handle user-handle
              :user-id user-id
              :friends new-friends}
             (update-friends user-handle user-id new-friends)))
      ;; should be able to fetch the updated list back out
      (is (= new-friends
             (pull-friends user-handle))))))
