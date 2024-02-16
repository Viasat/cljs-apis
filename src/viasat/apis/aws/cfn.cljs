;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.aws.cfn
  (:require [cljs.pprint :refer [pprint]]
            [clojure.string :as S]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [Eprintln right-pad]]
            [viasat.apis.aws.core :as aws]))

(defn get-stack-instances
  "Return EC2 instances (DescribeInstances) owned/tagged by stack.
  :TagMap is added to each instance."
  [stack opts]
  (P/let
    [aws-opts (select-keys opts [:debug :profile :no-profile :region])
     ec2-opts {:Filters [{:Name "instance-state-name"
                          :Values ["running"]}
                         {:Name "tag:aws:cloudformation:stack-name"
                          :Values [stack]}]}
     edata (aws/invoke :EC2 :DescribeInstances (merge aws-opts ec2-opts))]
    (->> (:Reservations edata)
         (mapcat :Instances)
         (map #(assoc % :TagMap (aws/get-tag-map %))))))

(defn stack-run
  "Run cmd on EC2 instances of stack using AWS SSM Session Manager.
  Returns a map with the ListCommands overall status and an :Instances
  key with full instance data (using get-stack-instances) plus
  a :CommandInvocations key for each instances with the detailed
  command invocation result for that instance.

  opts keys:
    :debug           : debug output and AWS call tracing
    :work-dir        : used for working dir for command invocation
    :timeout         : overall timeout value in seconds
    :get-filter-opts : a function that will be called with the
                       instances of the stack and returns filtering
                       parameters (:Targets, :InstanceIds, etc) for
                       the SendCommand invocation.
  "
  [stack cmd opts]
  (P/let
    [{:keys [dbg work-dir timeout get-filter-opts]
      :or {dbg identity
           work-dir "/"
           timeout 60}} opts
     aws-opts (select-keys opts [:debug :profile :no-profile :region])

     _ (dbg "Gathering stack instance data")
     instances (get-stack-instances stack opts)
     _ (when (= 0 (count instances))
         (throw (ex-info (str "No running instances found for" stack) {})))

     filt-opts (if get-filter-opts
                 (get-filter-opts instances)
                 {:Targets [{:Key "tag:aws:cloudformation:stack-name"
                             :Values [stack]}]})
     cmd-opts (merge {:DocumentName "AWS-RunShellScript"
                      :Comment "stack-run command execution"
                      :MaxErrors "100%"
                      :MaxConcurrency "100%"
                      :TimeoutSeconds timeout
                      :Parameters {:commands [cmd]
                                   :workingDirectory [work-dir]}}
                     aws-opts
                     filt-opts)
     cdata (aws/invoke :SSM :SendCommand cmd-opts)

     _ (dbg "Waiting for commands to complete")
     cmd-id (-> cdata :Command :CommandId)
     done-fn (fn [data]
               (let [res (-> data :Commands first)
                     {:keys [Status StatusDetails CompletedCount TargetCount]} res
                     done? (#{"Success"
                              "Failed"
                              "Delivery Timed Out"
                              "Execution Timed Out"} Status)]
                 (dbg (str "Commands "
                           (if done? "complete" "running ")
                           " (status: " Status
                           ", details: " StatusDetails
                           ", count: " CompletedCount "/" TargetCount ")"))
                 done?))
     wait-res (aws/invoke-until done-fn :SSM :ListCommands
                                (merge aws-opts {:CommandId cmd-id
                                                 :wait-sleep 1000}))
     _ (dbg "Gather command results")
     cmd-res (aws/invoke :SSM :ListCommandInvocations
                         (merge aws-opts {:CommandId cmd-id
                                          :Details true}))
     instance-map (zipmap (map :InstanceId instances) instances)
     results (merge (-> wait-res :Commands first)
                    {:Instances
                     (for [inv (:CommandInvocations cmd-res)
                           :let [id (:InstanceId inv)
                                 inst (get instance-map id)]]
                       (assoc inst :CommandInvocation inv))})]
    results))

(def line-re (js/RegExp. "^" "gm"))

(defn print-run-results
  "Print the results of a call to stack-run. Each line of output will
  be prefixed with result and instance name.

  opts keys:
    :debug     : show debug about AWS invocations
    :verbose   : include instance ID in prefix
    :no-prefix : omit the prefix from lines of output
    :quiet     : do not print the leading overall summary line
    :name-fn   : a function to get the instance name (default is Name tag)
    :edn       : output all the results as EDN
    :json      : output all the results as JSON
    :pretty    : pretty print the EDN/JSON output"
  [results {:keys [verbose no-prefix quiet name-fn edn json pretty]
            :or {name-fn #(get-in % [:TagMap "Name"])}}]
  (cond
    edn
    (if pretty (pprint results) (prn results))

    json
    (if pretty
      (println (js/JSON.stringify (clj->js results) nil 2))
      (println (js/JSON.stringify (clj->js results))))

    :else
    (let [{:keys [Status StatusDetails ErrorCount TargetCount]} results
          instances (for [inst (:Instances results)]
                      (assoc inst :Name (name-fn inst)))
          name-pad (apply max (map (comp count :Name) instances))]
      (when (not quiet)
        (Eprintln (str "Overall results - status: '" Status "', "
                       "details: '" StatusDetails "', "
                       "success/total: "
                       (- TargetCount ErrorCount) "/" TargetCount)))
      (doseq [inst instances]
        (let [{:keys [ResponseCode Output]}
              , (-> inst :CommandInvocation :CommandPlugins (get 0))
              in-pre (right-pad (:Name inst) name-pad)
              ec-pre (right-pad (str "[" ResponseCode "]") 5)
              prefix (if no-prefix
                       ""
                       (if verbose
                         (str ec-pre " " (:InstanceId inst) "  " in-pre "  ")
                         (str ec-pre " " in-pre "  ")))
              output (-> Output
                         (S/replace #"\n*$" "")
                         (.replace line-re prefix))]
          (println output))))))

(defn describe-all-stacks
  "Perform a ListStacks then a DescribeStack[s] for each stack."
  [opts]
  (P/let
    [{:keys [filter-fn log-fn parallel]} opts
     aws-opts (select-keys opts [:debug :profile :no-profile :region :role-arn])
     stack-list (P/->> (aws/invoke :CloudFormation :ListStacks aws-opts)
                       :StackSummaries)
     stack-list (if filter-fn (filter filter-fn stack-list) stack-list)
     _ (when log-fn (log-fn (str "Querying " (count stack-list) " stacks ("
                                 parallel " at a time)")))
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
                  _ (when log-fn (log-fn (str "Queried " (count res) "/" (count stack-list))))
                  (P/recur (drop parallel left) res))))]
    stacks))
