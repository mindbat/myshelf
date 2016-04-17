(ns myshelf.test.twitter
  (:require [cheshire.core :as json]
            [clojure.test :refer :all]
            [langohr.basic :as lb]
            [myshelf.twitter :refer :all]
            [twitter.api.restful :as twitter]))

(deftest t-generate-status
  ;; if have msg or url, just use that
  (is (= "pinfeathers:gollyfluff"
         (generate-status {:msg "pinfeathers"
                           :url "gollyfluff"
                           :results ["ignore" "me"]})))
  ;; rank books should print just first three titles
  (let [ranked [{:id 1 :title "A Wrinkle in Time"}
                {:id 2 :title "A Wizard of Earthsea"}
                {:id 3 :title "Dune"}
                {:id 4 :title "The Goblin Emperor"}
                {:id 5 :title "Redshirts"}]
        status (generate-status {:sent-cmd "rank-books"
                                 :sent-args ["ignored"]
                                 :results ranked})]
    (is (.contains status (:title (first ranked))))
    (is (.contains status (:title (second ranked))))
    (is (.contains status (:title (nth ranked 2))))
    (is (.contains status (:title (nth ranked 3))))
    ;; shouldn't have the last one
    (is (not (.contains status (:title (last ranked)))))
    ;; should not have anyone's ids
    (is (not (.contains status (str (:id (first ranked))))))
    (is (not (.contains status (str (:id (second ranked))))))
    (is (not (.contains status (str (:id (nth ranked 2))))))
    (is (not (.contains status (str (:id (nth ranked 3)))))))
  (let [add-results {:random "text"}
        add-sent-args ["random-number" "to-read"]]
    ;; add book should report success if any results
    (is (= "Added random-number to to-read"
           (generate-status {:sent-cmd "add-book"
                             :results add-results
                             :sent-args add-sent-args})))
    ;; add book should report failure if no results
    (is (= "Could not add random-number to to-read"
           (generate-status {:sent-cmd "add-book"
                             :results nil
                             :sent-args add-sent-args}))))
  (let [find-results [{:id 1 :title "Full Fathom Five"
                       :author "Max Gladwell"}
                      {:id 2 :title "The Bone Clocks"
                       :author "David Mitchell"}
                      {:id 3 :title "Footsteps in the Sky"
                       :author "Greg Keyes"}
                      {:id 4 :title "The Martian"
                       :author "Andy Weir"}]
        status (generate-status {:sent-cmd "find-book"
                                 :results find-results})]
    ;; find book should report id and title and author
    (is (.contains status "1|Full Fathom Five|Max Gladwell"))
    (is (.contains status "2|The Bone Clocks|David Mitchell"))
    (is (.contains status "3|Footsteps in the Sky|Greg Keyes"))
    ;; find book should only report 3 results
    (is (not (.contains status "4|The Martian|Andy Weir")))))

(deftest t-generate-status-char-limit
  (let [find-results [{:id 1
                       :title (str "The End of Alchemy"
                                   ": Money, Banking, and the "
                                   "Future of the Global Economy")
                       :author "Mervyn King"}
                      {:id 2
                       :title (str "A Monument to the End of Time: "
                                   "Alchemy, Fulcanelli, & the Great Cross")
                       :author "Jay Weidner"}
                      {:id 3 :title "Footsteps in the Sky"
                       :author "Greg Keyes"}]
        status (generate-status {:sent-cmd "find-book"
                                 :results find-results})]
    ;; status should be less than 140 characters long
    (is (> 140 (count status)))
    ;; should have shortened both titles
    (is (.contains status "1|The End of Alchemy: Money...|Mervyn King"))
    (is (.contains status "2|A Monument to the End of ...|Jay Weidner"))
    ;; should have left the third one untrimmed
    (is (.contains status "3|Footsteps in the Sky|Greg Keyes"))))

(def sample-tweets
  {:body [{:id 7
           :text "myshelf-bot: find The Bone Clocks"
           :user {:screen_name "mindbat"}}
          {:id 6
           :text "yolo"
           :user {:screen_name "mindbat"}}
          {:id 5
           :text "myshelf-bot: add 12121212"
           :user {:screen_name "mindbat"}}
          {:id 4
           :text "myshelf-bot: rank to-read"
           :user {:screen_name "mindbat"}}
          {:id 3
           :text "nothing here but myshelf-bot"
           :user {:screen_name "mindbat"}}
          {:id 2
           :text "@mindbat: myshelf-bot add 13456232"
           :user {:screen_name "gozarian"}}
          {:id 1
           :text "myshelf-bot: what are you doing"
           :user {:screen_name "mindbat"}}]})

(deftest t-check-tweets
  (let [published (atom [])]
    (with-redefs [twitter/statuses-user-timeline (fn [& args]
                                                   sample-tweets)
                  lb/publish (fn [_ _ _ cmd]
                               (swap! published conj cmd))]
      (is (= 7
             (check-tweets nil nil "mindbat" nil)))
      (is (= 3
             (count @published)))
      (is (= #{{:user-handle "mindbat"
                :cmd "find"
                :args ["The Bone Clocks"]}
               {:user-handle "mindbat"
                :cmd "add"
                :args ["12121212"]}
               {:user-handle "mindbat"
                :cmd "rank"
                :args ["to-read"]}}
             (set (map #(json/parse-string % true)
                       @published)))))))
