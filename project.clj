(defproject kamera "0.1.3-SNAPSHOT"
  :description "UI testing via image comparison and devcards"
  :url "https://github.com/oliyh/kamera"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[me.raynes/conch "0.8.0"]
                 [clj-chrome-devtools "20200423"]
                 [hickory "0.7.1"]]
  :monkeypatch-clojure-test false
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.0"]
                                       [org.clojure/clojurescript "1.10.339"]
                                       [org.clojure/tools.reader "1.3.2"]]}
             :dev {:dependencies [;; required for report app & example project
                                  [com.bhauman/figwheel-main "0.1.9"]
                                  [reagent "0.8.1"]
                                  [devcards "0.2.6"]
                                  [binaryage/devtools "0.9.10"]]
                   :source-paths ["src/cljs"]
                   :test-paths ["test/cljs"]
                   :resource-paths ["resources" "target" "test-resources" "example/resources"]
                   :clean-targets ^{:protect false} ["target"
                                                     "resources/public/css/kamera.css"
                                                     "resources/public/kamera.js"]
                   :plugins [[lein-sass "0.4.0"]]}
             :repl {:prep-tasks ^:replace []}
             :build {:prep-tasks ^:replace []}}

  :sass {:src "resources/sass"
         :output-directory "resources/public/css"}

  :prep-tasks [["with-profile" "+build,+dev" "run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"] ;; fig:min
               ["sass" "once"]]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" kamera.test-runner]})
