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
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [overtone.at-at :as at-at]
            [clojure.set :refer [difference]]))

(def thursday 4)
(def week-in-ms (* 60 60 24 7 1000))
(def day-in-ms (* 60 60 24 1000))
(def half-an-hour-in-ms (* 30 60 1000))

(def scheduler-pool (at-at/mk-pool))

(defn next-day-start
  "Returns the start of the next day after the specified time, where day = 1 for Monday to 7 for Sunday."
  [time day]
  (let [dow (core-time/day-of-week time)]
    (if (< dow day)
      (core-time/plus (core-time/today-at-midnight) (core-time/days (- day dow)))
      (core-time/plus (core-time/today-at-midnight) (core-time/days (- (+ day 7) dow))))))

(defn initial-delay
  []
  (let [current-time (core-time/now)
        next-thursday-start (next-day-start current-time thursday)]
    (- (coerce-time/to-long next-thursday-start) (coerce-time/to-long current-time))))

(defn output-piped-input-stream
  [stream]
  (info "I'll try to read the input stream")
  (let [rdr (clojure.java.io/reader stream)]
    (doseq [line (line-seq rdr)]
      (info line))))


(defn bake-base-ami
  []
  (info "Starting scheduled bake of base ami.")
  (-> (base/create-base-ami (nokia/latest-nokia-ami))
      (packer/build)
      (output-piped-input-stream)))

(defn bake-public-ami
  []
  (info "Starting scheduled bake of public ami.")
  (-> (public-ami/create-public-ami)
      (packer/build)
      (output-piped-input-stream)))

(defn bake-amis
  []
  (bake-base-ami)
  (bake-public-ami))

(defn kill-amis-for-application
  "Deregisters amis for an application apart from the latest 10. Doesn't deregister the ami that is deployed
   according to asgard. Note: amis are retrieved from AWS in latest first order."
  [name]
  (let [amis (->> (map :ImageId (aws/service-images "ditto"))
                  (reverse)
                  (drop 5))
        amis (difference (set amis) (asgard/active-amis-for-application name))]
    (when (seq amis)
      (info (format "Deregistering amis: %s for :%s" name amis))
      (map aws/deregister-ami amis))))

(defn kill-amis
  []
  (info "Starting process to kill old amis for all services")
  (map kill-amis-for-application (onix/get-applications)))

(defn start-bake-scheduler
  "Start the baking scheduler, getting it to occur every time-ms ms with an initial delay before
   the first bake of initial-delay-ms ms. No parameter variant sets the bakes to occur a the start
   of every Thursday, as a new Nokia ami is made available every Wednesday. "
  ([time-ms initial-delay-ms]
     (info "Setting next base and public api bake time, initial-delay:" initial-delay "interval:" time-ms)
     (at-at/every time-ms
            bake-amis
            scheduler-pool
            :initial-delay initial-delay-ms
            :desc "baker"))
  ([] (start-bake-scheduler week-in-ms (initial-delay))))

(defn start-deregister-old-amis-scheduler
  "Starts a scheduler for a task that deregisters old amis for all services. The most recent 10 are kept
   along with, if different, the currently live ami."
  ([time-ms initial-delay-ms]
     (at-at/every time-ms
            kill-amis
            scheduler-pool
            :initial-delay initial-delay-ms
            :desc "killer"))
  ([] (start-deregister-old-amis-scheduler half-an-hour-in-ms half-an-hour-in-ms)))

(defn job-is-scheduled?
  "Returns truthy if the named job is scheduled"
  [name]
  (some #(= (:desc %) name) (at-at/scheduled-jobs scheduler-pool)))
