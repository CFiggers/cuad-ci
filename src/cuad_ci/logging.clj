(ns cuad-ci.logging
  (:require [clojure.spec.alpha :as s]))

(s/def :logging/time int?)
(s/def :logging/log-collection-status (s/or :isready #{:collectionready}
                                            :iscollecting (s/map-of :docker/container-id int?)
                                            :isfinished #{:collectionfinished}))
(s/def :logging/log-collection (s/map-of :core/step-name :core/collection-status))

(s/def :logging/output (s/coll-of string?))
(s/def :logging/log (s/keys :opt [:core/step-name
                                  :logging/output]))

(defn init-log-collection [pipeline]
  (let [steps (map :core/step-name pipeline)]
    (zipmap steps (repeat :collectionready))))

(s/fdef init-log-collection
  :args (s/cat :pipeline :core/pipeline)
  :ret :logging/log-collection)

(def test-log-coll {"First step" :collectionready, "Second step" {"3c77a261455ec445c960b11927891c33d50c3c6b1dc7ea7c464da32b8c9a6088" 0}})

(def test-state {{:core/step-name "First step", :docker/image {:docker/image-name "ubuntu", :docker/image-tag "latest"}, :core/commands ["date" "echo hello"]}
                 "3c77a261455ec445c960b11927891c33d50c3c6b1dc7ea7c464da32b8c9a6088"})

;; => [:buildrunning
;;     {{:core/step-name "First step", :docker/image "ubuntu", :core/commands ["date" "echo hello"]}
;;      "3c77a261455ec445c960b11927891c33d50c3c6b1dc7ea7c464da32b8c9a6088"}]

(s/conform :core/build-state test-state)

(defn change-log [state basetime stepname defaultst]
  (let [status-type (first (s/conform :core/build-state state))]
    (case status-type
      :buildrunning
      (let [running-stepname (:core/step-name (first (keys state)))]
        (if (= running-stepname stepname)
          [stepname {(first (vals state)) basetime}]
          [stepname defaultst]))

      [stepname defaultst])))

(defn each-log [state newtime [stepname log-coll-status]]
  (case log-coll-status
    :collectionfinished [stepname :collectionfinished]
    :collectionready (change-log state 0 stepname :collectionready)
    (change-log state newtime stepname :collectionfinished)))

(defn update-log-collection [state basetime log-coll]
  (into {} (map #(each-log state basetime %) log-coll)))

(s/fdef update-log-collection
  :args (s/and (s/cat :state :core/build-state
                      :basetime :logging/time
                      :log-coll :logging/log-collection))
  :ret :logging/log-collection)

(defn each-run [dservice until [stepname log-coll-status]]
  (case (first (s/conform :logging/log-collection-status log-coll-status))
    (:isready
     :isfinished) {}
    (let [cid (first (keys log-coll-status))
          since (first (vals log-coll-status))
          opts {:docker/container-id cid
                :docker/since since
                :docker/until until}]
      [[:core/step-name stepname] [:logging/output ((.fetch-logs dservice) opts)]])))

(defn run-collection [dservice basetime log-coll]
  (into {} (apply concat (map #(each-run dservice basetime %) log-coll))))

(s/fdef run-collection
  :args (s/and (s/cat :service :docker/service
                      :basetime :docker/time
                      :log-coll :logging/log-collection))
  :ret :logging/log)

(defn collect-logs [dservice log-coll build]
  (let [now (.getTime (java.util.Date.))
        logs (run-collection
              dservice
              now
              log-coll)
        new-log-coll (update-log-collection
                      (:core/build-state build)
                      now
                      log-coll)]
    [new-log-coll logs]))

(s/fdef collect-logs
  :args (s/and (s/cat :service :docker/service
                      :logcoll :logging/log-collection
                      :build :core/build)))
