(ns cuad-ci.agent
  (:require [cuad-ci.core :as core]
            [clojure.spec.alpha :as s]))

;; See :core/build-number
(s/def :agent/start-build (s/keys :req [:core/build-number
                                        :core/pipeline]))
(s/def :agent/command (s/or :is-startbuild #{:agent/start-build})) ;; Corresponds to Agent/Cmd

(s/def :agent/log-collected (s/keys :req [:core/build-number
                                          :logging/log]))
(s/def :agent/build-updated (s/keys :req [:core/build-number
                                          :core/build]))
(s/def :agent/message (s/or :is-logcoll #{:agent/log-collected}
                            :is-buildupd #{:agent/build-updated})) ;; Corresponds to Agent/Msg



