(ns cuad-ci.core-test
  (:require [clojure.test :as test]
            [cuad-ci.core :as core]
            [cuad-ci.docker :as docker]
            [cuad-ci.runner :as runner]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            ;; [clojure.spec.test.alpha :as spec-test]
            [clojure.java.shell :as shell]
            [clojure.string :as string]))

(defmacro make-step [name image commands]
  {:core/step-name name
   :docker/image {:docker/image-name image :docker/image-tag "latest"}
   :core/commands commands})

(def test-pipeline
  [(make-step "First step" "ubuntu" ["date" "echo hello"])
   (make-step "Second step" "ubuntu" ["uname -r" "echo hello"])])

(def bad-pipeline
  [(make-step "Should fail" "ubuntu" ["exit 1"])])

(def test-workspace-pipeline
  [(make-step "First step" "ubuntu" ["echo hello > test"])
   (make-step "Second step" "ubuntu" ["cat test"])])

(def test-log-pipeline
  [(make-step "Long step" "ubuntu" ["echo hello" "sleep 2" "echo world" "sleep 2"])
   (make-step "Echo Linux" "ubuntu" ["uname -s"])])

(def test-pull-pipeline
  [(make-step "First step" "busybox" ["date"])])

(defn cleanup-docker []
  (shell/sh "bash" "-c" "docker rm -f $(docker ps -aq --filter \"label=quad\") && docker volume rm -f $(docker volume ls -q --filter \"label=quad\")"))

(test/deftest progress []
  (prn "Test run: progress")
  (let [docker (docker/create-service)
        ;; empty-hooks (runner/empty-hooks)
        runner (runner/create-service docker)]
    (test/testing "core/progress"
      (test/testing "should return a valid build"
        (prn "- Cuad CI should return a valid build")
        (let [build ((.preparebuild runner) test-pipeline)]
          (test/is (s/valid? :core/build (core/progress docker build))))))))

(test/deftest basic-tests
  (prn "Test run: basic-tests")
  (do (cleanup-docker)
      (let [docker (docker/create-service)
            runner (runner/create-service docker)
            empty-hooks (runner/empty-hooks)]
        (test/testing "Cuad CI"
          (test/testing "should run a build (success)"
            (prn "- Cuad CI should run a build (success)")
            (let [build ((.preparebuild runner) test-pipeline)
                  res ((.runbuild runner) empty-hooks build)]
              (test/is (= (res :core/build-state) :buildsucceeded))
              (test/is (= true (core/all-steps-success res))))))

        (test/testing "should run a build (failure)"
          (prn "- Cuad CI should run a build (failure)")
          (let [build ((.preparebuild runner) bad-pipeline)
                res ((.runbuild runner) empty-hooks build)]
            (test/is (= (res :core/build-state) :buildfailed))))

        (test/testing "should share workspace between steps"
          (prn "- Cuad CI should share workspace between steps")
          (let [build ((.preparebuild runner) test-workspace-pipeline)
                res ((.runbuild runner) empty-hooks build)]
            (test/is (= (res :core/build-state) :buildsucceeded))
            (test/is (= true (core/all-steps-success res)))))

        (test/testing "should collect logs"
          (prn "- Cuad CI should collect logs")
          (let [mem (atom #{"hello" "world" "Linux"})
                build ((.preparebuild runner) test-log-pipeline)
                onlog (fn [log] ;; {"Long step" ("hello" "world")}
                        (let [remaining @mem
                              logged (set (mapcat #(string/split % #" ") (:logging/output log)))
                              found-words (set/intersection remaining logged)]
                          (swap! mem set/difference found-words)))
                test-hooks (runner/->hooks (partial onlog))
                res ((.runbuild runner) test-hooks build)]
            (test/is (= (res :core/build-state) :buildsucceeded))
            (test/is (= true (core/all-steps-success res)))
            (test/is (= #{} @mem))))

        (test/testing "should pull images"
          (prn "- Cuad CI should pull images")
          (shell/sh "bash" "-c" "docker rmi -f busybox")
          (let [build ((.preparebuild runner) test-pull-pipeline)
                res ((.runbuild runner) empty-hooks build)]
            (test/is (= (res :core/build-state) :buildsucceeded))
            (test/is (= true (core/all-steps-success res))))))))

(test/deftest basic-tests-2
  (prn "Test run: basic-tests")
  (let [docker (docker/create-service)
        runner (runner/create-service docker)
        empty-hooks (runner/empty-hooks)]
    (test/testing "should decode pipelines"
      (prn "- Cuad CI should decode yaml pipelines")
      (let [pipeline (core/readpipeline "test/cuad_ci/pipeline.sample.yml")
            build ((.preparebuild runner) pipeline)
            res ((.runbuild runner) empty-hooks build)]
        (test/is (= (res :core/build-state) :buildsucceeded))
        (test/is (= true (core/all-steps-success res)))))))
