(ns myshelf.core
  (:require [myshelf.friends :refer [get-user-friends]]
            [myshelf.rank :refer [rank-books]]
            [myshelf.reviews :refer [add-book-review]]
            [myshelf.shelves :refer [find-book-on-shelves
                                     get-all-books-on-shelf]]))

(defn add-book-review-by-title
  [consumer access-token user-id title rating & [review-text]]
  (if-let [shelved-id (:id (first (find-book-on-shelves consumer
                                                        access-token
                                                        user-id
                                                        title)))]
    (add-book-review consumer access-token shelved-id rating
                     review-text)
    "Could not find book on shelves"))

(defn rank-books-on-shelf
  [consumer access-token user-id shelf]
  (let [books (get-all-books-on-shelf consumer access-token
                                      user-id shelf)
        friends (get-user-friends consumer access-token user-id)]
    (rank-books consumer access-token books friends)))
