;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.artifactory
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["debug$default" :as debug]
            ["axios$default" :as axios]
            ["axios-retry$default" :as axios-retry]
            ["prompt$default" :as prompt]))

(defn enable-debug []
  (debug/enable "axios")
  (js/require "axios-debug-log/enable"))

(def log (debug "axios"))

;; Users can override any of these settings via the by setting
;; 'axios-retry' in the axios-opts.
(axios-retry axios
             (clj->js
              {:retries 3
               :retryDelay axios-retry/exponentialDelay
               :onRetry
               (fn [retryCount, _, requestConfig]
                 (log "Retry" retryCount "for" (-> requestConfig ->clj :url)))
               :retryCondition
               (some-fn
                 axios-retry/isNetworkOrIdempotentRequestError
                 ;; E.g. connection timeouts
                 #(= (.-code %) "ECONNABORTED"))
               :shouldResetTimeout true}))

(defn prompt-when-missing-credentials [opts]
  (P/let [{:keys [artifactory-username artifactory-identity-token]} opts
          prompt-schema [{:name "artifactory-username"
                          :description "Artifactory Username"}
                         {:name "artifactory-identity-token"
                          :description "Artifactory API Token"
                          :hidden true}]
          overrides (merge {}
                           (when artifactory-username {:artifactory-username artifactory-username})
                           (when artifactory-identity-token {:artifactory-identity-token artifactory-identity-token}))
          _ (set! (.-override prompt) (clj->js overrides))
          _ (set! (.-message prompt) "Please enter")
          prompt-results (P/-> (.get prompt (clj->js prompt-schema)) ->clj)]
    (merge opts prompt-results)))

(defn get-auth-headers [opts]
  (let [{:keys [artifactory-username
                artifactory-identity-token]} opts
        auth-token (if (and artifactory-username
                            artifactory-identity-token)
                     (->
                       (js/Buffer.from (str artifactory-username ":" artifactory-identity-token))
                       (.toString "base64"))
                     (throw (js/Error. "Artifactory username and identity token required.")))]
    {:Authorization (str "Basic " auth-token)
     :Content-Type "application/json"}))

(defn get-images [repo
                  {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (str artifactory-api "/docker/" repo "/v2/_catalog")
          resp (axios url (clj->js axios-opts))]
    (P/-> resp ->clj :data :repositories)))


(defn get-image-tags [repo image
                      {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (str artifactory-api "/docker/" repo "/v2/" image "/tags/list")
          resp (axios url (clj->js axios-opts))]
    (P/-> resp ->clj :data :tags)))

(defn get-manifest-uri [repo image tag
                        {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [types #{"/manifest.json" "/list.manifest.json"}
          url (str artifactory-api "/storage/" repo "/" image "/" tag)
          resp (axios url (clj->js axios-opts))
          manifest (->> resp ->clj :data :children (map :uri) (filter types) first)]
    (str url manifest)))

(defn get-tag-manifest [repo image tag
                        {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (get-manifest-uri repo image tag opts)
          resp (axios url (clj->js axios-opts))]
    (P/-> resp ->clj :data)))

(defn get-tag-manifest-metadata [repo image tag
                                 {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (get-manifest-uri repo image tag opts)
          resp (axios url (clj->js (merge axios-opts
                                          {:params {:stats true}})))]
    (P/-> resp ->clj :data)))

(defn get-full-images [repo images
                       {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [raw (P/all
                (for [image images]
                  (P/let
                    [tags (get-image-tags repo image opts)
                     manifests (P/all (for [tag tags]
                                        (get-tag-manifest
                                          repo image tag opts)))
                     metadatas (P/all (for [tag tags]
                                        (get-tag-manifest-metadata
                                          repo image tag opts)))]
                    (map #(merge %3 %2 {:image image :tag %1})
                         tags manifests metadatas))))]
    (apply concat raw)))

(defn get-npm-modules [repo
                       {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (str artifactory-api "/search/artifact")
          opts (merge axios-opts
                      {:params {:repos repo
                                :name "package.json"}})
          resp (axios url (clj->js opts))]
    (P/->> resp ->clj :data :results
           (map #(->> (:uri %)
                      (re-seq #"/.npm/(.*)/package.json")
                      first second)))))

(defn get-npm-module [repo module
                      {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (str artifactory-api "/npm/" repo "/" module)
          resp (axios url (clj->js axios-opts))]
    (P/-> resp ->clj :data)))

(defn get-npm-full-modules [repo modules
                            {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [raw (P/all
                (for [module modules]
                  (P/let
                    [data (get-npm-module repo module opts)
                     versions (-> data :versions)]
                    (map (fn [[vkey vdata]]
                           (merge vdata
                                  {:created (get-in data [:time vkey])}))
                         versions))))]
    (apply concat raw)))
