(ns example.another-core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [example.core :refer [hello-world]]
     [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg another-hello-world-test
  [hello-world])
