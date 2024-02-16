;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.docker
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["axios$default" :as axios]))

(defn get-image-tags [image
                      {:keys [dbg namespace base-url axios-opts]
                       :or {dbg identity}}]
  (P/let
    [;; set base-url/namespace if unset or set but nil
     base-url (or base-url "https://hub.docker.com/v2/namespaces/")
     namespace (or namespace "library")
     url (str base-url "/" namespace "/repositories/" image "/tags")
     _ (dbg "Downloading" url)
     resp (axios url (clj->js axios-opts))]
    (P/->> resp ->clj :data :results
           (map #(merge {:image image :namespace namespace} %)))))

