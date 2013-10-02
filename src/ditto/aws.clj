(ns ditto.aws
  (:require [me.raynes.conch :as conch]
            [cheshire.core :as json]))

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
           "--filters" (str "Name=name,Values=" name) " Name=is-public,Values=false")
      (json/parse-string true)
      :Images
      seq))
