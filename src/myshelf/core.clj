(ns myshelf.core
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [clojure.java.io :refer [input-stream]]
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
      (xml/parse-str
       (:body
        (http/get url
                  {:query-params (merge credentials
                                        params)})))))

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

(defn element->map
  "Convert a clojure.data.xml Element into a hashmap"
  [element]
  {(:tag element)
   (if (string? (first (:content element)))
     (first (:content element))
     (apply merge (map element->map (:content element))))})

(defn extract-book-data
  "Given a <book> in a <review>, pull out the book's info into
  a more useful hashmap"
  [review-element]
  (->> review-element
       :content
       (filter #(= :book (:tag %)))
       first
       :content
       (reduce (fn [book-map book-tag]
                 (merge book-map (element->map book-tag)))
               {})))

(defn get-books-on-shelf
  "Returns list of hashmaps representing the books on a given
  user's bookshelf"
  [consumer access-token user-id shelf]
  (let [shelf-url (str "https://www.goodreads.com/review/list/"
                       user-id)
        resp (make-auth-request-GET consumer
                                    access-token
                                    shelf-url
                                    {:shelf shelf
                                     :format "xml"
                                     :v 2})]
    (->> resp
         :content
         second
         :content
         (map extract-book-data))))

(defn find-book-by-title
  "Runs a query for a book by title. Returns only the last
  20 results."
  [consumer access-token title]
  (let [search-url "https://www.goodreads.com/search/index"
        params {:q title
                :format "xml"
                :field "title"}
        resp (make-auth-request-GET consumer
                                    access-token
                                    search-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (filter #(= :results (:tag %)))
         first
         :content
         (map element->map)
         (map (comp (juxt :title :id :author) :best_book :work)))))

(defn find-book-by-title-and-author
  "Runs a query for a book by title and author.
  Returns only the last 20 results."
  [consumer access-token title author]
  (let [search-url "https://www.goodreads.com/search/index"
        params {:q (str title " " author)
                :format "xml"}
        resp (make-auth-request-GET consumer
                                    access-token
                                    search-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (filter #(= :results (:tag %)))
         first
         :content
         (map element->map)
         (map (comp (juxt :title :id :author) :best_book :work)))))
