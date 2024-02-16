#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns docker-query
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [viasat.util :refer [parse-opts Eprintln Eprn Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.docker :as docker]))

(def usage "
Usage:
  docker-query [options] <namespace> <image>

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

  --base-url URL                      Docker Hub base URL [env: DOCKER_HUB_BASE_URL]
                                      [default: https://hub.docker.com/v2/namespaces/]")

(def IMAGE-SCHEMA
  {:fields [:namespace
            :image
            [:tag [:name]]
            :full_size
            :last_updater_username
            :last_updated]
   :sort :last_updated})

(def IMAGE-SCHEMA-VERBOSE
  {:fields [:namespace
            :image
            [:tag [:name]]
            :full_size
            :last_updater_username
            :last_updated
            :tag_last_pushed
            :tag_last_pulled
            :digest]
   :sort :last_updated})

(def TIME-REGEX #"^(last_updated|tag_last_pushed|tag_last_pulled)")

(P/let
  [opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug verbose namespace image]} opts
   dbg (if debug Eprintln identity)
   _ (when debug (Epprint {:opts opts}))

   schema (merge {:time-regex TIME-REGEX}
                 (if verbose IMAGE-SCHEMA-VERBOSE IMAGE-SCHEMA))
   data (docker/get-image-tags image (assoc opts :namespace namespace))]

  (schema-print data schema opts))
