(ns myshelf.friends
  (:require [myshelf.auth :refer [make-auth-request-GET]]
            [myshelf.common :refer [build-url
                                    element->map
                                    try-int]]
            [myshelf.reviews :refer [get-book-rating]]))

(defn get-user-friends
  [consumer access-token user-id]
  (let [friends-url (build-url "friend" "user.xml")
        params {:id user-id}
        resp (make-auth-request-GET consumer
                                    access-token
                                    friends-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (map element->map)
         (map :user))))

(defn get-friend-ratings-for-book
  [consumer access-token book-id & {:keys [friends user-id]}]
  (let [friends (or friends
                    (get-user-friends consumer access-token
                                      user-id))]
    (->> friends
         (pmap #(get-book-rating consumer access-token
                                 (:id %) book-id))
         (filter identity)
         (map try-int)
         (filter #(> % 0)))))
