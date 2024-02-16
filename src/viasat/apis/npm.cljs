;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.npm
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["axios$default" :as axios]))

(defn get-package [pkg {:keys [dbg base-url]
                        :or {dbg identity}}]
  (P/let
    [;; set base-url if unset or set but nil
     base-url (or base-url "https://registry.npmjs.com")
     url (str base-url "/" pkg)
     _ (dbg "Downloading" url)
     pkg (P/-> (axios url) ->clj :data)]
    pkg))

(defn get-versions [pkg opts]
  (P/let
    [pkg (get-package pkg opts)
     versions-times (:time pkg)
     versions (for [v (-> pkg :versions vals)]
                (assoc v :time (get versions-times
                                    (keyword (get v :version)))))]
    versions))
