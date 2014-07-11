(ns ditto.web
  (:require [ditto
             [scheduler :as scheduler]
             [entertainment-ami :as base]
             [bake-service-ami :as service-ami]
             [public-ami :as public-ami]
             [packer :as packer]
             [aws :as aws]
             [pokemon :as pokemon]
             [onix :as onix]
             [yum :as yum]
             [nokia :as nokia]]
            [cheshire.core :as json]
            [compojure.core :refer [defroutes context GET PUT POST DELETE]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.middleware.format-response :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
            [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument]]
            [overtone.at-at :refer [show-schedule]]))

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
  "Returns the service status"
  []
  (let [baking-scheduled (scheduler/job-is-scheduled? "baker")
        ami-killing-scheduled (scheduler/job-is-scheduled? "killer")
        success (and baking-scheduled ami-killing-scheduled)]
    (response
     {:name "ditto"
      :version *version*
      :success success
      :dependencies [{:name "baking-schedule" :success baking-scheduled}
                     {:name "ami-killing-schedule" :success ami-killing-scheduled}]}
     "application/json"
     (if success 200 500))))

(defn latest-amis
  "Returns the latest amis that we know about"
  []
  {:status 200 :body {:nokia-base-hvm (nokia/latest-nokia-ami :hvm)
                      :nokia-base-para (nokia/latest-nokia-ami :para)
                      :ent-base-hvm (nokia/entertainment-base-ami-id :hvm)
                      :ent-base-para (nokia/entertainment-base-ami-id :para)
                      :ent-public-hvm (nokia/entertainment-public-ami-id :hvm)
                      :ent-public-para (nokia/entertainment-public-ami-id :para)}})

(defn latest-service-amis
  "Returns the list of amis for the supplied service name"
  [service-name]
  (->> (aws/service-images service-name)
       (map #(select-keys % [:Name :ImageId]))
       (reverse)
       (take 10)))

(defn bake-entertainment-base-ami
  "Create a pair of new base entertainment ami from the latest nokia base ami.
   Takes a param of virt-type, either hvm or para.
   If dry-run then only return the packer template, don't run it."
  [virt-type dry-run]
  {:pre [(#{:hvm :para} virt-type)]}
  (let [template (base/create-base-ami virt-type)]
    (if-not dry-run
      (-> template
          (packer/build)
          (response))
      (response template))))

(defn bake-entertainment-public-ami
  "Create a new public entertainment ami from the latest ent base ami.
   If dry-run then only return the packer template, don't run it."
  [virt-type dry-run]
  {:pre [(#{:hvm :para} virt-type)]}
  (let [template (public-ami/create-public-ami virt-type)]
    (if-not dry-run
      (-> template
          (packer/build)
          (response))
      (response template))))

(defn bake-service-ami
  "Bake a new ami for the service name and version based on the latest base ent ami.
   If dry-run then only return the packer template, don't run it."
  [name version dry-run virt-type]
  {:pre [#{:para :hvm} virt-type]}
  (if (not (onix/service-exists? name))
    (error-response (str "The service '" name "' doesn't exist.") 404)
    (let [rpm-name (onix/rpm-name name)]
      (if-let [version (yum/get-latest-iteration name version rpm-name)]
        (let [template (service-ami/create-service-ami name version rpm-name virt-type)]
          (if dry-run
            (response template)
            (-> template packer/build response)))
        (error-response (format "Are you baking too soon? No RPM for '%s' '%s'." name version) 404)))))

;; todo - either get rid of the non chroot version or factor these together if need to keep
(defn bake-chroot-service-ami
  "Bake a new ami for the service name and version based on the latest base ent ami.
   If dry-run then only return the packer template, don't run it."
  [name version dry-run virt-type]
  {:pre [#{:para :hvm} virt-type]}
  (if (not (onix/service-exists? name))
    (error-response (str "The service '" name "' doesn't exist.") 404)
    (let [rpm-name (onix/rpm-name name)]
      (if-let [version (yum/get-latest-iteration name version rpm-name)]
        (let [template (service-ami/create-chroot-service-ami name version rpm-name virt-type)]
          (if dry-run
            (response template)
            (-> template packer/build response)))
        (error-response (format "Are you baking too soon? No RPM for '%s' '%s'." name version) 404)))))

(defn service-icon
  "Returns the service icon"
  []
  {:status 200
   :headers {"Content-Type" "image/jpeg"}
   :body (-> (clojure.java.io/resource "ditto.jpg")
             (clojure.java.io/input-stream))})

(def lock (atom false))

(defn lockable-bake
  "Bake the ami if the service isn't locked"
  [bake]
  (if-not @lock
    (bake)
    {:status 503
     :headers {"Content-Type" "text/plain"}
     :body (str "Service is temporarily locked with message: " @lock)}))

(defn remove-ami
  [service ami]
  "Reregister the supplied ami"
  (if (aws/deregister-ami service ami)
      (response (format "%s deleted successfully" ami) "application/json" 204)
      (response (format "Failed to remove %s" ami) "application/json" 500)))

(defroutes routes
  (GET "/healthcheck" []
       (status))
  (context
   "/1.x" []

   (GET "/ping" []
        "pong")

   (GET "/status" []
        (status))

   (GET "/amis" []
        (latest-amis))

   (POST "/lock" req
         (let [message (get-in req [:body "message"])]
           (reset! lock (or message "Ditto is locked, no reason was supplied."))
           (str "Ditto is locked and won't accept new builds: " @lock)))

   (DELETE "/lock" []
           (reset! lock false)
           {:status 204})

   (GET "/inprogress" []
        (response (with-out-str (show-schedule packer/timeout-pool)) "text/plain"))

   (POST "/clean/:service" [service]
         (if (= service "all")
           (scheduler/kill-amis)
           (scheduler/kill-amis-for-application service)))

   (GET "/amis/:service-name" [service-name]
        (latest-service-amis service-name))

   (POST "/bake/entertainment-ami/:virt-type" [virt-type dryrun]
         (lockable-bake #(bake-entertainment-base-ami (keyword virt-type) dryrun)))

   (POST "/bake/public-ami/:virt-type" [virt-type dryrun]
         (lockable-bake #(bake-entertainment-public-ami (keyword virt-type) dryrun)))

   (POST "/bake/:service-name/:service-version" [service-name service-version dryrun virt-type]
         (lockable-bake
          #(bake-service-ami service-name service-version dryrun (or (keyword virt-type) :para))))

   (POST "/chroot/:service-name/:service-version" [service-name service-version dryrun virt-type]
         (lockable-bake
          #(bake-chroot-service-ami service-name service-version dryrun (or (keyword virt-type) :para))))

   (POST "/make-public/:service" [service]
         (aws/allow-prod-access-to-service service))

   (DELETE "/:service-name/amis/:ami" [service-name ami]
           (remove-ami service-name ami))

   (GET "/pokemon" []
        (response pokemon/ditto "text/plain"))

   (GET "/icon" []
        (service-icon)))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-json-body)
      (expose-metrics-as-json)))
