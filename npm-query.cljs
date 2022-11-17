#!/usr/bin/env nbb
(ns npm-query
  (:require [promesa.core :as P]
            [viasat.util :refer [parse-opts Epprint Eprintln]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.npm :as npm]))

(def usage "
Usage:
  npm-query [options] <package-name>

Options:
  --debug                  Debug output [env: DEBUG]
  --base-url URL           RPM repo base URL
                           [default: https://registry.npmjs.com]

  --fields FIELDS          Fields to print (comma or space separated)
  --sort FIELD             Sort using FIELD
  --rsort FIELD            Reverse sort using FIELD
  --field-max FIELD_MAX    Maximum field width (when not --verbose) [default: 100]
  --no-headers             Skip printing headers
  --json                   Print full results as JSON (default)
  --edn                    Print full results as EDN
  --pretty                 Pretty print --json or --edn")


(def npm-schema
  {:fields [:name
            :version
            :time]
   :sort :time})

(P/let
  [opts (parse-opts usage (or *command-line-args* []))
   {:keys [debug package-name]} opts
   dbg (if debug Eprintln identity)
   _ (when debug (Epprint {:opts opts}))
   versions (npm/get-versions package-name (assoc opts :dbg dbg))]

  (schema-print versions npm-schema opts))


