(ns cuad-ci.runner
  (:require [cuad-ci.core :as core]
            [cuad-ci.docker :as docker]))

(deftype service [runbuild preparebuild])

(defn runbuild_ [service build]
  ;; (clojure.pprint/pprint build)
  (loop [newBuild (core/progress service build)]
    ;; (clojure.pprint/pprint newBuild)
    (case (newBuild :core/build-state)
      :buildsucceeded newBuild
      :buildfailed newBuild
      (do (Thread/sleep 1000)
          (recur (core/progress service newBuild))))))

(defn preparebuild_ [service pipeline]
  (let [volume ((.create-volume service))]
    {:core/pipeline pipeline
     :core/build-state :buildready
     :core/completed-steps {}
     :docker/volume-name volume}))

(defn create-service [docker]
  (->service
   (partial runbuild_ docker)
   (partial preparebuild_ docker)))
