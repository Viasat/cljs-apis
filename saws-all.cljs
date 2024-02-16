#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns saws
  (:require [viasat.util :refer [parse-opts Eprintln Epprint]]
            [viasat.apis.saws :refer [usage-general usage-options
                                      service-aliases arg->keyword run]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [argv *command-line-args*
      cfg (parse-opts (str usage-general usage-options) argv)
      {:keys [service command]} cfg
      [service command] (map arg->keyword [service command])
      service (get service-aliases (name service) service)
      _ (when (empty? cfg) (js/process.exit 2))]
  (run cfg service command {}))
