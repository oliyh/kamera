(defproject kamera "0.1.1-SNAPSHOT"
  :description "UI testing via image comparison and devcards"
  :url "https://github.com/oliyh/kamera"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :dependencies [[me.raynes/conch "0.8.0"]
                 [doo-chrome-devprotocol "0.1.0"]
                 [hickory "0.7.1"]]
  :monkeypatch-clojure-test false
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.0"]
                                       [org.clojure/clojurescript "1.10.339"]
                                       [org.clojure/tools.reader "1.3.2"]
                                       [com.bhauman/figwheel-main "0.1.9"]]}
             :dev {:dependencies [;; required for report app & example project
                                  [reagent "0.8.1"]
                                  [devcards "0.2.6"]
                                  [binaryage/devtools "0.8.3"]]
                   :source-paths ["src/cljs"]
                   :test-paths ["test/cljs"]
                   :resource-paths ["resources" "target" "example/resources"]
                   :clean-targets ^{:protect false} ["target"
                                                     "resources/public/css"
                                                     "resources/public/kamera.js"]
                   :plugins [[lein-sass "0.4.0"]]
                   :sass {:src "resources/sass"
                          :output-directory "resources/public/css"}}}

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" kamera.test-runner]
            "build-ui"  ["do" ["sass" "once"] ["fig:min"]]
            "test"      ["do" ["build-ui"] ["test"]]
            "jar"       ["do" ["build-ui"] ["jar"]]})
