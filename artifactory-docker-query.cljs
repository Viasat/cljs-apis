#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns artifactory-docker-query
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [parse-opts Eprintln Eprn Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.artifactory :as art]
            ["dotenv$default" :as dotenv]))

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
  --artifactory-identity-token TOKEN  Artifactory identity token. If no username is
                                      provided, then assume this is a bearer token.
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

(P/let
  [_ (dotenv/config #js {:path ".secrets"})
   opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug verbose repo image-names
           artifactory-base-url artifactory-username artifactory-identity-token]} opts
   _ (when debug
       (Epprint {:opts opts})
       (art/enable-debug))

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
