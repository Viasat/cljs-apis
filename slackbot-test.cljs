#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns slackbot-test
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["dotenv$default" :as dotenv]
            [viasat.util :refer [parse-opts Eprintln Eprn Epprint]]
            [viasat.apis.slack :as slack]))

;; Links:
;; - Main management link:
;;   https://api.slack.com/apps/APP_ID/general
;; - Commands must be registered:
;;   https://api.slack.com/apps/APP_ID/slash-commands
;; - Events app_mention and message.* must be registered:
;;   https://api.slack.com/apps/APP_ID/event-subscriptions

(def usage "
Usage:
  slackbot-test [options] <channel>

Positional Parameters:
  channel    Slack channel ID (e.g. 'C016TBXVA9C')

Options:
  --debug               Debug/trace output [env: DEBUG]
")

(set! *warn-on-infer* false)

(defn hello-msg [user]
  (let [text (str "Hello<@" user ">! Here is a button for you")]
    {:text text
     :blocks [{
               :type "section"
               :text {:type "mrkdwn"
                      :text text}
               :accessory {:type "button"
                           :text {:type "plain_text"
                                  :text "Click Me"}
                           :action_id "button_click"}}]}))


(dotenv/config #js {:path ".secrets"})

(P/let
  [opts (parse-opts usage *command-line-args*)
   {:keys [debug channel]} opts
   _ (when debug
       (Eprintln "Settings:") (Epprint opts))
   _ (when (not js/process.env.SLACK_BOT_TOKEN)
       (Eprintln "Error: SLACK_BOT_TOKEN not set")
       (js/process.exit 2))
   _ (.on js/process "SIGINT" #(js/process.exit 130))
   ctx (slack/websocket-ctx (merge opts {:channel channel}))
   _ (when debug (Eprintln "Slack context:") (Epprint ctx))
   app (doto
         (:app ctx)
         (.event "app_mention"
                 (fn [evt]
                   (let [{:keys [body client logger]} (->clj evt)
                         event (:event body)]
                     (Eprn :event-app_mention event))))
         (.command "/hello"
                   (fn [evt]
                     (let [{:keys [command ack say]} (->clj evt)]
                       (Eprn :got-command-hello command)
                       (ack)
                       (when debug (Eprn "Acknowledged hello"))
                       (say "I'm responding to your hello"))))
         (.message "xyzzy"
                   (fn [evt]
                     (let [{:keys [message]} (->clj evt)]
                       (Eprn :got-message-xyzzy message)
                       (slack/post-message ctx (hello-msg (:user message))))))
         (.action "button_click"
                  (fn [evt]
                    (let [{:keys [body ack say]} (->clj evt)
                          ts (-> body :container :message_ts)]
                      (Eprn :got-action-button-click body)
                      (ack)
                      (-> (:app ctx) .-client .-reactions
                          (.add #js {:channel (:channel ctx)
                                     :name "tada"
                                     :timestamp ts})))))
         (.start))
   _ (Eprn :started)
   now (.toISOString (js/Date.))
   res (slack/post-message ctx {:text (str "Slackbot started at " now)})]
  (when debug (Eprintln "Start greeting result:") (Epprint res))
  
  (js/setInterval #(when debug (Eprn :heartbeat)) 10000)
  nil)
