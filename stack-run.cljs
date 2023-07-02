#!/usr/bin/env nbb

(ns stack-run
  (:require [clojure.string :as S]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [parse-opts right-pad
                                 Eprintln Epprint fatal]]
            [viasat.apis.aws.core :as aws]
            [viasat.apis.aws.cfn :as cfn]))

(def usage "
Usage:
  stack-run [options] <stack> <cmd-args> <cmd-args>...

Options:
  -v, --verbose        Verbose output (show instance IDs too) [env: VERBOSE]
  --debug              Show debug/trace output (stderr) [env: DEBUG]
  --quiet              Show only result output.
  --no-prefix          Do not print status/host prefix in output
  --profile PROFILE    AWS profile [env: PROFILE] [default: saml]
  --no-profile         Do not use a profile value [env: NO_PROFILE]
  --region REGION      AWS region [env: REGION] [default: us-west-2]
  --work-dir WORK-DIR  Directory where command will execute [default: '/']
  --timeout TIMEOUT    Command timeout in seconds [default: 60]
  --json               Print full results as JSON
  --edn                Print full results as EDN
  --pretty             Pretty print --json or --edn
")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(P/let
  [cfg (parse-opts usage *command-line-args*)
   _ (when (empty? cfg) (js/process.exit 2))
   cfg (merge cfg {:timeout (js/parseInt (:timeout cfg) 10)})
   {:keys [debug stack cmd-args]} cfg

   dbg (if debug Eprintln list)
   _ (when debug (Eprintln "Settings:"))
   _ (when debug (Epprint cfg))

   results (cfn/stack-run stack (S/join " " cmd-args) cfg)]

  (cfn/print-run-results results cfg)
  (when (> (:ErrorCount results) 0) (fatal 1)))
