#!/usr/bin/env nbb
(ns slack-thread-notify
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["dotenv$default" :as dotenv]
            [viasat.util :refer [parse-opts Eprintln Epprint]]
            [viasat.apis.slack :as slack]))

(def usage "
Usage:
  slack-thread-notify [options] <channel> <regex> <top-msg> <reply-msg>

Positional Parameters:
  channel    Slack channel ID (e.g. 'C016TBXVA9C')
  regex      Regex to match the top-level of the thread
  top-msg    Top-level thread message to update
  reply-msg  Reply to add to top-level thread message

Options:
  --debug               Debug/trace output [env: DEBUG]
  --max-age-mins MINS   Maximum age (minutes) of existing thread to match [default: 1440]
")

(set! *warn-on-infer* false)


(dotenv/config #js {:path ".secrets"})

(P/let
  [opts (parse-opts usage *command-line-args*)
   {:keys [debug channel regex top-msg reply-msg max-age-mins]} opts
   _ (when debug
       (Eprintln "Settings:") (Epprint opts))
   _ (when (not js/process.env.SLACK_BOT_TOKEN)
       (Eprintln "Error: SLACK_BOT_TOKEN not set")
       (js/process.exit 2))

   top-msg (->clj (js/JSON.parse top-msg))
   reply-msg (->clj (js/JSON.parse reply-msg))
   ctx (slack/websocket-ctx (merge opts {:channel channel}))
   _ (when debug (Eprintln "slack ctx:") (Epprint ctx))
   now (.toISOString (js/Date.))
   res (slack/update-thread ctx regex top-msg reply-msg
                            (* 60 max-age-mins))]
  (when debug (Eprintln "update result:") (Epprint res)))
