#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns artifactory-npm-query
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [parse-opts Eprintln Eprn Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.artifactory :as art]
            ["dotenv$default" :as dotenv]))

(def usage "
Usage:
  artifactory-npm-query [options] <repo> <module-names>...

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

(def MODULE-SCHEMA
  {:fields [:name
            :version
            :description
            :author
            :main
            :created
            [:repository [:repository :url]]
            [:shasum [:dist :shasum]]]})



(P/let
  [_ (dotenv/config #js {:path ".secrets"})
   opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug verbose repo module-names
           artifactory-base-url artifactory-username artifactory-identity-token]} opts
   _ (when debug
       (Epprint {:opts opts})
       (art/enable-debug))

   auth-headers (art/get-auth-headers opts)
   opts (assoc opts
               :axios-opts {:headers auth-headers}
               :artifactory-api (str artifactory-base-url "/api"))
   schema MODULE-SCHEMA

   modules (if (seq module-names)
             module-names
             (do
               (Eprintln "Getting image names from repo:" repo)
               (art/get-npm-modules repo opts)))
   _ (Eprintln "Getting information (in parallel) for modules:" modules)
   data (art/get-npm-full-modules repo modules opts)]
  (schema-print data schema opts))
