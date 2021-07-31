(ns cuad-ci.docker
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [unixsocket-http.core :as uhttp]
            [clojure.string :as string]
            ;; [clojure.pprint :as pprint]
            ))

(s/def :docker/container-exit-code (s/or :step-success zero?
                                         :step-failed int?)) ;; Corresponds to Docker/ContainerExitCode

(s/def :docker/container-status (s/or :is-running #{:container-running}
                                      :is-other string?
                                      :is-exited :docker/container-exit-code)) ;; Corresponds to Docker/ContainerId

(s/def :docker/image-name string?)
(s/def :docker/image-tag string?)
(s/def :docker/image (s/keys :req [:docker/image-name]
                             :opt [:docker/image-tag]))
(s/def :docker/cmd string?)
(s/def :docker/vol string?)
(s/def :docker/create-container-options (s/keys :req [:docker/image
                                                      :docker/cmd
                                                      :docker/vol]))

(s/def :docker/container-id string?) ;; Corresponds to Docker/ContainerId
(s/def :docker/since int?)
(s/def :docker/until int?)
(s/def :docker/fetch-logs-options (s/keys :req [:docker/container-id
                                                :docker/since
                                                :docker/until]))

(s/def :docker/volume-name string?) ;; Corresponds to Docker/Volume

(deftype image [name 
                tag]) ;; Corresponds to Docker/Image

(s/def :docker/service #(not (nil? %)))

(deftype service [create-container
                  start-container
                  container-status
                  create-volume
                  fetch-logs
                  pull-image]) ;; Corresponds to Docker/Service

(defn create-container_ [request {:keys [image cmd vol]}]
  (let [target "/containers/create"
        body (json/write-str
              {"Image" image
               "Tty" true
               "Labels" {"quad" ""}
               "Cmd" (string/join "\n" (cons "set -ex" cmd))
               "Entrypoint" ["/bin/sh" "-c"]
               "WorkingDir" "/app"
               "HostConfig" {"Binds" [(str vol ":/app")]}})
        payload {:headers {"content-type" "application/json"}
                 :body body}
        return (request target payload)
        cid ((json/read-str (:body return) :key-fn keyword) :Id)]
    ;; (pprint/pprint cid)
    cid))

(defn start-container_ [request cid]
  (let [target (str "/containers/" cid "/start")
        return (request target)]
    return))

(defn container-status_ [request cid]
  (let [target (str "/containers/" cid "/json")
        return (request target)
        res (json/read-str (:body return) :key-fn keyword)
        state (:State res)
        status (:Status state)]
    (case status
      "running" [:container-running]
      "exited" [:container-exited (:ExitCode state)]
      [:container-other status])))

(defn create-volume_ [request]
  (let [target "/volumes/create"
        body (json/write-str
              {"Labels" {"quad", ""}})
        payload {:headers {"content-type" "application/json"}
                 :body body}
        return (request target payload)
        volume-name ((json/read-str (:body return) :key-fn keyword) :Name)]
    volume-name))

(defn fetch-logs_ [request opts]
  (let [target "/containers/"
        cid (:docker/container-id opts)
        since (:docker/since opts)
        until (:docker/until opts)
        url (str target
                 cid
                 "/logs?stdout=true&stderr=true&since="
                 since
                 "&until="
                 until)
        return (request url)
        logs (filter #(not (= \+ (first %))) (clojure.string/split-lines (:body return)))]
    logs))

;; (fetch-logs_ #(uhttp/get (uhttp/client "unix:///var/run/docker.sock") (str "/v1.40" %)) 
;;              {:docker/container-id "3c77a261455ec445c960b11927891c33d50c3c6b1dc7ea7c464da32b8c9a6088"
;;               :docker/since 0
;;               :docker/until 1627751417382})

(defn pull-image_ [request {image-tag :docker/image-tag 
                            image-name :docker/image-name}]
  (let [target "/images/create"
        url (str target
                 "?tag="
                 image-tag
                 "&fromImage="
                 image-name)
        return (request url)
        response (:body return)]
    response))

(s/fdef pull-image_ 
        :args (s/and (s/cat :request :docker/service
                            :image :docker/image)))

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
     (partial container-status_ get-req-fn)
     (partial create-volume_ post-req-fn)
     (partial fetch-logs_ get-req-fn)
     (partial pull-image_ post-req-fn))))