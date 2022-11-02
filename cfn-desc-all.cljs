(ns cfn-desc-all
  (:require [clojure.string :as S]
            [promesa.core :as P]
            ["fs-extra$default" :as fs]
            [viasat.util :refer [parse-opts Eprintln Epprint Eprn]]
            [viasat.apis.aws.core :as aws]))

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
   aws-opts (select-keys cfg [:debug :profile :no-profile :region])

   _ (when debug (Eprintln "Settings:"))
   _ (when debug (Epprint cfg))
   
   stack-list (P/->> (aws/invoke :CloudFormation :ListStacks aws-opts)
                     :StackSummaries)
   stack-list (if since
                (filter #(> (:CreationTime %) (js/Date. since)) stack-list)
                stack-list)
   _ (Eprintln (str "Querying " (count stack-list) " stacks ("
                    parallel " at a time)"))
   stacks (P/loop [left stack-list
                   res []] 
            (if (empty? left)
              res
              (P/let [schunk (take parallel left)
                      r (P/all
                          (for [stack schunk]
                            (aws/invoke
                              :CloudFormation :DescribeStacks
                              (merge aws-opts
                                     {:StackName (:StackId stack)}))))
                      res (apply conj res (mapcat :Stacks r))]

                _ (Eprintln (str "Queried " (count res) "/" (count stack-list)))
                (P/recur (drop parallel left) res))))]
  (Eprintln "Writing JSON results to:" json-stack-file)
  (fs/writeFile json-stack-file (js/JSON.stringify (clj->js stacks))))

