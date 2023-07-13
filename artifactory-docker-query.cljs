#!/usr/bin/env nbb

(ns artifactory-docker-query
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [parse-opts Eprintln Eprn Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.artifactory :as art]
            ["dotenv$default" :as dotenv]
            ["prompt$default" :as prompt]))

(def usage "
Usage:
  artifactory-docker-query [options] <repo> <image-names>...

Options:
  --debug                             Debug output [env: DEBUG]
  -v, --verbose                       Verbose output [env: VERBOSE]

  --fields FIELDS                     Fields to print (comma or space separated)
  --sort FIELD                        Sort using FIELD
  --rsort FIELD                       Reverse sort using FIELD
  --field-max FIELD_MAX               Maximum field width (when not --verbose) [default: 100]
  --no-headers                        Skip printing headers
  --json                              Print full results as JSON (default)
  --edn                               Print full results as EDN
  --pretty                            Pretty print --json or --edn

  --artifactory-base-url URL          Artifactory base URL
                                      [env: ARTIFACTORY_BASE_URL]
  --artifactory-username USERNAME     Artifactory username
                                      [env: ARTIFACTORY_USERNAME]
  --artifactory-identity-token TOKEN  Artifactory identity token
                                      [env: ARTIFACTORY_IDENTITY_TOKEN]")

(def IMAGE-SCHEMA
  {:fields [:image
            :tag
            :downloadCount
            :size
            :createdBy
            :lastUpdated
            :lastDownloaded]
   :sort :lastUpdated})

(def IMAGE-SCHEMA-VERBOSE
  {:fields [:image
            :tag
            :downloadCount
            :size
            :createdBy
            :modifiedBy
            :lastModified
            :created
            :lastUpdated
            :lastDownloaded
            [:sha256 [:checksums :sha256]]]
   :sort :lastUpdated})

(def TIME-REGEX #"^lastDownloaded")

(defn prompt-when-missing-credentials [opts]
  (P/let [{:keys [artifactory-username artifactory-identity-token]} opts
          prompt-schema [{:name "artifactory-username"
                          :description "Artifactory Username"}
                         {:name "artifactory-identity-token"
                          :description "Artifactory API Token"
                          :hidden true}]
          overrides (merge {}
                           (when artifactory-username {:artifactory-username artifactory-username})
                           (when artifactory-identity-token {:artifactory-identity-token artifactory-identity-token}))
          _ (set! (.-override prompt) (clj->js overrides))
          _ (set! (.-message prompt) "Please enter")
          prompt-results (P/-> (.get prompt (clj->js prompt-schema)) ->clj)]
    (merge opts prompt-results)))

(P/let
  [_ (dotenv/config #js {:path ".secrets"})
   opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug verbose repo image-names
           artifactory-base-url artifactory-username artifactory-identity-token]} opts
   _ (when debug
       (Epprint {:opts opts})
       (art/enable-debug))

   opts (prompt-when-missing-credentials opts)

   auth-headers (art/get-auth-headers opts)
   opts (assoc opts
               :axios-opts {:headers auth-headers}
               :artifactory-api (str artifactory-base-url "/api"))
   schema (merge {:time-regex TIME-REGEX}
                 (if verbose IMAGE-SCHEMA-VERBOSE IMAGE-SCHEMA))

   images (if (seq image-names)
            image-names
            (do
              (Eprintln "Getting image names from repo:" repo)
              (art/get-images repo opts)))
   _ (Eprintln "Getting information (in parallel) for images:" images)
   data (art/get-full-images repo images opts)]

  (schema-print data schema opts))
