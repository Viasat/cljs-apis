(ns viasat.apis.github
  (:require [clojure.string :as S]
            [cljs-bean.core :refer [->clj]]
            [clojure.set :refer [difference]]
            [promesa.core :as P]
            [viasat.util :refer [fatal]]
            ["@octokit/rest" :refer [Octokit]]))


(defn ghe [opts]
  (let [opts (->clj opts)
        baseUrl (-> (get opts :baseUrl "https://api.github.com")
                    (S/replace #"[/]*$" ""))
        opts (assoc opts :baseUrl baseUrl)]
    (Octokit. (clj->js opts))))

(defn REST [client endpoint action opts]
  (P/let
    [log (or (-> client (aget "log") (aget "log"))
             list) ;; if no logger then use list to ignore
     rest-obj (aget client "rest")
     endpoint-obj (aget rest-obj endpoint)
     _ (when (not endpoint-obj)
         (fatal 1 (str "No endpoint '" endpoint "' found in: "
                       (S/join ", " (sort (keys (js->clj rest-obj)))))))
     action-obj (aget endpoint-obj action)
     _ (when (not action-obj)
         (fatal 1 (str "No action '" action "' found in: "
                       (S/join ", " (sort (keys (js->clj endpoint-obj)))))))

     url (-> action-obj (aget "endpoint") (aget "DEFAULTS") (aget "url"))
     req-params (set (map second (re-seq #"{([^}]*)}" url)))
     missing-params (difference req-params (set (map name (keys opts))))
     _ (when (seq missing-params)
         (fatal 1 (str "Missing param(s) from URL '" url "': "
                       (S/join ", " missing-params))))

     _ (log (str "URL: " url ", opts: " opts))

     res (action-obj (clj->js opts))]
  (->clj res)))
