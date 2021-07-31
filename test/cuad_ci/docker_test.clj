(ns cuad-ci.docker-test
  (:require [clojure.test :as test]
            [cuad-ci.docker :as docker]
            [clojure.spec.alpha :as s]))

(test/deftest create-container_ []
  (prn "Testing create-container_")
  (let [docker (docker/create-service)]
    (test/testing "docker/create-container_"
      (test/testing "should return a container id"
        (prn "- Should return a container id")
        (let [vid ((.create-volume docker))
              opts {:image "ubuntu" :cmd "echo hello" :vol vid}]
          (test/is (s/valid? :docker/container-id
                             ((.create-container docker) opts))))))))

(test/deftest start-container_ []
  (prn "Testing start-container_")
  (let [docker (docker/create-service)]
    (test/testing "docker/start-container_"
      (test/testing "should return a 204 status code"
        (prn "- should return a 204 status code")
        (let [vid ((.create-volume docker))
              opts {:image "ubuntu" :cmd "echo hello" :vol vid}
              cid ((.create-container docker) opts)]
          (test/is (= 204 (:status ((.start-container docker) cid)))))))))
