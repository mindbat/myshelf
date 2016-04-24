(ns myshelf.test.db
  (:require [clojure.test :refer :all]
            [myshelf.db :refer :all]))

(use-fixtures :once
  migrate-db-fixture
  clean-db-fixture)

(deftest t-friends-crud
  ;; create a new user
  (let [user-id "42"
        user-handle "ford prefect"
        access-token {:oauth_token "pinfeathers"
                      :oauth_token_secret "gollyfluff"}]
    (insert-user user-id user-handle access-token)
    ;; insert a new friends list
    (let [friends ["1337" "255"]]
      ;; should have returned the inserted data
      (is (= {:user-id user-id
              :friends friends}
             (update-friends user-id friends)))
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
               (pull-friends user-id)))))))
