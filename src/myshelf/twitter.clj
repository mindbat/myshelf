(ns myshelf.twitter
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.core :as lc]
            [langohr.consumers :as lcs]
            [langohr.queue :as lq]
            [myshelf.worker :refer [default-exchange
                                    connection-params
                                    reply-queue
                                    worker-queue]]
            [twitter.oauth :as auth]
            [twitter.api.restful :as twitter])
  (:gen-class))

(defn get-creds
  []
  (auth/make-oauth-creds (System/getenv "TWITTER_APP_KEY")
                         (System/getenv "TWITTER_APP_SECRET")
                         (System/getenv "TWITTER_USER_TOKEN")
                         (System/getenv "TWITTER_USER_SECRET")))

(defn make-command
  [tweet]
  (let [screen-name (-> tweet
                        :user
                        :screen_name)
        [cmd args] (-> (:text tweet)
                       (str/split #":" 2)
                       second
                       str/trim
                       (str/split #" " 2))]
    {:user-handle screen-name
     :cmd cmd
     :args [(first (str/split args #"\n"))]}))

(defn send-command
  [channel tweet]
  (let [cmd (make-command tweet)]
    (lb/publish channel default-exchange worker-queue
                (json/generate-string cmd))))

(defn valid-command?
  [tweet]
  (->> tweet
       :text
       (re-find #"^myshelf-bot: (add|find|rank) ((\w|-+) ?)+$")
       first
       boolean))

(defn check-tweets
  [channel creds screen-name current-id]
  (let [tweets (twitter/statuses-user-timeline
                :oauth-creds creds
                :params (merge {:screen-name screen-name
                                :count 10}
                               (when current-id
                                 {:since_id current-id})))
        last-seen-id (:id (first (:body tweets)))]
    (->> tweets
         :body
         (sort-by :id)
         (filter valid-command?)
         (map (partial send-command channel))
         doall)
    last-seen-id))

(defn book->string
  [{:keys [id title author]}]
  (let [trimmed-title (if (> (count title) 30)
                        (str (subs title 0 25) "...")
                        title)]
    (format "%s|%s|%s" id trimmed-title author)))

(defn generate-status
  [{:keys [msg url sent-cmd sent-args results]}]
  (cond
    (or msg url) (str msg ":" url)
    (= "rank-books" sent-cmd) (str/join "\n" (map :title
                                                  (take 3 results)))
    (= "add-book" sent-cmd) (if results
                              (format "Added %s to %s" (first sent-args)
                                      (second sent-args))
                              (format "Could not add %s to %s"
                                      (first sent-args)
                                      (second sent-args)))
    (= "find-book" sent-cmd) (->> results
                                  (take 3)
                                  (map book->string)
                                  (str/join "\n"))))

(defn handle-reply
  [creds channel metadata body]
  (let [parsed-body (json/parse-string (String. body "UTF-8") true)
        status (generate-status parsed-body)]
    (try (twitter/statuses-update :oauth-creds creds
                                  :params
                                  {:status (str "@"
                                                (:user-handle parsed-body)
                                                "\n"
                                                status)})
         (catch Exception ex
           (println "error attempting to post status" status)
           (println (.getMessage ex))))))

(defn -main [& args]
  (let [conn (lc/connect connection-params)
        channel (lch/open conn)
        screen-name "mindbat"
        creds (get-creds)]
    (lq/declare channel reply-queue {:auto-delete false})
    (lcs/subscribe channel reply-queue (partial handle-reply creds)
                   {:auto-ack true})
    (loop [ch channel
           current-id nil]
      (let [last-id (check-tweets ch creds screen-name current-id)]
        (Thread/sleep (* 60 1000) #_"one minute")
        (recur ch (or last-id current-id))))))
