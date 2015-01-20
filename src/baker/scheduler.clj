(ns baker.scheduler
  (:require [baker
             [awsclient :as awsclient]
             [onix :as onix]
             [packer :as packer]]
            [clj-time.core :as core-time]
            [clj-time.coerce :as coerce-time]
            [clojure.tools.logging :refer [debug info warn error]]
            [environ.core :refer [env]]
            [io.clj.logging :refer [with-logging-context]]
            [overtone.at-at :as at-at])
  (:import [org.joda.time DateTimeConstants]))

(def scheduler-pool (at-at/mk-pool))

(defn ms-until-next-day
  "Returns the number of milliseconds until the next instance of Thursday at midnight"
  [day]
  (let [now (core-time/now)]
    (-> (cond-> now
                (>= (.getDayOfWeek now) day)
                (.plusWeeks 1))
        (.withDayOfWeek day)
        (.toDateMidnight)
        ((fn [the-day] (core-time/interval now the-day)))
        (core-time/in-millis))))

(defn kill-amis-for-application
  "Deregisters amis for an application apart from the latest 5. Doesn't deregister any amis that are deployed.
   Note: amis are retrieved from AWS in latest first order."
  [name]
  (debug (str "Killing amis for " name))
  (let [amis (->> (awsclient/service-ami-ids name)
                  (reverse)
                  (drop 5)
                  (set)
                  (awsclient/filter-active-amis))]
    (debug (str "List of amis to kill: " amis))
    (doseq [ami amis]
      (debug (str "Degregistering AMI: " ami))
      (awsclient/deregister-ami name ami))))

(defn kill-amis
  "Deregister any sufficiently old amis"
  []
  (when (Boolean/valueOf (env :lister-available))
    (info "Starting process to kill old amis for all services")
    (doseq [app (onix/get-applications)]
      (try
        (kill-amis-for-application app)
        (catch Exception e
          (with-logging-context {:application app}
            (error e "Error while killing AMIs for application")))))))

(defn start-builder-scheduler
  "Accepts a list of function calls to schedule in the form of a map:
  {:handler 'function to call' :desc 'description' :run-weekly-on 'which day of the week to run on 1 (monday) - 7'}"
  [scheduled-builders]
  (doseq [{:keys [handler run-weekly-on desc]} scheduled-builders]
    (let [initial-delay (ms-until-next-day run-weekly-on)]
      (info (format "Scheduling '%s' to run weekly on day: %s which is in: %s ms"
                    desc run-weekly-on initial-delay))
      (at-at/every (* 60 60 24 7 1000) handler scheduler-pool :initial-delay initial-delay :desc "baker"))))

(defn start-deregister-old-amis-scheduler
  "Starts a scheduler for a task that deregisters old amis for all services. The most recent 10 are kept
   along with, if different, the currently live ami."
  []
  (at-at/every (* 30 60 1000) kill-amis scheduler-pool :initial-delay (* 60 1000) :desc "killer"))
