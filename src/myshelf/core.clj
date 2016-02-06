(ns myshelf.core
  (:require [clj-http.client :as http]
            [clojure.java.io :refer [input-stream]]
            [clojure.xml :as xml]
            [oauth.client :as oauth]))

(def goodreads-request-token-url
  "http://www.goodreads.com/oauth/request_token")
(def goodreads-authorize-url
  "http://www.goodreads.com/oauth/authorize")
(def goodreads-access-token-url
  "http://www.goodreads.com/oauth/access_token")
(def goodreads-base-url "http://goodreads.com/")
(def goodreads-crypto-default :hmac-sha1)

(defn get-consumer
  "Get an API consumer for Goodreads"
  [key secret]
  (oauth/make-consumer key
                       secret
                       goodreads-request-token-url
                       goodreads-access-token-url
                       goodreads-authorize-url
                       :hmac-sha1))

(defn get-request-token
  "Get a Goodreads request token for a particular application"
  [consumer]
  (oauth/request-token consumer))

(defn find-approval-uri
  "Fetch the uri to be opened in a browser for the user to grant approval
  to this application"
  [consumer request-token]
  (oauth/user-approval-uri consumer
                           (:oauth_token request-token)))

(defn get-access-token
  "Once access has been granted, fetch an access token for using the API"
  [consumer request-token]
  (oauth/access-token consumer request-token))

(defn make-auth-request-GET
  [consumer access-token url params]
    (let [{:keys [oauth_token oauth_token_secret]} access-token
        credentials (oauth/credentials consumer
                                       oauth_token
                                       oauth_token_secret
                                       :GET
                                       url
                                       params)]
    (xml/parse (input-stream
                (.getBytes
                 (:body (http/get url
                                  {:query-params credentials})))))))

(defn get-user-id
  "Fetch the Goodreads user id for the user that has granted access"
  [consumer access-token]
  (let [user-id-url "https://www.goodreads.com/api/auth_user"
        resp (make-auth-request-GET consumer
                                    access-token
                                    user-id-url
                                    {})]
    (->> resp
        :content
        (filter #(= :user (:tag %)))
        first
        :attrs
        :id)))
