(ns viasat.apis.github
  (:require [clojure.string :as S]
            [cljs-bean.core :refer [->clj]]
            [promesa.core :as P]
            ["@octokit/rest" :refer [Octokit]]))


(defn ghe [opts]
  (let [opts (->clj opts)
        baseUrl (-> (get opts :baseUrl "https://api.github.com")
                    (S/replace #"[/]*$" ""))
        opts (assoc opts :baseUrl baseUrl)]
    (Octokit. (clj->js opts))))

(defn REST [client endpoint action opts]
  (P/let
    [req (-> client (aget "rest") (aget endpoint) (aget action))
     res (req (clj->js opts))]
  (->clj res)))
