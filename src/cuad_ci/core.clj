(ns cuad-ci.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [cuad-ci.docker :as docker]))

;; Corresponds to Core/StepName
(s/def :core/step-name string?)
;; Corresponds to Core/Commands
(s/def :core/commands (s/coll-of string?))

;; Corresponds to Core/Step
(s/def :core/step (s/keys :req [:core/step-name :core/commands
                                :docker/image]))

;; Corresponds to Core/Pipeline
(s/def :core/pipeline (s/and not-empty
                             (s/coll-of #(s/valid? :core/step %))))

(s/def :core/step-result (s/or :step-succeeded #{:step-success}
                               :step-failed :docker/container-exit-code))

;; Corresponds to Core/BuildResult
(s/def :core/build-result #{:buildsucceeded :buildfailed})

;; Corresponds to Core/BuildState
(s/def :core/build-state (s/or :buildready #{:buildready}
                               :buildrunning (s/map-of :core/step :docker/container-id)
                               :result :core/build-result))

(s/def :core/completed-steps (s/map-of :core/step-name :core/step-result))

;; Corresponds to Core/Build
(s/def :core/build (s/keys :req [:core/pipeline :core/build-state
                                 :core/completed-steps]))

(def test-build
  {:core/pipeline [{:core/step-name "Step 1"
                    :core/commands ["echo hello" "echo there"]
                    :docker/image "ubuntu"}
                   {:core/step-name "Step 2"
                    :core/commands ["this" "that"]
                    :docker/image "ubuntu"}]
   :core/build-state :buildready
   :core/completed-steps {}})

(def test-build-c
  (s/conform :core/build test-build))

(def test-build-running
  {:core/pipeline [{:core/step-name "Step 1"
                    :core/commands ["this" "that"]
                    :docker/image "ubuntu"}
                   {:core/step-name "Step 2"
                    :core/commands ["this" "that"]
                    :docker/image "ubuntu"}]
   :core/build-state {{:core/step-name "Step 1"
                       :core/commands ["this" "that"]
                       :docker/image "ubuntu"}
                      "test container id"}
   :core/completed-steps {"Step 1" :step-success}})

(def test-build-somecomplete
  {:core/pipeline [{:core/step-name "Step 1"
                    :core/commands ["this" "that"]
                    :docker/image "ubuntu"}
                   {:core/step-name "Step 2"
                    :core/commands ["this" "that"]
                    :docker/image "ubuntu"}]
   :core/build-state {{:core/step-name "Step 2"
                       :core/commands ["this" "that"]
                       :docker/image "ubuntu"}
                      "test container id"}
   :core/completed-steps {"Step 1" :step-success}})

(def test-build-allcomplete
  {:core/pipeline [{:core/step-name "Step 1"
                    :core/commands ["echo hello" "echo there"]
                    :docker/image "ubuntu"}
                   {:core/step-name "Step 2"
                    :core/commands ["this" "that"]
                    :docker/image "ubuntu"}]
   :core/build-state :buildsucceeded
   :core/completed-steps {"Step 1" :step-success
                          "Step 2" :step-success}})

(defn all-steps-run [build]
  (every?
   (set (keys (build :core/completed-steps)))
   (map :core/step-name (build :core/pipeline))))

(defn all-steps-success [build]
  (every?
   #(= % :step-success)
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
        thisstep-name (thisstep :core/step-name)
        step-status :step-success] ;; TODO -- calc from code
    (assoc build
           :core/build-state :buildready
           :core/completed-steps
           (assoc completed-steps
                  thisstep-name step-status))))

(defn buildrunning [service build]
  (let [cid (first (vals (:core/build-state build)))
        [status code] ((.container-status service) cid)]
    (case status
      :container-running build
      :container-exited (container-exited build code)
      :container-other)))

;; docker/service -> core/build -> result
(defn progress [service build]
  (let [conf-build (s/conform :core/build build)
        state (:core/build-state conf-build)]
    (case (first state)
      :buildready (buildready service build)
      :buildrunning (buildrunning service build)
      (:buildsucceeded
       :buildfailed) build)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


;; (defn test-case [a]
;;   (case a
;;    :this "it's this"
;;    :that "it's that"
;;    (let [{:keys [key]} a]
;;      (str "it's " key))))

;; (test-case {:key "the other thing"})
