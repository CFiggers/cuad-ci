(ns cuad-ci.core-test
  (:require [clojure.test :as test]
            [cuad-ci.core :as core]
            [cuad-ci.docker :as docker]
            [cuad-ci.runner :as runner]
            ;; [clojure.spec.alpha :as s]
            ;; [clojure.spec.test.alpha :as spec-test]
            [clojure.java.shell :as shell]))

(defmacro make-step [name image commands]
  {:core/step-name name
   :docker/image image
   :core/commands commands})

(let [service (docker/create-service)]
  (:core/build-state (core/progress service (runner/preparebuild_ service test-pipeline))))

(def test-pipeline
  [(make-step "First step" "ubuntu" ["date" "echo hello"])
   (make-step "Second step" "ubuntu" ["uname -r" "echo hello"])])

(def bad-pipeline
  [(make-step "Should fail" "ubuntu" ["exit 1"])])

(def test-workspace-pipeline
  [(make-step "First step" "ubuntu" ["echo hello > test"])
   (make-step "Second step" "ubuntu" ["cat test"])])

(defn cleanup-docker []
  (shell/sh "bash" "-c" "docker rm -f $(docker ps -aq --filter \"label=quad\") && docker volume rm -f $(docker volume ls -q --filter \"label=quad\")"))

(test/deftest a-test
  (do (cleanup-docker)
      (let [docker (docker/create-service)
            runner (runner/create-service docker)]
        (test/testing "Cuad CI"
          (test/testing "should run a build (success)"
            (let [build ((.preparebuild runner) test-pipeline)
                  res ((.runbuild runner) build)]
              (test/is (= (res :core/build-state) :buildsucceeded))
              (test/is (= true (core/all-steps-success res)))))
          (test/testing "should run a build (failure)"
            (let [build ((.preparebuild runner) bad-pipeline)
                  res ((.runbuild runner) build)]
              (test/is (= (res :core/build-state) :buildfailed))))
          (test/testing "should share workspace between steps"
            (let [build ((.preparebuild runner) test-workspace-pipeline)
                  res ((.runbuild runner) build)]
              (test/is (= (res :core/build-state) :buildsucceeded))
              (test/is (= true (core/all-steps-success res)))))))))