(ns myshelf.test.twitter
  (:require [cheshire.core :as json]
            [clojure.test :refer :all]
            [langohr.basic :as lb]
            [myshelf.db :as db]
            [myshelf.twitter :refer :all]
            [twitter.api.restful :as twitter]))

(use-fixtures :once
  db/migrate-db-fixture)

(use-fixtures :each
  db/clean-db-fixture)

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
                             :sent-args add-sent-args})))))

(def sample-tweets
  {:body [{:id 7
           :text "myshelf-bot: find The Bone Clocks"
           :user {:screen_name "mindbat"}}
          {:id 6
           :text "yolo"
           :user {:screen_name "mindbat"}}
          {:id 5
           :text "myshelf-bot: add The Bone Clocks by Mitchell"
           :user {:screen_name "mindbat"}}
          {:id 4
           :text "myshelf-bot: rank to-read"
           :user {:screen_name "mindbat"}}
          {:id 3
           :text "nothing here but myshelf-bot"
           :user {:screen_name "mindbat"}}
          {:id 2
           :text "@mindbat: myshelf-bot add 1223134"
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
      (is (= 2
             (count @published)))
      (is (= #{{:user-handle "mindbat"
                :cmd "add"
                :args ["The Bone Clocks" "Mitchell"]}
               {:user-handle "mindbat"
                :cmd "rank"
                :args ["to-read"]}}
             (set (map #(json/parse-string % true)
                       @published)))))))

(deftest t-listen-for-tweets
  (let [published (atom [])]
    (with-redefs [twitter/statuses-user-timeline (fn [& args]
                                                   sample-tweets)
                  lb/publish (fn [_ _ _ cmd]
                               (swap! published conj cmd))]
      (let [listen-fut (future (listen-for-tweets nil nil "mindbat" 1000))]
        (Thread/sleep 100)
        (is (= 2
               (count @published)))
        (is (= #{{:user-handle "mindbat"
                  :cmd "add"
                  :args ["The Bone Clocks" "Mitchell"]}
                 {:user-handle "mindbat"
                  :cmd "rank"
                  :args ["to-read"]}}
               (set (map #(json/parse-string % true)
                         @published))))
        (future-cancel listen-fut)
        (is (= 7 (:last_tweet (db/find-last-tweet "mindbat")))))
      (reset! published [])
      (let [listen-fut (future (listen-for-tweets nil nil "mindbat" 1000))]
        (is (= 0
               (count @published)))
        (future-cancel listen-fut)
        (is (= 7 (:last_tweet (db/find-last-tweet "mindbat"))))))))
