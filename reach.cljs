#!/usr/bin/env nbb

(ns reach
  (:require [cljs.pprint :refer [pprint]]
            [clojure.string :as S]
            [promesa.core :as P]
            [viasat.util :refer [parse-opts Eprintln]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.aws.core :as aws]))

(defn epprint [arg] (P/let [result arg] (pprint result) result))

(def usage "
Usage:
  reach [options] <source> <dest> <proto>

Options:
  --debug             Show trace/debug for AWS calls [env: DEBUG]
  --profile PROFILE   AWS profile [env: PROFILE] [default: saml]
  --region REGION     AWS region [env: REGION] [default: us-west-2]
  --source-ip IP      Address of the resource that is the start of the path
  --dest-ip IP        Address of the resource that is the end of the path
  --dest-port PORT    Destination port

  --fields FIELDS        Fields to print (comma or space separated)
  --sort FIELD           Sort using FIELD
  --rsort FIELD          Reverse sort using FIELD
  --field-max FIELD_MAX  Maximum field width (when not --verbose) [implicit default: 40]
  --no-headers           Skip printing headers
  --json                 Print full results as JSON
  --edn                  Print full results as EDN
  --pretty               Pretty print --json or --edn
  --short                Print ID/name only (first table column)
")

(defn create-network-insights-path
  "Create a Network Insights Path resource and return the ID."
  [opts]
  (P/let [common-opts (select-keys opts [:profile :region :debug])
          opts {:Source (:source opts) :Destination (:dest opts) :Protocol (:proto opts)}
          result (aws/invoke :EC2 :CreateNetworkInsightsPath
                             (merge common-opts opts
                                    (if-let [source-ip (:source-ip opts)] {:SourceIp source-ip})
                                    (if-let [dest-ip (:dest-ip opts)] {:DestinationIp dest-ip})
                                    (if-let [dest-port (:dest-port opts)] {:DestinationPort dest-port})))]
    (get-in result [:NetworkInsightsPath :NetworkInsightsPathId])))

(defn start-network-insights-analysis
  "Start a reachability analysis for a Network Insights Path resource by ID."
  [opts id]
  (P/let [common-opts (select-keys opts [:profile :region :debug])
          opts {:NetworkInsightsPathId id}
          result (aws/invoke :EC2 :StartNetworkInsightsAnalysis (merge common-opts opts))]
    (get-in result [:NetworkInsightsAnalysis :NetworkInsightsAnalysisId])))

(defn describe-network-insights-analysis-until
  "Describe a reachability analysis for a Network Insights Path resource by ID."
  [done-fn opts id]
  (P/let [common-opts (select-keys opts [:profile :region :debug])
          opts {:NetworkInsightAnalysisIds [id]}
          result (aws/invoke-until done-fn :EC2 :DescribeNetworkInsightsAnalyses (merge common-opts opts))]
    (-> result :NetworkInsightsAnalyses first)))

(defn delete-network-insights-analysis
  "Delete a Network Insights Analysis resource by ID."
  [opts id]
  (P/let [common-opts (select-keys opts [:profile :region :debug])
          opts {:NetworkInsightsAnalysisId id}
          result (aws/invoke :EC2 :DeleteNetworkInsightsAnalysis (merge common-opts opts))]
    result))

(defn delete-network-insights-path
  "Delete a Network Insights Path resource by ID."
  [opts id]
  (P/let [common-opts (select-keys opts [:profile :region :debug])
          opts {:NetworkInsightsPathId id}
          result (aws/invoke :EC2 :DeleteNetworkInsightsPath (merge common-opts opts))]
    (get result :NetworkInsightsPathId)))

(def SCHEMA
  {:fields [[:Seq [:SequenceNumber]]
            [:Id [:Component :Id]]
            [:Name [:Component :Name]]
            [:Vpc [:Vpc :Name]]
            [:Subnet [:Subnet :Name]]
            [:InHdrSrc [:InboundHeader :SourceAddresses 0]]
            [:InHdrDst [:InboundHeader :DestinationAddresses 0]]
            [:OutHdrSrc [:OutboundHeader :SourceAddresses 0]]
            [:OutHdrDst [:OutboundHeader :DestinationAddresses 0]]
            ]})

(P/let [opts (parse-opts usage *command-line-args*)
        nip-id (create-network-insights-path opts)
        nia-id (start-network-insights-analysis opts nip-id)
        result (describe-network-insights-analysis-until
                (fn [res] (not= "running" (-> res :NetworkInsightsAnalyses first :Status))) opts nia-id)
        _ (delete-network-insights-analysis opts nia-id)
        _ (delete-network-insights-path opts nip-id)]
  (Eprintln "Status:" (:Status result) "\n")
  (doseq [direction [:ForwardPathComponents :ReturnPathComponents]]
    (when (not (:no-headers opts))
      (println (str (name direction) ":")))
    (schema-print (get result direction) SCHEMA opts)))
