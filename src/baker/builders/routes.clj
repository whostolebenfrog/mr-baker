(ns baker.builders.routes
  (:require [baker.builders
             [bake-example-template :as example]
             [scheduler :as builders-scheduler]]
            [compojure.core :refer [GET PUT POST DELETE defroutes]]))

(def lock (atom false))

;; chuck these into another namespace just so that they can be altered without
;; touching this file
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

  (POST "/example/:name/:version/:virt-type" [name version dry-run virt-type]
        (lockable-bake #(example/bake-example-ami name version dry-run (keyword virt-type))))

  (POST "/chroot-example/:name/:version/:virt-type" [name version dry-run virt-type]
        (lockable-bake #(example/bake-example-ami-chroot name version dry-run (keyword virt-type)))))
