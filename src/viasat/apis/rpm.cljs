;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.rpm
  (:require [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            [clojure.string :as S]
            ["node:util" :refer [promisify]]
            ["node:zlib" :as zlib]
            ["axios$default" :as axios]
            ["fast-xml-parser" :as fastxml]))

(def unzip (promisify zlib/unzip))

(def parser (new fastxml.XMLParser #js {:ignoreAttributes false :attributeNamePrefix "" :textNodeName "$t"}))

(defn get-rpms [repo {:keys [dbg base-url rpm-names axios-opts]
                      :or {dbg identity}}]
  (P/let
    [repo-url (-> (when base-url
                    (S/replace base-url #"/*$" "/"))
                  (str repo)
                  (S/replace #"/*$" ""))
     repomd-url (str repo-url "/repodata/repomd.xml")
     _ (dbg "Downloading" repomd-url)
     resp (P/-> (axios repomd-url (clj->js axios-opts)) ->clj)

     _ (dbg "Converting repomd.xml to JSON")
     repomd (->clj (.parse parser (:data resp)))
     primary-path (->> repomd
                       :repomd
                       :data
                       (filter #(= "primary" (:type %)))
                       first
                       :location
                       :href)
     primary-url (str repo-url "/" primary-path)

     _ (dbg "Downloading" primary-url)
     resp (axios primary-url (clj->js (merge axios-opts
                                             {:responseType "arraybuffer"})))
     _ (dbg "Uncompressing" primary-url)
     primary-data (P/-> (-> resp .-data unzip) .toString)

     _ (dbg "Converting to JSON")
     primary (->clj (.parse parser primary-data))
     all-rpms (-> primary :metadata :package)
     rpms (if (seq rpm-names)
            (do
              (dbg "Filtering for rpm names" rpm-names)
              (filter #((set rpm-names) (:name %)) all-rpms))
            all-rpms)]
    rpms))
