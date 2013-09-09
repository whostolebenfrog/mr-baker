(ns ditto.web
  (:require [ditto
             [entertainment-ami :as base]
             [packer :as packer]
             [pokemon :as pokemon]]
            [compojure.core :refer [defroutes context GET PUT POST DELETE]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.format-response :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
            [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                              replace-guid replace-mongoid replace-number]]
            [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument]]))

(def ^:dynamic *version* "none")
(defn set-version! [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn response
  "Accepts a body an optionally a content type and status. Returns a response object."
  [body & [content-type status]]
  {:status (or status 200)
   :headers {"Content-Type" (or content-type "application/json")}
   :body body})

;; TODO - check onix status in deps
;; TODO - check eu-west-1 status in deps
(defn status
  [recursive]
  (response (merge
             {:name "ditto" :version *version* :success true}
             (when recursive {:dependencies []}))))

(comment (spit "/tmp/xxx" (base/create-base-ami "ami-098b917d")))

;; TODO - what's the normal json response for an error etc?
;; TODO - can we schedule a task to create the base ami rather than having to push it?
(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        [] "pong")

   (GET "/status"
        [recursive] (status recursive))

   (POST "/entertainment-ami/:parent-ami" [parent-ami dry-run]
         (if-not dry-run
           (-> (base/create-base-ami parent-ami)
               (packer/build)
               (response))
           (response (base/create-base-ami parent-ami))))

   (GET "/pokemon" []
        (response pokemon/ditto "text/plain"))

   (GET "/icon" []
        {:status 200
         :headers {"Content-Type" "image/jpeg"}
         :body (-> (clojure.java.io/resource "ditto.jpg")
                   (.getFile)
                   (java.io.FileInputStream.))}))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
