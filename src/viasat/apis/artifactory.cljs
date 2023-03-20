(ns viasat.apis.artifactory
  (:require [clojure.pprint :refer [pprint]]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["dotenv$default" :as dotenv]
            ["axios$default" :as axios]
            ["prompt$default" :as prompt]))

(defn enable-debug []
  (js/require "axios-debug-log/enable"))

(defn get-auth-headers [opts]
  (dotenv/config #js {:path ".secrets"})
  (P/let
    [{:keys [username password]
      :or {username (aget js/process.env "ARTIFACTORY_USERNAME")
           password (or (aget js/process.env "ARTIFACTORY_IDENTITY_TOKEN")
                        (aget js/process.env "ARTIFACTORY_API_KEY")
                        (aget js/process.env "ARTIFACTORY_PASSWORD"))}} opts
     prompt-schema [{:name "username"
                     :description "Artifactory Username"}
                    {:name "password"
                     :description "Artifactory Password/API Key"
                     :hidden true}]
     overrides (merge {}
                      (when username {:username username})
                      (when password {:password password}))
     _ (set! (.-override prompt) (clj->js overrides))
     _ (set! (.-message prompt) "Please enter")
     {:keys [username password]}
     , (P/-> (.get prompt (clj->js prompt-schema)) ->clj)
     auth-token (->
                  (js/Buffer.from (str username ":" password))
                  (.toString "base64"))]
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
