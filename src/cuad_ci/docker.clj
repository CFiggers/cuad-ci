(ns cuad-ci.docker
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [unixsocket-http.core :as uhttp]))

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
    ;; (clojure.pprint/pprint cid)
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

(defn create-service []
  (->service
   (partial create-container_)
   (partial start-container_)))

(defn container-status [build]
  [:container-exited 0])

;;(progress (create-service) test-build)

;; (let [service (create-service)
;;       cid ((.create_container service) {:image "ubuntu" :cmd "echo test"})
;;       res ((.start_container service) cid)]
;;   res)