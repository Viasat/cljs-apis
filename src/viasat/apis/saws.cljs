(ns viasat.apis.saws
  (:require [cljs.pprint :refer [pprint]]
            [clojure.string :as S]
            [clojure.edn :as edn]
            [promesa.core :as P]
            [viasat.util :refer [Eprintln Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.aws.core :as aws]))


(def usage-general "
Usage:
  saws <service> <command> [options] [<other>...]")

(def usage-options "
Options:
  --verbose              Verbose output [env: VERBOSE]
  --debug                Show debug/trace output (stderr) [env: DEBUG]
  --profile PROFILE      AWS profile [env: PROFILE] [default: saml]
  --no-profile           Do not use a profile value
                         [env: NO_PROFILE]
  --role-arn             ARN of role to use assume for execution
  --region REGION        AWS region [env: REGION] [default: us-west-2]
  -p, --parameters EDN   EDN string with command parameters
                         (e.g. '{InstanceIds [\"i-123\" \"i-456\"]}')
  --extract EXTRACT      Path into results to extract (comma or space separated)
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

(def service-aliases
  {"EC2"             :ec2
   "VPC"             :vpc
   "IAM"             :iam
   "cf"              :cfn
   "cloudformation"  :cfn
   "cloud-formation" :cfn
   "CloudFormation"  :cfn
   "ServiceCatalog"  :sc
   "servicecatalog"  :sc
   "service-catalog" :sc
   "db"              :dynamodb
   "dynamo"          :dynamodb})

(def client-aliases
  ;;         Client name
  {:ec2      "EC2"
   :vpc      "EC2"
   :iam      "IAM"
   :cfn      "CloudFormation"
   :sc       "ServiceCatalog"
   :dynamodb "DynamoDB"
   :ecr      "ECR"})

(def arg->param
  {:name                :Name
   :id                  :Id
   :instance-id         :InstanceId
   :stack               :StackName
   :product             :ProductName
   :product-id          :ProductId
   :artifact-version    :ProvisioningArtifactName
   :artifact-version-id :ProvisioningArtifactId
   :table               :TableName
   :prov-name           :ProvisionedProductName
   :repo                :repositoryName})

(def extract-actions
  {:raw           #(map identity %)
   :remove-nulls  #(filter identity %)
   :decode-base64 #(->> % (filter some?) (map (fn [s] (.toString (.from js/Buffer s "base64")))))})

(defn extract-data
  [response schema]
  (if (not schema)
    (let [ks (keys (dissoc response
                           :$metadata :NextToken :NextPageToken
                           :IsTruncated :Marker :TotalResultsCount))]
      (if (= 0 (count ks))
        []
        (if (= 1 (count ks))
          (get response (first ks))
          (throw (js/Error.
                   (str "No extract schema defined and data has multiple keys: " ks))))))
    (loop [result response
           [path & schema] schema]
      (if path
        (let [exfn (if (map? path)
                    (get extract-actions (:action path))
                    (fn [xs] (map #(get % path) xs)))
              res (if (sequential? result)
                    (let [res (exfn result)]
                      (if (vector? (first res))
                        (apply concat res)
                        res))
                    (first (exfn [result])))]
          (recur res schema))
        result))))

(defn arg->keyword [arg]
  (keyword (second (first (re-seq #"^:?(.*)$" arg)))))

(def TIME-REGEX #"Time$|^createdAt|^imagePushedAt$")

(defn run
  "Invoke an AWS service API command, extract the data, and print the
  result.  The service keyword will be used to find and load the AWS
  SDK module for that service. The command keyword will be used to
  find the command within that module. The command-schema defines how
  to extract and print the results of the call.

  cfg keys:
    :debug      : debug output and AWS call tracing
    :parameters : EDN string with additional parameters for the AWS call
    :extract    : a path (comma separated) into the data. Overrides
                  command-schema extract definition.
  "
  [cfg service command command-schema]
  (P/catch
    (P/let
      [{:keys [debug parameters extract]} cfg
       dbg (if debug Eprintln identity)

       _ (dbg "Command line Configuration:")
       _ (when debug (Epprint cfg))

       command-schema (merge {:time-regex TIME-REGEX}
                             command-schema)
       client-name (get client-aliases service service)
       command-name (get command-schema :command command)
       extract-schema (if extract
                        (map keyword (S/split extract #"[, ]"))
                        (get command-schema :extract))

       aws-opts (select-keys cfg [:debug :profile :no-profile :region :role-arn])
       user-params (when parameters (edn/read-string parameters))
       cmd-params (merge aws-opts user-params
                         (reduce
                           (fn [params [k v]]
                             (if (contains? arg->param k)
                               (assoc params (get arg->param k) v)
                               params))
                           {} cfg))

       _ (dbg "Other settings:")
       _ (dbg "  Client Name:    " client-name)
       _ (dbg "  Command Name:   " command-name)
       _ (dbg "  Command Params: " cmd-params)
       _ (dbg "  Command Schema: " command-schema)
       _ (dbg "  Extract Schema: " extract-schema)
       resp (aws/invoke client-name command-name cmd-params)
       data (extract-data resp extract-schema)]
      (schema-print data command-schema (assoc cfg :dbg dbg)))
    (fn [e]
      (binding [*out* *err*]
        (println "Error:" (ex-message e))
        (pprint (ex-data e))
        (js/process.exit 1)))))
