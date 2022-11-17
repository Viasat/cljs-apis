(ns viasat.apis.npm
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["axios$default" :as axios]))

(defn get-package [pkg {:keys [dbg base-url]
                        :or {dbg identity
                             base-url "https://registry.npmjs.com"}}]
  (P/let
    [url (str base-url "/" pkg)
     _ (dbg "Downloading" url)
     pkg (P/-> (axios url) ->clj :data)]
    pkg))

(defn get-versions [pkg {:keys [dbg base-url] :as opts}]
  (P/let
    [pkg (get-package pkg opts)
     versions-times (:time pkg)
     versions (for [v (-> pkg :versions vals)]
                (assoc v :time (get versions-times
                                    (keyword (get v :version)))))]
    versions))
