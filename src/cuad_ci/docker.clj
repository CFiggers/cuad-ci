(ns cuad-ci.docker
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [unixsocket-http.core :as uhttp]
            ;; [clojure.pprint :as pprint]
            ))

(s/def :docker/container-exit-code (s/or :step-success zero?
                                         :step-failed int?)) ;; Corresponds to Docker/ContainerExitCode

(s/def :docker/container-id string?) ;; Corresponds to Docker/ContainerId

(s/def :docker/container-status (s/or :is-running #{:container-running}
                                      :is-other string?
                                      :is-exited :docker/container-exit-code))

(deftype image [name tag]) ;; Corresponds to Docker/Image

(deftype service [create-container 
                  start-container
                  container-status]) ;; Corresponds to Docker/Service

(defn create-container_ [request {:keys [image cmd]}]
  (let [target "/containers/create"
        body (json/write-str
              {"Image" image
               "Tty" true
               "Labels" {"quad" ""}
               "Cmd" cmd
               "Entrypoint" ["/bin/sh" "-c"]})
        payload {:headers {"content-type" "application/json"}
                 :body body}
        return (request target payload)
        cid ((json/read-str (return :body)) "Id")]
    ;; (pprint/pprint cid)
    cid))

(defn start-container_ [request cid]
  (let [target (str "/containers/" cid "/start")
        return (request target)]
    return))

(defn container-status_ [request cid]
  (let [target (str "/containers/" cid "/json")
        req (request target)
        res (json/read-str (:body req) :key-fn keyword)
        state (:State res)
        status (:Status state)]
    (case status
      "running" [:container-running]
      "exited" [:container-exited (:ExitCode state)]
      [:container-other status])))

(defn create-service []
  (let [manager (uhttp/client "unix:///var/run/docker.sock")
        api-ver "/v1.40"
        post-req-fn (fn ([a] (uhttp/post manager (str api-ver a)))
                   ([a b] (uhttp/post manager (str api-ver a) b)))
        get-req-fn (fn ([a] (uhttp/get manager (str api-ver a)))
                   ([a b] (uhttp/get manager (str api-ver a) b)))]
    (->service
     (partial create-container_ post-req-fn)
     (partial start-container_ post-req-fn)
     (partial container-status_ get-req-fn))))