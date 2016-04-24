(ns myshelf.test.worker
  (:require [clojure.test :refer :all]
            [myshelf.auth :refer [get-access-token
                                  get-user-id]]
            [myshelf.db :as db]
            [myshelf.worker :refer :all]))

(use-fixtures :once
  db/migrate-db-fixture)

(use-fixtures :each
  db/clean-db-fixture)

(deftest t-goodreads-access-for-user
  ;; arbitrary handles should be false
  (let [user-handle "prefect"
        user-access-token {:oauth_token "pinfeathers"
                           :oauth_token_secret "gollyfluff"}]
    (is (not (goodreads-access-for-user? user-handle)))
    ;; insert user into db
    (db/insert-user "42" user-handle user-access-token)
    ;; should now return true
    (is (goodreads-access-for-user? user-handle))
    ;; creds should include handle and info
    (let [creds (:prefect @goodreads-creds)]
      (is (= "42"
             (:user-id creds)))
      (is (= user-access-token
             (:access-token creds)))))
  ;; another arbitrary handle should be false
  (let [user-handle "ford"
        user-access-token {:oauth_token "pinfeathers"
                           :oauth_token_secret "gollyfluff"}]
    (is (not (goodreads-access-for-user? user-handle)))
    ;; mock out access token and user id
    (with-redefs [get-access-token (fn [& args] user-access-token)
                  get-user-id (fn [& args] "7")]
      ;; call again; should be true now
      (is (goodreads-access-for-user? user-handle)))
    ;; should have updated db
    (let [user (db/find-by-id "7")]
      (is (= "ford"
             (:handle user)))
      (is (= "pinfeathers"
             (:access_token user)))
      (is (= "gollyfluff"
             (:access_token_secret user))))
    ;; should have updated creds atom
    (let [creds (:ford @goodreads-creds)]
      (is (= "7"
             (:user-id creds)))
      (is (= user-access-token
             (:access-token creds))))))
