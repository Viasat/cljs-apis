#!/usr/bin/env nbb

(ns artifactory-docker-query
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [parse-opts Eprintln Eprn Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.artifactory :as art]))

(def usage "
Usage:
  artifactory-docker-query [options] <repo> <image-names>...

Options:
  --debug                  Debug output [env: DEBUG]
  --verbose                Verbose output [env: VERBOSE]
  --artifactory-api URL    Artifactory base API URL
                           [env: ARTIFACTORY_BASE_URL]

  --fields FIELDS          Fields to print (comma or space separated)
  --sort FIELD             Sort using FIELD
  --rsort FIELD            Reverse sort using FIELD
  --field-max FIELD_MAX    Maximum field width (when not --verbose) [default: 100]
  --no-headers             Skip printing headers
  --json                   Print full results as JSON (default)
  --edn                    Print full results as EDN
  --pretty                 Pretty print --json or --edn")

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
  [opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug verbose repo image-names]} opts
   _ (when debug
       (Epprint {:opts opts})
       (art/enable-debug))
   auth-headers (art/get-auth-headers opts)
   opts (assoc opts :axios-opts {:headers auth-headers})
   schema (merge {:time-regex TIME-REGEX}
                 (if verbose IMAGE-SCHEMA-VERBOSE IMAGE-SCHEMA))

   images (if (seq image-names)
            image-names
            (do
              (Eprintln "Getting image names from repo:" repo)
              (art/get-images repo opts)))
   _ (Eprintln "Getting information (in parallel) for images:" images)
   data (artifactory/get-full-images repo images)]

  (schema-print data schema opts))


