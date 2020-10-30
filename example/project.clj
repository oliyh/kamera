(defproject example "0.1.0-SNAPSHOT"
  :description "An example project demoing the use of https://github.com/oliyh/kamera"
  :url "https://github.com/oliyh/kamera"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.0"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [reagent "0.8.1"]
                 [devcards "0.2.6"]
                 [kamera "0.1.4-SNAPSHOT"]]

  :source-paths ["src"]
  :resource-paths ["resources"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" example.test-runner]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.9"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]}})
