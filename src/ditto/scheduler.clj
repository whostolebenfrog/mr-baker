(ns ditto.scheduler
  (:require [ditto
             [aws :as aws]
             [entertainment-ami :as base]
             [nokia :as nokia]
             [asgard :as asgard]
             [public-ami :as public-ami]
             [onix :as onix]
             [packer :as packer]]
            [clj-time.core :as core-time]
            [clj-time.coerce :as coerce-time]
            [clj-http.client :as client]
            [clojure.tools.logging :refer [debug info warn error]]
            [environ.core :refer [env]]
            [io.clj.logging :refer [with-logging-context]]
            [overtone.at-at :as at-at]
            [clojure.set :refer [difference]])
  (:import [org.joda.time DateTimeConstants]))

(def week-in-ms (* 60 60 24 7 1000))
(def half-an-hour-in-ms (* 30 60 1000))

(def scheduler-pool (at-at/mk-pool))

(defn ms-until-next-thursday
  "Returns the number of milliseconds until the next instance of Thursday at midnight"
  []
  (let [now (core-time/now)]
    (-> (cond-> now
                (>= (.getDayOfWeek now) (DateTimeConstants/THURSDAY))
                (.plusWeeks 1))
        (.withDayOfWeek (DateTimeConstants/THURSDAY))
        (.toDateMidnight)
        ((fn [thurs] (core-time/interval now thurs)))
        (core-time/in-millis))))

(defn output-piped-input-stream
  "Writes the supplied output stream to the logger"
  [stream]
  (doseq [line (line-seq (clojure.java.io/reader stream))]
    (info line)))

(defn bake-base-ami
  "Bake the base ami"
  []
  (info "Starting scheduled bake of base ami.")
  (-> (base/create-base-ami (nokia/latest-nokia-ami))
      (packer/build "base")
      (output-piped-input-stream)))

(defn bake-public-ami
  "Bake the public ami"
  []
  (info "Starting scheduled bake of public ami.")
  (-> (public-ami/create-public-ami)
      (packer/build "public")
      (output-piped-input-stream)))

(defn bake-amis
  "Bake a new base ami, followed by its public counterpart"
  []
  (try
    (bake-base-ami)
    (bake-public-ami)
    (catch Exception e
      (error e "Error while baking shared AMIs"))))

(defn kill-amis-for-application
  "Deregisters amis for an application apart from the latest 5. Doesn't deregister the ami that is deployed
   according to asgard. Note: amis are retrieved from AWS in latest first order."
  [name]
  (debug (str "Killing amis for " name))
  (let [amis (->> (map :ImageId (aws/service-images name))
                  (reverse)
                  (drop 5))
        amis (difference (set amis) (asgard/active-amis-for-application name))]
    (debug (str "List of amis to kill: " amis))
    (doseq [ami amis]
      (debug (str "Degregistering AMI: " ami))
      (aws/deregister-ami name ami))))

(defn kill-amis
  "Deregister any sufficiently old amis"
  []
  (info "Starting process to kill old amis for all services")
  (doseq [app (onix/get-applications)]
    (try
      (kill-amis-for-application app)
      (catch Exception e
        (with-logging-context {:application app}
          (error e "Error while killing AMIs for application"))))))

(defn start-bake-scheduler
  "Start the baking scheduler, getting it to occur every time-ms ms with an initial delay before
   the first bake of initial-delay-ms ms. No parameter variant sets the bakes to occur a the start
   of every Thursday, as a new Nokia ami is made available every Wednesday. "
  ([time-ms initial-delay-ms]
     (info "Setting next base and public api bake time, initial-delay:" initial-delay-ms "interval:" time-ms)
     (at-at/every
      time-ms
      bake-amis
      scheduler-pool
      :initial-delay initial-delay-ms
      :desc "baker"))
  ([] (start-bake-scheduler week-in-ms (ms-until-next-thursday))))

(defn start-deregister-old-amis-scheduler
  "Starts a scheduler for a task that deregisters old amis for all services. The most recent 10 are kept
   along with, if different, the currently live ami."
  ([time-ms initial-delay-ms]
     (at-at/every
      time-ms
      kill-amis
      scheduler-pool
      :initial-delay (* 60 1000)
      :desc "killer"))
  ([] (start-deregister-old-amis-scheduler half-an-hour-in-ms half-an-hour-in-ms)))

(defn job-is-scheduled?
  "Returns truthy if the named job is scheduled"
  [name]
  (some #(= (:desc %) name) (at-at/scheduled-jobs scheduler-pool)))
