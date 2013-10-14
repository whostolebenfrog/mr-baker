(ns ditto.yum
  (:require [ditto
             [bake-service-ami :as bake-service]]
            [environ.core :refer [env]]
            [clj-http.client :as client]))

(defn rpm-url
  [service-name service-version]
  (str (env :service-yum-url) "/" (bake-service/rpm-name service-name service-version)))

(defn ami-exists?
  "Returns true if the ami exists in the brislabs yumrepo; otherwise returns false."
  [service-name service-version]
  (let [response (client/head (rpm-url service-name service-version) {:throw-exceptions false})
        status (:status response)]
    (= status 200)))
