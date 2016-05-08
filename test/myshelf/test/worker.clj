(ns myshelf.test.worker
  (:require [cheshire.core :as json]
            [clojure.test :refer :all]
            [langohr.basic :as lb]
            [myshelf.auth :refer [get-access-token
                                  get-user-id]]
            [myshelf.books :refer [find-book-by-title-and-author]]
            [myshelf.db :as db]
            [myshelf.models.user :as user]
            [myshelf.shelves :refer [add-book-to-shelf]]
            [myshelf.worker :refer :all]))

(use-fixtures :once
  db/migrate-db-fixture)

(use-fixtures :each
  db/clean-db-fixture)

(deftest t-add-access-token
  ;; no mocking; get-access-token and get-user-id should throw
  ;; create new user
  (let [handle "merlin"
        handle-key (keyword handle)]
    (user/create-user :handle handle :last-tweet 256)
    ;; add-access-token should return false
    (is (not (add-access-token handle (atom {}))))
    ;; user in db should not have oauth
    (let [db-user (user/find-by-handle handle)]
      (is (nil? (:oauth_token db-user)))
      (is (nil? (:oauth_token_secret db-user))))
    ;; mock out get-access-token and get-user-id to pass
    (let [access-token {:oauth_token "token"
                        :oauth_token_secret "secret"}
          user-id "1337"]
      (with-redefs [get-access-token (fn [& args] access-token)
                    get-user-id (fn [& args] user-id)]
        ;; add-access-token should return updated creds
        (let [ret (add-access-token handle (atom {}))]
          (is ret)
          (is (= access-token (:access-token (handle-key ret))))
          (is (= user-id (:user-id (handle-key ret))))))
      ;; user in db should have oauth
      (let [db-user (user/find-by-handle handle)]
        (is (= (:oauth_token access-token)
               (:oauth_token db-user)))
        (is (= (:oauth_token_secret access-token)
               (:oauth_token_secret db-user))))
      ;; no mocking out
      ;; add-access-token should return db creds
      (let [ret (add-access-token handle (atom {}))]
        (is ret)
        (is (= access-token (:access-token (handle-key ret))))
        (is (= user-id (:user-id (handle-key ret))))))))

(deftest t-goodreads-access-for-user
  ;; arbitrary handles should be false
  (let [user-handle "prefect"
        user-access-token {:oauth_token "pinfeathers"
                           :oauth_token_secret "gollyfluff"}
        goodreads-creds (atom {})]
    (is (not (goodreads-access-for-user? goodreads-creds user-handle)))
    ;; insert user into db
    (user/create-user :goodreads-id "42"
                      :handle user-handle
                      :oauth-token (:oauth_token user-access-token)
                      :oauth-token-secret (:oauth_token_secret
                                           user-access-token))
    ;; should now return true
    (is (goodreads-access-for-user? goodreads-creds user-handle))
    ;; creds should include handle and info
    (let [creds (:prefect @goodreads-creds)]
      (is (= "42"
             (:user-id creds)))
      (is (= user-access-token
             (:access-token creds))))
    ;; another arbitrary handle should be false
    (let [user-handle "ford"
          user-access-token {:oauth_token "pinfeathers"
                             :oauth_token_secret "gollyfluff"}]
      (is (not (goodreads-access-for-user? goodreads-creds user-handle)))
      ;; mock out access token and user id
      (with-redefs [get-access-token (fn [& args] user-access-token)
                    get-user-id (fn [& args] "7")]
        ;; call again; should be true now
        (is (goodreads-access-for-user? goodreads-creds user-handle)))
      ;; should have updated db
      (let [user (user/find-by-goodreads-id "7")]
        (is (= "ford"
               (:handle user)))
        (is (= "pinfeathers"
               (:oauth_token user)))
        (is (= "gollyfluff"
               (:oauth_token_secret user))))
      ;; should have updated creds atom
      (let [creds (:ford @goodreads-creds)]
        (is (= "7"
               (:user-id creds)))
        (is (= user-access-token
               (:access-token creds)))))
    ;; if user exists but has no auth, should return false
    (let [handle "archimedes"]
      (user/create-user :handle handle :last-tweet 24)
      (is (not (goodreads-access-for-user? goodreads-creds handle))))))

(deftest t-add-book
  (let [found-books [{:id "12"
                      :title "The Bone Clocks"
                      :author "David Mitchell"}
                     {:id "13"
                      :title "The Bone Clockmaker"
                      :author "Dorothy Drew"}]
        added (atom [])
        published (atom [])]
    (with-redefs [find-book-by-title-and-author (fn [& args]
                                                  found-books)
                  add-book-to-shelf (fn [& args]
                                      (swap! added conj (nth args 2)))
                  lb/publish (fn [& args] (swap! published conj (last args)))]
      (add-book nil "mindbat" nil nil
                "The Bone Clocks" "Mitchell" "to-read"))
    (is (= 1 (count @added)))
    (is (= "12" (first @added)))
    (is (= 1 (count @published)))
    (is (= {:user-handle "mindbat"
            :sent-cmd "add-book"
            :sent-args ["Mitchell" "The Bone Clocks" "to-read"]
            :results @added}
           (json/parse-string (first @published) true)))))

(deftest t-add-book-not-found
  (let [found-books []
        added (atom [])
        published (atom [])]
    (with-redefs [find-book-by-title-and-author (fn [& args]
                                                  found-books)
                  add-book-to-shelf (fn [& args]
                                      (swap! added conj (nth args 2)))
                  lb/publish (fn [& args] (swap! published conj (last args)))]
      (add-book nil "mindbat" nil nil
                "The Bone Clocks" "Davis" "to-read"))
    (is (= 0 (count @added)))
    (is (= 1 (count @published)))
    (is (= {:user-handle "mindbat"
            :sent-cmd "add-book"
            :sent-args ["Davis" "The Bone Clocks" "to-read"]
            :results nil}
           (json/parse-string (first @published) true)))))
