#!/usr/bin/env nbb
(ns rpm-repo-query
  (:require [promesa.core :as P]
            [viasat.util :refer [parse-opts Epprint Eprintln]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.rpm :as rpm]))

(def usage "
Usage:
  rpm-repo-query [options] <repo> <rpm-names>...

Options:
  --debug                  Debug output [env: DEBUG]
  --base-url URL           RPM repo base URL
                           [env: RPM_REPO_BASE_URL]

  --fields FIELDS          Fields to print (comma or space separated)
  --sort FIELD             Sort using FIELD
  --rsort FIELD            Reverse sort using FIELD
  --field-max FIELD_MAX    Maximum field width (when not --verbose) [default: 100]
  --no-headers             Skip printing headers
  --json                   Print full results as JSON (default)
  --edn                    Print full results as EDN
  --pretty                 Pretty print --json or --edn")


(def rpm-schema
  {:fields [:name
            :summary
            [:version    [:version :ver]]
            [:release    [:version :rel]]
            :arch
            [:build-time [:time :build]]
            [:size       [:size :installed]]]
   :sort :build-time})

(P/let
  [opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug repo]} opts
   dbg (if debug Eprintln identity)
   _ (when debug (Epprint {:opts opts}))
   rpms (rpm/get-rpms repo (assoc opts :dbg dbg))]

  (schema-print rpms rpm-schema opts))


