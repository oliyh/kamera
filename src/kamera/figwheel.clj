(ns kamera.figwheel
  (:require [kamera.core :as k]
            [figwheel.main.api :as fig-api]
            [figwheel.main :as fig]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as string]
            [doo-chrome-devprotocol.core :as dcd]
            [clojure.test :refer [deftest]])
  (:import [io.webfolder.cdp.session Session]))

(defn start-devcards [build-id]
  (fig-api/start {:mode :serve
                  :open-url false
                  :connect-url "http://[[config-hostname]]:[[server-port]]/devcards.html"}
                 build-id)
  ;; looks like you have to look at the websocket url to know what the port and hostname are going to be, bit rubbish
  (let [config (fig/config-for-id build-id)]
    (try
      (assert (get-in config [:options :devcards]) "Devcards must be enabled")
      (let [connect-url (get-in config [:options :closure-defines 'figwheel.repl/connect-url])]
        (assert connect-url "Could not detect a url to connect to")
        connect-url)
      (catch Exception e
        (fig-api/stop build-id)
        nil))))

(defn stop-devcards [build-id]
  (fig-api/stop build-id))

(defn extract-links [content]
  (->> (h/as-hickory (h/parse content))
       (s/select (s/child (s/class "com-rigsomelight-devcards-list-group-item")
                          s/last-child))
       (map (comp first :content))
       (map string/trim)
       (map #(str "#!/" %))))

(defn find-test-urls [^Session session]
  (let [dom (.getDOM (.getCommand session))
        root-node (.getDocument dom)
        html (.getOuterHTML dom (.getNodeId root-node) nil nil)]
    (extract-links html)))


(deftest go
  (let [build-id "example/dev"]
    (dcd/with-chrome-session (assoc dcd/default-options :chrome-args [])
      (fn [session opts]
        (let [url (kamera.figwheel/start-devcards build-id)]
          (try
            (.navigate session url)
            (.waitDocumentReady session 15000)
            (Thread/sleep 2000)
            (let [target-urls (kamera.figwheel/find-test-urls session)]
              (k/run-tests
               session
               (map (fn [target-url]
                      {:url target-url
                       :reference-file (str (subs target-url 3) ".png")})
                    target-urls)
               (-> k/default-opts
                   (update :default-target merge {:root url
                                                  :reference-directory "example/test-resources/kamera"
                                                  :screenshot-directory "example/target/kamera"})))
              (println "Finished run tests"))

            (finally
              (kamera.figwheel/stop-devcards build-id))))))))


(comment
  (fig-api/start {:mode :serve :open-url false} "example/dev")
  (fig-api/stop "example/dev"))

;; also have an api where you start the build and provide the figwheel url etc, because they may not use the edn files.
;; maybe it's possible to get chrome to execute a script that calls fighwheel in cljs to get a list of test namespaces rather than scraping?
