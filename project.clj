(defproject kamera "0.1.5-SNAPSHOT"
  :description "UI testing via image comparison and devcards"
  :url "https://github.com/oliyh/kamera"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[me.raynes/conch "0.8.0"]
                 [clj-chrome-devtools "20200423"]
                 [hickory "0.7.1"]
                 [org.clojure/tools.logging "1.1.0"]

                 ;; clj-chrome-devtools, ring and figwheel-main all fight over these
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [org.eclipse.jetty/jetty-servlet "9.4.28.v20200408"]
                 [org.eclipse.jetty/jetty-client "9.4.28.v20200408"]
                 [org.eclipse.jetty/jetty-http "9.4.28.v20200408"]
                 [org.eclipse.jetty/jetty-io "9.4.28.v20200408"]
                 [org.eclipse.jetty/jetty-util "9.4.28.v20200408"]
                 [org.eclipse.jetty/jetty-xml "9.4.28.v20200408"]
                 [org.eclipse.jetty.websocket/websocket-api "9.4.28.v20200408"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.28.v20200408"]
                 [org.eclipse.jetty.websocket/websocket-common "9.4.28.v20200408"]]
  :monkeypatch-clojure-test false
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [org.clojure/clojurescript "1.10.339"]
                                       [org.clojure/tools.reader "1.3.2"]]}
             :dev {:dependencies [;; required for report app & example project
                                  [com.bhauman/figwheel-main "0.2.9"]
                                  [reagent "0.8.1"]
                                  [devcards "0.2.7"]
                                  [binaryage/devtools "0.9.10"]
                                  [ring "1.8.1"]
                                  [ring/ring-jetty-adapter "1.8.1"]

                                  ;; clj-chrome-devtools, ring and figwheel-main all fight over these
                                  [org.eclipse.jetty/jetty-server "9.4.28.v20200408"]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.28.v20200408"]
                                  [org.eclipse.jetty.websocket/websocket-server "9.4.28.v20200408"]]
                   :source-paths ["src/cljs"]
                   :test-paths ["test/cljs"]
                   :resource-paths ["resources" "target" "test-resources" "example/resources"]
                   :clean-targets ^{:protect false} ["target"
                                                     "resources/public/css/kamera.css"
                                                     "resources/public/kamera.js"]
                   :plugins [[lein-sass "0.4.0"]]}
             :repl {:prep-tasks ^:replace []}
             :build {:prep-tasks ^:replace []}
             :example-test {:prep-tasks ^:replace []
                            :source-paths ["test-resources"]}}

  :sass {:src "resources/sass"
         :output-directory "resources/public/css"}

  :prep-tasks [["with-profile" "+build,+dev" "run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"] ;; fig:min
               ["sass" "once"]]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" kamera.test-runner]})
