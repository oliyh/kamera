(ns kamera.app-test
  (:require [kamera.app :as app]
            [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg kamera-report-test
  [app/kamera-report])
