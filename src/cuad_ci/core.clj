(ns cuad-ci.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            ;; [clojure.spec.test.alpha :as spec-test]
            [yaml.core :as yaml]
            [cuad-ci.docker :as docker]
            [clojure.set :as set]))

(s/def :core/step-name string?) ;; Corresponds to Core/StepName
(s/def :core/commands (s/coll-of string?)) ;; Corresponds to Core/Commands
;; See :docker/image
(s/def :core/step (s/keys :req [:core/step-name 
                                :core/commands
                                :docker/image])) ;; Corresponds to Core/Step

(s/def :core/build-number int?) ;; Corresponds to Core/BuildNumber

(s/def :core/pipeline (s/and not-empty
                             (s/coll-of #(s/valid? :core/step %)))) ;; Corresponds to Core/Pipeline
(s/def :core/build-result #{:buildsucceeded
                            :buildfailed
                            :buildunexpected})
(s/def :core/build-state (s/or :buildready #{:buildready}
                               :buildrunning (s/map-of :core/step :docker/container-id)
                               :result :core/build-result)) ;; Corresponds to Core/BuildResult
(s/def :core/step-result (s/or :step-succeeded #{:step-success}
                               :step-failed :docker/container-exit-code)) ;; Corresponds to Core/BuildState
(s/def :core/completed-steps (s/map-of :core/step-name :core/step-result))
;; See :docker/volume-name
(s/def :core/build (s/keys :req [:core/pipeline 
                                 :core/build-state
                                 :core/completed-steps
                                 :docker/volume-name])) ;; Corresponds to Core/Build

(defn all-steps-run [build]
  (every?
   (set (keys (build :core/completed-steps)))
   (map :core/step-name (build :core/pipeline))))

(s/fdef all-steps-run
        :args (s/cat :build :core/build)
        :ret boolean?)

(defn all-steps-success [build]
  (every?
   #(= % 0)
   (vals (build :core/completed-steps))))

(s/fdef all-steps-success
        :args (s/cat :build :core/build)
        :ret boolean?)

(defn nextstep [build]
  (first (filter
          #(not ((set (keys (build :core/completed-steps))) (% :core/step-name)))
          (build :core/pipeline))))

(s/fdef nextstep
        :args (s/cat :build :core/build)
        :ret :core/step)

(defn readpipeline [path]
  (let [yaml (yaml/from-file path)
        yaml-pipe (:steps yaml)
        rekeyed (mapv #(set/rename-keys % {:name :core/step-name
                                           :image :docker/image
                                           :commands :core/commands}) yaml-pipe)
        upfun (fn [x]
                (let [imgvec (string/split x #":")]
                  (zipmap [:docker/image-name :docker/image-tag] (conj imgvec "latest"))))]
    (mapv #(update % :docker/image upfun) rekeyed)))

(defn buildready [service build]
  (cond
    (not (all-steps-success build)) ;; Any step failed
    (assoc build :core/build-state :buildfailed)

    (not (all-steps-run build)) ;; Any steps need run
    (let [stepnext (nextstep build)
          image (str (:docker/image-name (:docker/image stepnext))
                     ":"
                     (:docker/image-tag (:docker/image stepnext)))
          cmd (:core/commands stepnext)
          vol (:docker/volume-name build)
          opts {:image image :cmd cmd :vol vol}
          pull-img ((.pull-image service) (:docker/image stepnext))
          cid ((.create-container service) opts)
          res ((.start-container service) cid)]
      ;; (clojure.pprint/pprint res)
      (assoc build :core/build-state {stepnext cid}))

    :else (assoc build :core/build-state :buildsucceeded)))

(s/fdef buildready
        :args (s/and (s/cat :service :docker/service 
                            :build :core/build))
        :ret :core/build)

(defn container-exited [build code]
  (let [completed-steps (:core/completed-steps build)
        thisstep (first (keys (build :core/build-state)))
        thisstep-name (thisstep :core/step-name)]
    (assoc build
           :core/build-state :buildready
           :core/completed-steps
           (assoc completed-steps
                  thisstep-name code))))

(s/fdef container-exited
        :args (s/and (s/cat :build :core/build
                            :code int?))
        :ret :core/build)

(defn buildrunning [service build]
  (let [cid (first (vals (:core/build-state build)))
        [status code] ((.container-status service) cid)]
    (case status
      :container-running build
      :container-exited (container-exited build code)
      :container-other)))

(s/fdef buildrunning
        :args (s/and (s/cat :service :docker/service 
                            :build :core/build))
        :ret :core/build)

(defn progress [service build]
  (let [conf-build (s/conform :core/build build)
        state (:core/build-state conf-build)]
    (case (first state)
      :buildready (buildready service build)
      :buildrunning (buildrunning service build)
      (:buildsucceeded
       :buildfailed
       :buildunexpected) build)))

(s/fdef progress
        :args (s/and (s/cat :service :docker/service 
                            :build :core/build))
        :ret :core/build)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
