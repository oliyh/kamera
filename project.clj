(defproject kamera "0.1.0-SNAPSHOT"
  :description "UI testing via image comparison and devcards"
  :url "https://github.com/oliyh/kamera"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.reader "1.3.2"]
                 [me.raynes/conch "0.8.0"]
                 [doo-chrome-devprotocol "0.1.0"]
                 [devcards "0.2.6"]
                 [com.bhauman/figwheel-main "0.1.9"]
                 [hickory "0.7.1"]]
  :resource-paths ["example/resources"]
  :monkeypatch-clojure-test false
  :profiles {:dev {:dependencies [;; required for example project
                                  [reagent "0.8.1"]
                                  [devcards "0.2.6"]]}})
