(ns example.another-core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [example.core :refer [hello-user]]
     [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg another-hello-user-test
  [hello-user "Figwheel"])
