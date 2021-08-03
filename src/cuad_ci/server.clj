(ns cuad-ci.server
  (:require [unixsocket-http.core :as uhttp]
            [cuad-ci.job-handler :as job-handler]
            [clojure.spec.alpha :as s]))

(s/def :server/port int?)
(s/def :server/config (s/keys :req [:server/port]))

(defn run [config handler]
  "Not much yet")

