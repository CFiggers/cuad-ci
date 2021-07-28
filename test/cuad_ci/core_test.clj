(ns cuad-ci.core-test
  (:require [clojure.test :as test]
            [cuad-ci.core :as core]
            [cuad-ci.docker :as docker]
            [cuad-ci.runner :as runner]
            [clojure.spec.alpha :as s]
            [clojure.java.shell :as shell]))

(defmacro make-step [name image commands]
  {:core/step-name name
   :docker/image image
   :core/commands commands})

;; (s/valid? :core/pipeline test-pipeline)
;; ;; => true

;; (s/valid? :core/build test-build)
;; ;; =? true

;; (runbuild (docker/create-service) test-build)

(def test-pipeline
  [(make-step "First step" "ubuntu" ["date"])
   (make-step "Second step" "ubuntu" ["uname -r"])])

;; (def test-build
;;   {:core/pipeline test-pipeline
;;    :core/build-state :buildready
;;    :core/completed-steps {}})

;; (def test-service
;;   (docker/create-service))

(defn cleanup-docker []
  (shell/sh "bash" "-c" "docker rm -f $(docker ps -aq --filter \"label=quad\")"))

(test/deftest a-test
  (do (cleanup-docker)
    (let [docker (docker/create-service)
          runner (runner/create-service docker)]
      (test/testing "Cuad CI"
        (test/testing "should run a build (success)"
          (let [build ((.preparebuild runner) test-pipeline)
                res ((.runbuild runner) build)]
            (test/is (= (res :core/build-state) :buildsucceeded))
            (test/is (= true (core/all-steps-success res)))))))))
