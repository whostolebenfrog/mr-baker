(ns ditto.aws
  (:require [me.raynes.conch :as conch]
            [cheshire.core :as json]
            [clojure.tools.logging :refer [info warn error]]))

(conch/programs aws)

(defn owned-images-by-name
  "Returns a list of images owned by the current account and filtered by the supplied name.
   Accepts * as a wild card.

   Returns an array of matching images or nil.

   Images are returned sorted alphabetically by name."
  [name]
  (sort-by :Name
           (-> (aws "ec2" "describe-images"
                    "--region" "eu-west-1"
                    "--output" "json"
                    "--owner" "self"
                    "--filters" (str "Name=name,Values=" name))
               (json/parse-string true)
               :Images
               seq)))

(defn private-images-by-name
  "Returns a list of private images that match the supplied name.
   Accepts * as a wild card.

   Returns an array of matching images or nil"
  [name]
  (-> (aws "ec2" "describe-images"
           "--region" "eu-west-1"
           "--output" "json"
           "--filters" (str "Name=name,Values=" name) " Name=is-public,Values=false")
      (json/parse-string true)
      :Images
      seq))

(defn service-images
  "Returns the images for a service"
  [name]
  (owned-images-by-name (str "ent*-" name "-*")))

(defn deregister-ami
  "Deregister an ami. Returns true if successful, otherwise false"
  [name]
  (info "Deregistering AMI " name)
  (let [result (aws "ec2" "deregister-image"
                    "--region" "eu-west-1"
                    "--output" "json"
                    "--image-id" name)]
    (when (seq result)
      (-> (json/parse-string result true)
          (:return)
          (read-string)))))
