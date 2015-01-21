(ns baker.onix
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]))

(def onix-base-url
  (env :onix-url))

(defn service-exists?
  "Returns true if the service is known to onix; otherwise returns false."
  [service-name]
  (let [app-url (str onix-base-url "/applications/" service-name)
        response (client/head app-url {:throw-exceptions false})
        status (:status response)]
    (= status 200)))

(defn get-applications
  "Returns the list of all applications in onix"
  []
  (get-in (client/get (str onix-base-url "/applications")
                      {:throw-exceptions false :as :json})
          [:body :applications]))

(defn service-property
  "Returns the onix property for the supplied name and service"
  [service-name property-name]
  (get-in (client/get (str onix-base-url "/applications/" service-name)
                      {:throw-exceptions false :as :json})
          [:body :metadata (keyword property-name)]))

(defn rpm-name
  "Returns any overridden RPM name for the given service, or nil if not defined"
  [service-name]
  (service-property service-name "rpmName"))

(defn shell-commands
  "Returns a list of shell commands to run on the service"
  [service-name]
  (service-property service-name "customBakeCommands"))
