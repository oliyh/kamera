(ns example.core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [example.core :refer [hello-user]]
     [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg hello-user-test
  [hello-user false])
