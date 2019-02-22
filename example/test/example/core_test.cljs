(ns example.core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [example.core :refer [multiply hello-world]]
     [devcards.core :refer-macros [defcard-rg]]))

(deftest multiply-test
  (is (= (* 1 2) (multiply 1 2))))

(deftest multiply-test-2
  (is (= (* 75 10) (multiply 10 75))))

(defcard-rg hello-world-test
  [hello-world])
