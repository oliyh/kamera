(ns kamera.app
  (:require [reagent.core :as reagent]))

(def app (js/document.getElementById "app"))

(defn- hello-world []
  [:h1 "kamera report"])

(defn- mount-app []
  (reagent/render [hello-world] app))

(defn- init []
  (mount-app))

(defn on-figwheel-reload []
  (mount-app))

(.addEventListener js/document "DOMContentLoaded" init)
