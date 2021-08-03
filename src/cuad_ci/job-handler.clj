(ns cuad-ci.job-handler
  (:require [clojure.spec.alpha :as s]
            [cuad-ci.agent :as agent]))

;; See :core/pipeline
(s/def :job-handler/job-state (s/or :isqueued #{:jobqueued}
                                    :isassigned #{:jobassigned}
                                    :isscheduled :core/build))
(s/def :job-handler/job (s/keys :req [:core/pipeline
                                      :job-handler/job-state]))

(deftype service [queue-job
                  dispatch-cmd
                  process-msg])

(defn queue-job_ [x]
  "Not much yet")

(defn dispatch-cmd_ [x]
  "Not much yet")

(defn process-msg_ [x]
  "Not much yet")

(defn create-service []
  (->service 
   (partial queue-job_)
   (partial dispatch-cmd_)
   (partial process-msg_)))