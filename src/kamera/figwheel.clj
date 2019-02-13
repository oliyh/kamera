(ns kamera.figwheel
  (:require [figwheel.main.api :as fig-api]
            [figwheel.main :as fig]))

(defn- fig-config [build-id]
  ;; looks like you have to look at the websocket url to know what the port and hostname are going to be, bit rubbish
  (fig/config-for-id "example/dev")
;;  (fig-api/read-build build-id)
  )



;; check the configurations that have devcards: true

;; get the port (and connect hostname) which will serve the devcards UI

;; point chrome there and scrape the urls, i don't think there's a better / static way in clj
;; maybe it's possible to get chrome to execute a script that calls fighwheel in cljs to get a list?
