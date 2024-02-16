;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.slack
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [Eprintln Epprint]]
            ["@slack/bolt$default" :refer [App]]))

;;; General utility functions

;; async version of ->clj
(defn P->clj [p] (P/let [res p] (->clj res)))

(defn map-subset?
  "Is m2 a recursive (sparse?) subset of m1.
  Note: Non-tail recursive so large structures can stack overflow."
  [m1 m2]
  (every? (fn [[k v2]]
            (let [v1 (get m1 k)]
              (if (and (map? v1) (map? v2))
                (map-subset? v1 v2)
                (= v1 v2)))) m2))

;;; Slack functions

(defn auth-info [{:keys [app]}]
  (-> app .-client .-auth (.test #js {}) P->clj))

(defn websocket-ctx [opts]
  (P/let [app (App. #js {:token js/process.env.SLACK_BOT_TOKEN
			 :socketMode true
			 :appToken js/process.env.SLACK_APP_TOKEN})
          info (auth-info {:app app})]
    (merge opts {:app app
                 :info info
                 :id (:user_id info)})))

(defn messages
  "[Async] Get all slack messages optionally filtered by filt."
  [{:keys [app channel]} & [filt]]
  (P/let [msgs (P->clj (-> app .-client .-conversations
                           (.history #js {:channel channel})))]
    (filter #(map-subset? % filt) (:messages msgs))))

(defn find-message
  "[Async] Find most recent message with text matching regex. If
  max-age-seconds is specified then only match a message younger than
  max-age-seconds."
  [{:keys [debug app id channel] :as ctx} regex & [max-age-seconds]]
  (P/let [msgs (messages ctx {:user id})
          now (/ (js/Date.) 1000)
          _ (when debug
              (Eprintln "Found" (count msgs) "message matching:" regex))
          match? #(and (re-find regex (:text %))
                       (or (= nil max-age-seconds)
                           (< (- now (:ts %)) max-age-seconds)))
          msg (first (filter match? msgs))]
    msg))

(defn post-message
  "[Async] Post/update a slack message. If msg contains :ts then the
  matching message will be updated otherwise a new message will be
  created. If :thread_ts is set then the message will be a threaded
  reply."
  [{:keys [app channel]} msg]
  (P/let [msg (assoc msg :channel channel)
          upsert (if (:ts msg)
                   #(-> app .-client .-chat (.update (clj->js msg)))
                   #(-> app .-client .-chat (.postMessage (clj->js msg))))
          res (P->clj (upsert (clj->js msg)))]
    res))

;; async
(defn update-thread
  "[Async] Find a Slack message matching msg-regex and change/edit
  it to top-msg. Then append a thread reply reply-msg to the same
  message. If max-age-seconds is specified then only match a message
  younger than max-age-seconds. If there is no match or the message
  exceeds the max age, then create a new top-level messsage top-msg
  before appending reply-msg."
  [ctx msg-regex top-msg reply-msg & [max-age-seconds]]
  (P/let [{:keys [debug app id channel]} ctx
          msg-re (js/RegExp msg-regex)
          found-msg (find-message ctx msg-re max-age-seconds)
          _ (when debug (Eprintln "Matching message:") (Epprint found-msg))
          new-msg (assoc top-msg :ts (:ts found-msg))
          _ (when debug
              (Eprintln (str (if found-msg "Updating" "Creating")
                             " '" msg-regex "' thread"))
              (Eprintln "Sending message:") (Epprint new-msg))
          top-res (post-message ctx new-msg)
          msg (:message top-res)
          _ (when debug
              (Eprintln (str "Adding to '" msg-regex "' thread")))
          reply-msg (assoc reply-msg :thread_ts (str (:ts top-res)))
          reply-res (post-message ctx reply-msg)]
    reply-res))

