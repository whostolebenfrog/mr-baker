(ns baker.builders.routes
  (:require [baker.builders
             [bake-base-ami :as base]
             [bake-public-ami :as public]
             [bake-service-ami :as service]
             [scheduler :as builders-scheduler]]
            [compojure.core :refer [GET PUT POST DELETE defroutes]]))

(def lock (atom false))

(defn lockable-bake
  "Bake the ami if the service isn't locked"
  [bake]
  (if-not @lock
    (bake)
    {:status 503
     :headers {"Content-Type" "text/plain"}
     :body (str "Service is temporarily locked with message: " @lock)}))

(defn lock-builders
  "Lock all builders (useful before redploying)"
  [req]
  (let [message (get-in req [:body "message"])]
    (reset! lock (or message "Baker is locked, no reason was supplied."))
    (str "Baker is locked and won't accept new builds: " @lock)))

(defn unlock-builders
  "Remove the builders lock"
  []
  (reset! lock false)
  {:status 204})

(defroutes route-defs

  (POST "/entertainment-ami/:virt-type" [virt-type dryrun]
        (lockable-bake #(base/bake-entertainment-base-ami (keyword virt-type) dryrun)))

  (POST "/public-ami/:virt-type" [virt-type dryrun]
        (lockable-bake #(public/bake-entertainment-public-ami (keyword virt-type) dryrun)))

  (POST "/base-amis" []
        (lockable-bake #(builders-scheduler/bake-amis))
        "OK")

  (POST "/:service-name/:service-version" [service-name service-version dryrun virt-type embargo]
        (lockable-bake
         #(service/bake-chroot-service-ami service-name service-version dryrun
                                           (or (keyword virt-type) :para) embargo))))
