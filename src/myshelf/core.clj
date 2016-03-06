(ns myshelf.core
  (:require [clojure.string :as str]
            [myshelf.auth :refer [make-auth-request-GET
                                  make-auth-request-POST
                                  make-auth-request-PUT]]
            [myshelf.common :refer [build-url
                                    element->map]]))

(defn get-user-id
  "Fetch the Goodreads user id for the user that has granted access"
  [consumer access-token]
  (let [user-id-url (build-url "api" "auth_user")
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

(defn find-book-by-title
  "Runs a query for a book by title. Returns only the last
  20 results."
  [consumer access-token title]
  (let [search-url (build-url "search" "index")
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
  (let [search-url (build-url "search" "index")
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

(defn add-book-review-by-title
  [consumer access-token user-id title rating & [review-text]]
  (if-let [shelved-id (:id (first (find-book-on-shelves consumer
                                                        access-token
                                                        user-id
                                                        title)))]
    (add-book-review consumer access-token shelved-id rating
                     review-text)
    "Could not find book on shelves"))

(defn find-book-and-score
  [consumer access-token user-id title]
  (let [book (first (find-book-on-shelves consumer
                                          access-token
                                          user-id
                                          title))
        friend-ratings (get-friend-ratings-for-book consumer
                                                    access-token
                                                    user-id
                                                    (:id book))]
    (compute-book-score book friend-ratings)))

(defn score-books-on-shelf
  [consumer access-token user-id shelf]
  (let [books (get-all-books-on-shelf consumer access-token
                                      user-id shelf)
        books (map #(select-keys % score-keys) books)
        friends (get-user-friends consumer access-token user-id)]
    (score-books consumer access-token books friends)))

(defn rank-books-on-shelf
  [consumer access-token user-id shelf]
  (let [scored-books (score-books-on-shelf consumer access-token
                                           user-id shelf)]
    (->> scored-books
         (sort-by :score >)
         (map (juxt :id :title (comp :name :author :authors))))))
