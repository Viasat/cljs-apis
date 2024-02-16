#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns cfn-desc-all
  (:require [clojure.string :as S]
            [promesa.core :as P]
            ["fs-extra$default" :as fs]
            [viasat.util :refer [parse-opts Eprintln Epprint Eprn]]
            [viasat.apis.aws.cfn :as cfn]))

(def usage "
Usage:
  cfn-desc-all [options] <json-stack-file>

Do CloudFormation DescribeStacks all stacks (running and deleted) and
store results as JSON in <json-stack-file>.

Options:
  --debug                Debug/trace output (stderr) [env: DEBUG]
  --profile PROFILE      AWS profile [env: PROFILE] [default: saml]
  --no-profile           Do not use a profile value
                         [env: NO_PROFILE]
  --role-arn             ARN of role to use assume for execution
  --region REGION        AWS region [env: REGION] [default: us-west-2]
  --parallel JOBS        Number of AWS API calls to run parallel
                         [default: 2]

  --since DATE           Only show stacks created since DATE
                         (up to max of 90 days ago).
")

(P/let
  [cfg (parse-opts usage *command-line-args*)
   _ (when (empty? cfg) (js/process.exit 2))
   {:keys [json-stack-file debug parallel since]} cfg
   aws-opts (select-keys cfg [:debug :profile :no-profile :region :role-arn])

   _ (when debug (Eprintln "Settings:"))
   _ (when debug (Epprint cfg))

   filter-fn (when since #(> (:CreationTime %) (js/Date. since)))
   stacks (cfn/describe-all-stacks (merge aws-opts
                                          {:parallel parallel
                                           :filter-fn filter-fn
                                           :log-fn Eprintln}))]
  (Eprintln "Writing JSON results to:" json-stack-file)
  (fs/writeFile json-stack-file (js/JSON.stringify (clj->js stacks))))
