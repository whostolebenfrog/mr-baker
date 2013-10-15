(ns ditto.onix
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]))

(def onix-base-url
  (env :service-onix-url))

(defn service-exists?
  "Returns true if the service is known to onix; otherwise returns false."
  [service-name]
  (let [app-url (str onix-base-url "/1.x/applications/" service-name)
        response (client/head app-url {:throw-exceptions false})
        status (:status response)]
    (= status 200)))
