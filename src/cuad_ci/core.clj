(ns cuad-ci.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [cuad-ci.docker :as docker]))


(s/def :core/step-name string?) ;; Corresponds to Core/StepName

(s/def :core/commands (s/coll-of string?)) ;; Corresponds to Core/Commands

(s/def :core/step (s/keys :req [:core/step-name :core/commands
                                :docker/image])) ;; Corresponds to Core/Step

(s/def :core/pipeline (s/and not-empty
                             (s/coll-of #(s/valid? :core/step %)))) ;; Corresponds to Core/Pipeline

(s/def :core/step-result (s/or :step-succeeded #{:step-success}
                               :step-failed :docker/container-exit-code))

(s/def :core/build-result #{:buildsucceeded 
                            :buildfailed 
                            :buildunexpected}) ;; Corresponds to Core/BuildResult

(s/def :core/build-state (s/or :buildready #{:buildready}
                               :buildrunning (s/map-of :core/step :docker/container-id)
                               :result :core/build-result)) ;; Corresponds to Core/BuildState

(s/def :core/completed-steps (s/map-of :core/step-name :core/step-result))

(s/def :core/build (s/keys :req [:core/pipeline :core/build-state
                                 :core/completed-steps])) ;; Corresponds to Core/Build

(defn all-steps-run [build]
  (every?
   (set (keys (build :core/completed-steps)))
   (map :core/step-name (build :core/pipeline))))

(defn all-steps-success [build]
  (every?
   #(= % 0)
   (vals (build :core/completed-steps))))

(defn nextstep [build]
  (first (filter
          #(not ((set (keys (build :core/completed-steps))) (% :core/step-name)))
          (build :core/pipeline))))

(defn buildready [service build]
  (cond
    (not (all-steps-success build)) ;; Any step failed
    (assoc build :core/build-state :buildfailed)

    (not (all-steps-run build)) ;; Any steps need run
    (let [stepnext (nextstep build)
          image (stepnext :docker/image)
          cmd (stepnext :core/commands)
          opts {:image image :cmd cmd}
          cid ((.create-container service) opts)
          res ((.start-container service) cid)]
      ;; (clojure.pprint/pprint res)
      (assoc build :core/build-state {stepnext cid}))

    :else (assoc build :core/build-state :buildsucceeded)))

(defn container-exited [build code]
  (let [completed-steps (:core/completed-steps build)
        thisstep (first (keys (build :core/build-state)))
        thisstep-name (thisstep :core/step-name)]
    (assoc build
           :core/build-state :buildready
           :core/completed-steps
           (assoc completed-steps
                  thisstep-name code))))

(defn buildrunning [service build]
  (let [cid (first (vals (:core/build-state build)))
        [status code] ((.container-status service) cid)]
    (case status
      :container-running build
      :container-exited (container-exited build code)
      :container-other)))

(defn progress [service build]
  (let [conf-build (s/conform :core/build build)
        state (:core/build-state conf-build)]
    (case (first state)
      :buildready (buildready service build)
      :buildrunning (buildrunning service build)
      (:buildsucceeded
       :buildfailed
       :buildunexpected) build)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
