(ns ditto.setup
  (:require [ditto
             [web :as web]
             [scheduler :as scheduler]]
            [clojure.string :as cs :only (split)]
            [clojure.tools.logging :refer [info warn error]]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [mixradio.instrumented-jetty :refer [run-jetty]]
            [radix.setup :as setup])
  (:gen-class))

(defonce server (atom nil))

(defn configure-server
  [server]
  (doto server
    (.setStopAtShutdown true)
    (.setStopTimeout setup/shutdown-timeout)))

(defn start-server []
  (run-jetty #'web/app {:port setup/service-port
                        :max-threads setup/threads
                        :join? false
                        :stacktraces? (not setup/production?)
                        :auto-reload? (not setup/production?)
                        :configurator configure-server
                        :send-server-version false}))

(defn start []
  (setup/configure-logging)
  (setup/start-graphite-reporting {:graphite-prefix (cs/join "." [(env :environment-name) (env :service-name) (env :box-id setup/hostname)])})
  (scheduler/start-bake-scheduler)
  (scheduler/start-deregister-old-amis-scheduler)
  (reset! server (start-server)))

(defn stop []
  (when-let [s @server]
    (.stop s)
    (reset! server nil))
  (shutdown-agents))

(defn -main [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
  (start))
