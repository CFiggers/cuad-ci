(ns cuad-ci.core-test
  (:require [clojure.test :refer :all]
            [cuad-ci.core :as core]
            [cuad-ci.docker :as docker]
            [clojure.spec.alpha :as s]))

(defmacro make-step [name image commands]
  {:core/step-name name
   :docker/image image
   :core/commands commands})

(def test-pipeline
  [(make-step "First step" "ubuntu" ["date"])
   (make-step "Second step" "ubuntu" ["uname -r"])])

;; (s/valid? :core/pipeline test-pipeline)
;; ;; => true

(def test-build
  {:core/pipeline test-pipeline
   :core/build-state :buildready
   :core/completed-steps {}})

;; (s/valid? :core/build test-build)
;; ;; =? true

;; (runbuild (docker/create-service) test-build)

(defn runbuild [service build]
  ;; (clojure.pprint/pprint build)
  (loop [newBuild (core/progress service build)]
    ;; (clojure.pprint/pprint newBuild)
    (case (newBuild :core/build-state)
      :buildsucceeded newBuild
      :buildfailed newBuild
      (do (Thread/sleep 1000)
          (recur (core/progress service newBuild))))))

;; (runbuild (docker/create-service) test-build)

(deftest a-test
  (let [docker (docker/create-service)]
    (testing "Cuad CI"
      (testing "should run a build (success)"
        (let [res (runbuild docker test-build)]
          (is (= (res :core/build-state) :buildsucceeded))
          (is (= true (core/all-steps-success res))))))))