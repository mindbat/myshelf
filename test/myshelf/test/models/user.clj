(ns myshelf.test.models.user
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.test :refer :all]
            [myshelf.db :refer :all]
            [myshelf.models.user :refer :all]))

(use-fixtures :once
  migrate-db-fixture)

(use-fixtures :each
  clean-db-fixture)

(deftest t-create
  ;; create new user with just bare bones
  (let [handle "archimedes"
        tweet-id 1234]
    (create-user :handle handle :last-tweet tweet-id)
    ;; should be able to get data back
    (let [new-user (find-by-handle handle)]
      (is (= handle (:handle new-user)))
      (is (= tweet-id (:last_tweet new-user)))
      (is (:user_id new-user))
      (is (:created_at new-user))
      (is (:updated_at new-user))))
  ;; create complete user
  (let [handle "merlin"
        tweet-id 3456
        goodreads-id "123456"
        oauth-token "token"
        oauth-token-secret "secret"]
    (create-user :goodreads-id goodreads-id
                 :handle handle
                 :last-tweet tweet-id
                 :oauth-token oauth-token
                 :oauth-token-secret oauth-token-secret)
    ;; should get info back
    (let [new-user (find-by-handle handle)]
      (is (= handle (:handle new-user)))
      (is (= tweet-id (:last_tweet new-user)))
      (is (= goodreads-id (:goodreads_id new-user)))
      (is (= oauth-token (:oauth_token new-user)))
      (is (= oauth-token-secret (:oauth_token_secret new-user)))
      (is (:user_id new-user))
      (is (:created_at new-user))
      (is (:updated_at new-user)))
    ;; should not be able to insert duplicate goodreads-id
    (is (thrown-with-msg? Exception
                          #"User with that goodreads-id already exists"
                          (create-user :goodreads-id goodreads-id)))
    ;; should not be able to insert duplicate handle
    (is (thrown-with-msg? Exception
                          #"User with that handle already exists"
                          (create-user :handle handle))))
  ;; should require either goodreads-id or handle
  (is (thrown-with-msg? Exception #"Must pass goodreads-id or handle"
                        (create-user :last-tweet 101)))
  ;; nonsense should be thrown away
  (create-user :handle "dr-donna" :last-tweet 256 :pinfeathers "gollyfluff")
  (let [new-user (find-by-handle "dr-donna")]
    (is (nil? (:pinfeathers new-user)))
    (is (= 256 (:last_tweet new-user)))))

(deftest t-update
  ;; create new user
  (let [handle "obrien"
        last-tweet 256]
    (create-user :handle handle :last-tweet last-tweet)
    ;; add access token info
    (let [new-user (find-by-handle handle)]
      (update-user (merge new-user {:oauth_token "token"
                                    :oauth_token_secret "secret"}))
      (let [updated (find-by-handle handle)
            updated-at (:updated_at updated)]
        ;; should be able to pull them back
        (is (= "token" (:oauth_token updated)))
        (is (= "secret" (:oauth_token_secret updated)))
        ;; updated-at should have been incremented
        (is (t/after? (tc/from-sql-time updated-at)
                      (tc/from-sql-time (:created_at updated))))
        ;; add goodreads-id
        (update-user (merge updated {:goodreads_id "42"}))
        ;; should be able to pull back
        (let [updated (find-by-handle handle)]
          ;; should be able to pull them back
          (is (= "token" (:oauth_token updated)))
          (is (= "secret" (:oauth_token_secret updated)))
          (is (= "42" (:goodreads_id updated)))
          ;; updated-at should have been incremented
          (is (t/after? (tc/from-sql-time (:updated_at updated))
                        (tc/from-sql-time updated-at))))))
    ;; should not be able to update without user-id
    (is (thrown-with-msg? Exception
                          #"Must pass user-id"
                          (update-user {:handle handle
                                        :last-tweet 512})))
    ;; should not be able to update non-existent user
    (is (thrown-with-msg? Exception
                          #"User does not exist"
                          (update-user {:user_id 42
                                        :handle "morrissey"})))
    ;; should not be able to update to existing handle
    (create-user :handle "archimedes" :last-tweet 512)
    (let [copy-user (find-by-handle "archimedes")]
      (is (thrown-with-msg? Exception
                            #"User with that handle already exists"
                            (update-user (merge copy-user
                                                {:handle handle}))))
      ;; should not be able to update to existing goodreads-id
      (is (thrown-with-msg? Exception
                            #"User with that goodreads-id already exists"
                            (update-user (merge copy-user
                                                {:goodreads_id "42"}))))))
  ;; junk attributes get thrown out
  (create-user :handle "merlin" :last-tweet 42)
  (let [merlin (find-by-handle "merlin")]
    (update-user (merge merlin {:last_tweet 56 :bermuda "vacation"}))
    (let [updated (find-by-handle "merlin")]
      (is (= 56 (:last_tweet updated)))
      (is (nil? (:bermuda updated))))))

(deftest t-update-last-tweet
  ;; should create new user on first call
  (let [handle "archimedes"
        last-tweet 101]
    (update-last-tweet handle last-tweet)
    (let [new-user (find-by-handle handle)]
      (is (= handle (:handle new-user)))
      ;; user should have tweet id set
      (is (= last-tweet (:last_tweet new-user)))
      (is (:created_at new-user))
      (is (:updated_at new-user))
      (is (= (:created_at new-user)
             (:updated_at new-user))))
    ;; should update the user on next call
    (let [new-tweet 256]
      (update-last-tweet handle new-tweet)
      (let [updated-user (find-by-handle handle)]
        ;; last-tweet should match
        (is (= new-tweet (:last_tweet updated-user)))
        (is (t/after? (tc/from-sql-time (:updated_at updated-user))
                      (tc/from-sql-time (:created_at updated-user))))))))

(deftest t-update-oauth
  ;; should create new user on first call
  (let [handle "archimedes"
        goodreads-id "42"
        oauth-token "token"
        oauth-token-secret "secret"]
    (update-oauth handle goodreads-id oauth-token oauth-token-secret)
    (let [new-user (find-by-handle handle)]
      (is (= handle (:handle new-user)))
      ;; user should have oauth set
      (is (= oauth-token (:oauth_token new-user)))
      (is (= oauth-token-secret (:oauth_token_secret new-user)))
      (is (:created_at new-user))
      (is (:updated_at new-user))
      (is (= (:created_at new-user)
             (:updated_at new-user))))
    ;; should update the user on next call
    (let [new-token "newtoken"
          new-token-secret "newsecret"]
      (update-oauth handle goodreads-id new-token new-token-secret)
      (let [updated-user (find-by-handle handle)]
        ;; oauth should match
        (is (= new-token (:oauth_token updated-user)))
        (is (= new-token-secret (:oauth_token_secret updated-user)))
        (is (t/after? (tc/from-sql-time (:updated_at updated-user))
                      (tc/from-sql-time (:created_at updated-user))))))))
