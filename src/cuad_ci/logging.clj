(ns cuad-ci.logging
  (:require [clojure.spec.alpha :as s]))

(s/def :logging/log-collection-status (s/or :isready #{:collectionready}
                                     :iscollecting (s/map-of :docker/container-id int?)
                                     :isfinished #{:collectionfinished}))
(s/def :logging/log-collection (s/map-of :core/step-name :core/collection-status))

(deftype log [output 
              step])

(defn init-log-collection [pipeline]
  (let [steps (map :core/step-name pipeline)]
    (zipmap steps (repeat :collectionready))))

(s/fdef init-log-collection
        :args (s/cat :pipeline :core/pipeline)
        :ret :logging/log-collection)

(defn collection-ready []
  "nothing yet")

(defn collecting-logs []
  "nothing yet")

(defn collection-finished []
  "nothing yet")

(defn update-log-collection [state log-coll]
  "nothing yet")

(s/fdef update-log-collection
        :args (s/and (s/cat :state :core/build-state
                            :log-coll :logging/log-collection))
        :ret :core/log-collection)

(defn collect-logs [service logcoll build]
  "nothing yet")

(s/fdef collect-logs
        :args (s/and (s/cat :service :docker/service
                            :logcoll :logging/log-collection
                            :build :core/build)))
