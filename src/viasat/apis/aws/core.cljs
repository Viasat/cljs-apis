(ns viasat.apis.aws.core
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [Eprintln pascal->snake]]))

;; module suffixes with non-standard mappings from client name
(def client-module-map
  {:CloudFormation  "cloudformation"
   :CloudWatchLogs  "cloudwatch-logs"
   :DynamoDB        "dynamodb"})

(defn sdk-load [prefix client & [item]]
  (let [item (name (or item client))
        suffix (get client-module-map (keyword client)
                    (-> client name pascal->snake))
        mod-name (str "@aws-sdk/" prefix "-" suffix)
        aws-module (js/require mod-name)
        obj (aget aws-module item)]
    (when (not obj)
      (throw (ex-info (str obj " not found in " mod-name) {})))
    obj))

(declare invoke)

(defn auth-client
  "Instantiate AWS client using opts map parameters. If :role-arn is
  specified in opts then an STS assume role is performed first."
  [client {:keys [profile no-profile role-arn region] :as opts}]
  (P/let [{:keys [getDefaultRoleAssumerWithWebIdentity]}
          , (->clj (js/require "@aws-sdk/client-sts"))
          {:keys [defaultProvider]}
          , (->clj (js/require "@aws-sdk/credential-provider-node"))
          Client (sdk-load "client" client)

          sts-opts (merge (dissoc opts :role-arn)
                          {:RoleArn role-arn
                           :RoleSessionName "session1"
                           :DurationSeconds 900})
          prov-opts (merge (when (not no-profile)
                             {:profile profile})
                           {:roleAssumerWithWebIdentity
                            , getDefaultRoleAssumerWithWebIdentity})

          cli-opts (if role-arn
                     (P/let [creds (P/-> (invoke :STS :AssumeRole sts-opts)
                                         :Credentials)]
                       {:credentials {:accessKeyId     (:AccessKeyId creds)
                                      :secretAccessKey (:SecretAccessKey creds)
                                      :sessionToken    (:SessionToken creds)}})
                     (P/let [provider (defaultProvider (clj->js prov-opts))]
                       {:credentialDefaultProvider provider}))
          client (Client. (clj->js (assoc cli-opts :region region)))]
    client))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General API commands
(defn invoke
  "Invoke AWS client command using opts map parameters. If response is
  paginated then repeatedly call the command and returned the full
  collected result."
  [client command {:keys [debug] :as opts}]
  (P/let [client (if (or (keyword? client) (string? client))
                (auth-client client opts)
                client)
          client-name (-> client .-constructor .-name)
          Command (if (or (keyword? command) (string? command))
                    (sdk-load "client" client-name (str (name command) "Command"))
                    command)
          command-name (.-name Command)]
    (P/loop [result {}
             opts (dissoc opts :profile :no-profile :region :debug)
             page 1
             last-token nil]
      (when debug
        (Eprintln "AWS DEBUG - sending" client-name command-name
                  "with opts" (pr-str opts)))
      (P/let [command (Command. (clj->js opts))
              data (P/-> (.send client command) ->clj)
              {:keys [NextToken nextToken nextForwardToken]} data
              token (or NextToken nextToken nextForwardToken)
              data (dissoc data
                           :NextToken :nextToken
                           :nextBackwardToken :nextForwardToken)
              result (merge-with into result data)]
        (if (and token (not= last-token token))
          (do (when debug
                (Eprintln "AWS DEBUG - sending" client-name command-name
                          "again to get page" (inc page)))
              (P/recur result (merge opts (cond
                                            NextToken {:NextToken NextToken}
                                            nextToken {:nextToken nextToken}
                                            nextForwardToken {:nextToken
                                                              nextForwardToken}))
                       (inc page) token))
          result)))))

(defn invoke-until
  "Invoke AWS Client Command using opts map parameters. Repeat the
  invocation until the pred function is true against the returned data.
  Delay wait-sleep milliseconds between each execution (default
  2000)."
  [pred client command {:as opts
                        :keys [wait-sleep]
                        :or {wait-sleep 2000}}]
  (P/loop []
    (P/let [res (invoke client command (dissoc opts :wait-sleep))]
      (if (pred res)
        res
        (P/do
          (P/delay wait-sleep nil)
          (P/recur))))))

(def utf8-decoder (js/TextDecoder.))

(defn lambda-call [opts lambda payload]
  (P/let
    [resp (P/-> (invoke :Lambda :Invoke
                        (merge opts {:FunctionName lambda
                                     :Payload (js/JSON.stringify (clj->js payload))}))
                ->clj)
     payload (->> resp :Payload (.decode utf8-decoder))
     decoded-resp (assoc resp :Payload payload)]
    (if-let [err (:FunctionError resp)]
      (throw (ex-info payload decoded-resp))
      (with-meta (-> payload js/JSON.parse ->clj) {:response decoded-resp}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Other AWS utility functions
(defn list->map
  "AWS often represents map data (tags, params, outputs, etc) as
  a list of maps which each contain a single key and value. Turn the
  list at path-fn into a map using key-fn and val-fn."
  [path-fn key-fn val-fn obj]
  (reduce (fn [m tag] (assoc m (key-fn tag) (val-fn tag))) {} (path-fn obj)))
(def get-tag-map (partial list->map :Tags :Key :Value))
(def get-param-map (partial list->map :Parameters :ParameterKey :ParameterValue))
(def get-output-map (partial list->map :Outputs :OutputKey :OutputValue))

(defn filter-by-tags
  "Filter objects by tags/values. Return the objects where all of the
  tags-values pairs are matched in the object tags."
  [objects & tags-values]
  (filter (fn [obj]
            (let [tag-map (get-tag-map obj)]
              (every? (fn [[tag value]]
                        (= (str value) (str (get tag-map tag))))
                      tags-values)))
          objects))

