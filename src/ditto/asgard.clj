(ns ditto.asgard
  "Namespace for talking to asgard. This needs to be removed and the call should
   go through exploud instead as we don't want multiple services depending on asgard"
  (:require [clj-http.client :as client]
            [environ.core :refer [env]]))

(def asgard-base-url (env :service-asgard-url))

(defn- image-ids-from-json
  "This walks any structure and extracts all values of :imageId into a set, which means
   that duplicates are thrown away."
  [cluster]
  (->> cluster (first) :instances (map :imageId) (set)))

(defn active-amis-for-application
  [name]
  (let [{:keys [body]} (client/get (str asgard-base-url "/eu-west-1/cluster/show/" name ".json")
                                   {:throw-exceptions false :as :json})]
    (image-ids-from-json body)))
