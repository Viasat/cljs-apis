#!/usr/bin/env nbb

(ns reach
  (:require [cljs.pprint :refer [pprint]]
            [clojure.string :as S]
            [promesa.core :as P]
            [viasat.util :refer [parse-opts]]
            [viasat.apis.aws.core :as aws]
            #?(:org.babashka/nbb ["easy-table$default" :as Table]
               :cljs             ["easy-table" :as Table])))

(defn epprint [arg] (P/let [result arg] (pprint result) result))

(def usage "
Usage:
  reach [options] <source> <dest> <proto>

Options:
  --profile PROFILE   AWS profile [env: PROFILE] [default: saml]
  --region REGION     AWS region [env: REGION] [default: us-west-2]
  --source-ip IP      Address of the resource that is the start of the path
  --dest-ip IP        Address of the resource that is the end of the path
  --dest-port PORT    Destination port
")

(defn create-network-insights-path
  "Create a Network Insights Path resource and return the ID."
  [opts]
  (P/let [common-opts (select-keys opts [:profile :region])
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
  (P/let [common-opts (select-keys opts [:profile :region])
          opts {:NetworkInsightsPathId id}
          result (aws/invoke :EC2 :StartNetworkInsightsAnalysis (merge common-opts opts))]
    (get-in result [:NetworkInsightsAnalysis :NetworkInsightsAnalysisId])))

(defn describe-network-insights-analysis-until
  "Describe a reachability analysis for a Network Insights Path resource by ID."
  [done-fn opts id]
  (P/let [common-opts (select-keys opts [:profile :region])
          opts {:NetworkInsightAnalysisIds [id]}
          result (aws/invoke-until done-fn :EC2 :DescribeNetworkInsightsAnalyses (merge common-opts opts))]
    (-> result :NetworkInsightsAnalyses first)))

(defn delete-network-insights-analysis
  "Delete a Network Insights Analysis resource by ID."
  [opts id]
  (P/let [common-opts (select-keys opts [:profile :region])
          opts {:NetworkInsightsAnalysisId id}
          result (aws/invoke :EC2 :DeleteNetworkInsightsAnalysis (merge common-opts opts))]
    result))

(defn delete-network-insights-path
  "Delete a Network Insights Path resource by ID."
  [opts id]
  (P/let [common-opts (select-keys opts [:profile :region])
          opts {:NetworkInsightsPathId id}
          result (aws/invoke :EC2 :DeleteNetworkInsightsPath (merge common-opts opts))]
    (get result :NetworkInsightsPathId)))

(P/let [opts (parse-opts usage *command-line-args*)
        common-opts (select-keys opts [:profile :region])
        nip-id (create-network-insights-path opts)
        nia-id (start-network-insights-analysis opts nip-id)
        result (describe-network-insights-analysis-until
                (fn [res] (not= "running" (-> res :NetworkInsightsAnalyses first :Status))) opts nia-id)
        _ (delete-network-insights-analysis opts nia-id)
        _ (delete-network-insights-path opts nip-id)]
  (println "Status:" (:Status result) "\n")
  (doseq [direction [:ForwardPathComponents :ReturnPathComponents]]
    (println (str (name direction) ":") "\n")
    (let [table (Table.)]
      (doseq [row (get result direction)]
        (doseq [col-path [[:SequenceNumber] [:Component :Id] [:Component :Name]]]
          (.cell table (S/join "/" (map name col-path)) (get-in row col-path)))
        (.newRow table))
      (println (.toString table)))))
