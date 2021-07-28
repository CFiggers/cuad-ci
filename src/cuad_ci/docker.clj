(ns cuad-ci.docker
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [unixsocket-http.core :as uhttp]
            [clojure.pprint :as pprint]))

;; Corresponds to Docker/ContainerExitCode
(s/def :docker/container-exit-code (s/or :step-succeeded zero?
                                         :step-failed int?))
;; Corresponds to Docker/ContainerId
(s/def :docker/container-id string?)

;; Corresponds to Docker/Image
(deftype image [name tag])

;; Corresponds to Docker/Service
(deftype service [create-container start-container])

(defn create-container_ [{:keys [image cmd]}]
  (let [manager (uhttp/client "unix:///var/run/docker.sock")
        body (json/write-str
              {"Image" image
               "Tty" true
               "Labels" {"quad" ""}
               "Cmd" cmd
               "Entrypoint" ["/bin/sh" "-c"]})
        return (uhttp/post manager "/v1.40/containers/create"
                           {:headers {"content-type" "application/json"}
                            :body body})
        cid ((json/read-str (return :body)) "Id")]
    ;; (pprint/pprint cid)
    cid))

;; (create-container_ {:image "ubuntu" :cmd "echo hello"})
;; ;; => Succeeds

(defn start-container_ [cid]
  (let [manager (uhttp/client "unix:///var/run/docker.sock")
        target (str "/v1.40/containers/" cid "/start")
        return (uhttp/post manager target)]
    return))

;; (start-container_ (create-container_ {:image "ubuntu" :cmd "echo hello"}))
;; ;; => Succeeds

;; TODO -- does create-interface make more sense here?
(defn create-service []
  (->service
   (partial create-container_)
   (partial start-container_)))

;; TODO -- implement this. Should return Status and Code
(defn container-status [build]
  [:container-exited 0])