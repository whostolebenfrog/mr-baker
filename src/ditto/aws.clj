(ns ditto.aws
  (:require [me.raynes.conch :as conch]
            [cheshire.core :as json]))

(conch/programs aws)

(defn get-owned-images-by-name
  "Returns a list of images owned by the current account and filtered by the supplied name.
   Accepts * as a wild card.

   Returns an array of matching images or [].

   Images are returned sorted alphabetically by name."
  [name]
  (sort-by :Name
           (-> (aws "ec2" "describe-images"
                    "--owner" "self"
                    "--filters" (str "Name=name,Values=" name))
               (json/parse-string true)
               :Images)))
