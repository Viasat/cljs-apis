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

(defn get-tag-manifest [repo image tag
                        {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (str artifactory-api "/storage/" repo "/" image "/" tag "/manifest.json")
          resp (axios url (clj->js axios-opts))]
    (P/-> resp ->clj :data)))

(defn get-tag-manifest-metadata [repo image tag
                                 {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [url (str artifactory-api "/storage/" repo "/" image "/" tag "/manifest.json")
          resp (axios url (clj->js (merge axios-opts
                                          {:params {:stats true}})))]
    (P/-> resp ->clj :data)))
