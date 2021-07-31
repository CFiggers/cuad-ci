(ns cuad-ci.runner
  (:require [cuad-ci.core :as core]
            [cuad-ci.logging :as logging]
            [cuad-ci.docker :as docker]))

(deftype service [runbuild preparebuild])

(deftype hooks [logCollected])

(defn runbuild_ [service hooks build]
  (loop [newBuild (core/progress service build)
         [new-coll logs] (logging/collect-logs service 
                                               (logging/init-log-collection (:core/pipeline build)) 
                                               build)]
    (do ((.logCollected hooks) logs)
        (case (newBuild :core/build-state)
          :buildsucceeded newBuild
          :buildfailed newBuild
          (do (Thread/sleep 1000)
              (recur (core/progress service newBuild)
                     (logging/collect-logs service new-coll newBuild)))))))

(defn preparebuild_ [service pipeline]
  (let [volume ((.create-volume service))]
    {:core/pipeline pipeline
     :core/build-state :buildready
     :core/completed-steps {}
     :docker/volume-name volume}))

(defn log-collected_ [x]
  "not much yet")

(defn empty-hooks []
  (->hooks
   (partial log-collected_)))

(defn create-service [docker]
  (->service
   (partial runbuild_ docker)
   (partial preparebuild_ docker)))
