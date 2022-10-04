#!/usr/bin/env nbb

(ns ghe
  (:require [clojure.string :as S]
            [promesa.core :as P]
            [viasat.util :refer [parse-opts Eprintln Epprint]]
            [viasat.schema-print :refer [schema-print]]
            [viasat.apis.github :as github]
            ["dotenv$default" :as dotenv]))

(def usage "
Usage:
  ghe [options] <endpoint> <action> <args>...

Options:
  --debug                Show debug/trace output (stderr) [env: DEBUG]
  --base-url URL         Github API base URL [env: GITHUB_BASE_URL]
                         [default: 'https://api.github.com']
  --github-token TOKEN   Github Personal Access Token [env: GITHUB_TOKEN]
  --owner OWNER          Github Owner [env: GITHUB_OWNER]
  --repo REPO            Github Repository [env: GITHUB_REPO]

  --table                Print results as a table
  --fields FIELDS        Fields to print (comma or space separated)
  --sort FIELD           Sort using FIELD
  --rsort FIELD          Reverse sort using FIELD
  --field-max FIELD_MAX  Maximum field width (when not --verbose) [default: 100]
  --no-headers           Skip printing headers
  --json                 Print full results as JSON (default)
  --edn                  Print full results as EDN
  --pretty               Pretty print --json or --edn")

(def TIME-REGEX #"-time$")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(dotenv/config #js {:path ".secrets"})

(P/let
  [cfg (parse-opts usage *command-line-args*)
   {:keys [debug base-url github-token owner repo table
           endpoint action args]} cfg
   _ (when debug (Eprintln "Settings:") (Epprint cfg))

   schema {:time-regex TIME-REGEX}

   client (github/ghe (merge {:auth github-token :baseUrl base-url}
                             (when debug {:log js/console})))
   cmd-opts (into {} (map #(S/split % #"[:=]") args))
   resp (github/REST client endpoint action
                     (merge (when owner {:owner owner})
                            (when repo {:repo repo})
                            cmd-opts))
   data (:data resp)
   result (if (and (map? data) (contains? data :total_count))
            (first (vals (dissoc data :total_count)))
            data)]
  (if table
    (schema-print result schema cfg)
    (schema-print result schema (merge {:json true} cfg))))

