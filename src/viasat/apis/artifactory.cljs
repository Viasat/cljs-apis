;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns viasat.apis.artifactory
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as S]
            [promesa.core :as P]
            [cljs-bean.core :refer [->clj]]
            ["debug$default" :as debug]
            ["axios$default" :as axios]
            ["axios-retry$default" :as axios-retry]))

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

(defn get-auth-headers [opts]
  (let [{:keys [artifactory-username
                artifactory-identity-token]} opts
        auth (cond
               (and (not (empty? artifactory-username))
                    (not (empty? artifactory-identity-token)))
               (->
                 (js/Buffer.from (str artifactory-username ":" artifactory-identity-token))
                 (.toString "base64")
                 (->> (str "Basic ")))

               (not (empty? artifactory-identity-token))
               (str "Bearer " artifactory-identity-token)

               :else
               (throw (js/Error. "Artifactory ID token or username+token required.")))]
    {:Authorization auth
     :Content-Type "application/json"}))

(defn compile-aql
  "Return AQL query map as a string. See: https://jfrog.com/help/r/jfrog-rest-apis/aql-syntax
  <domain_query>.find(<criteria>).include(<fields>).sort(<order_and_fields>).offset(<offset_records>).limit(<num_records>).distinct(<true|false>)"
  [aql]
  (let [transform {:domain name
                   :find #(.stringify js/JSON (clj->js %))
                   :include #(S/join ", "
                                     (map (fn [field]
                                            (str "\"" (name field) "\""))
                                          %))
                   :sort #(.stringify js/JSON (clj->js %))
                   :offset identity
                   :limit identity
                   :distinct identity}
        aql (reduce (fn [res [k tx-fn]]
                      (if (get res k)
                        (update res k tx-fn)
                        res))
                    aql
                    transform)
        {:keys [domain find include sort offset limit distinct]} aql]
    (str domain ".find(" find ")"
         (when include (str ".include(" include ")"))
         (when sort (str ".sort(" sort ")"))
         (when offset (str ".offset(" offset ")"))
         (when limit (str ".limit(" limit ")"))
         (when distinct (str ".distinct(" distinct ")")))))

(defn search
  "Execute AQL query via search API. Return map of 'results' and 'range'."
  [aql {:keys [artifactory-api axios-opts] :as opts}]
  (P/let [aql (compile-aql aql)
          _ (log "Search AQL: " aql)
          url (str artifactory-api "/search/aql")
          axios-opts (assoc-in axios-opts [:headers :Content-Type] "text/plain")
          resp (axios.post url aql (clj->js axios-opts))]
    (-> resp .-data ->clj)))

(defn search-all
  "Repeatedly execute AQL query via search API, appending '.offset(..)' to gather
  all paginated results. Return sequence of all found results."
  [aql opts]
  (P/loop [rows []]
    (P/let [offset (count rows)
            {:keys [results range]} (search (assoc aql :offset offset)
                                            opts)
            {:keys [end_pos total]} range
            rows (into rows results)]
      (if (= end_pos total)
        rows
        (P/recur rows)))))

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
  (P/let [conditions {:$and [{:repo {:$eq repo}}
                             {:$or (for [image images]
                                     {:path {:$match (str image "/*")}})}
                             {:$or [{:name {:$eq "manifest.json"}}
                                    {:name {:$eq "list.manifest.json"}}]}]}
          fields [:repo :path :name :type :size
                  :created :created_by :modified :modified_by :updated
                  :actual_md5 :actual_sha1 :original_md5 :original_sha1 :sha256 :stat]
          aql {:domain :items
               :find conditions
               :include fields}
          items (search-all aql opts)]
    (for [item items]
      (let [{:keys [path created_by modified modified_by updated
                    actual_md5 actual_sha1 original_md5 original_sha1 sha256
                    stats]} item
            {:keys [downloaded downloaded_by downloads]} (first stats)
            [_ image tag] (re-find #"^(.+)/([^/]+)$" path)]
        (merge item
               {:image image
                :tag tag
                ;; Attempting as much compatability with 'get-tag-manifest and
                ;; 'get-tag-manifest-metadata -based responses as possible
                :mimeType "application/json"
                :createdBy created_by
                :lastModified modified
                :modifiedBy modified_by
                :lastUpdated updated
                :checksums {:sha1 actual_sha1
                            :md5 actual_md5
                            :sha256 sha256}
                :originalChecksums {:sha1 original_sha1
                                    :md5 original_md5
                                    :sha256 sha256}
                :downloadCount downloads
                :lastDownloaded downloaded
                :lastDownloadedBy downloaded_by})))))

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
