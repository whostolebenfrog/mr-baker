(ns ditto.nokia
  (:require [ditto.awsclient :as awsclient]
            [clj-time
             [core :as time-core]
             [format :as time-format]
             [periodic :as time-periodic]]))

(defn past-wednesdays
  "Returns a seq of previous wednesdays orderered most recent first"
  []
  (->> (time-periodic/periodic-seq (time-core/date-time 2013 8 28) (time-core/weeks 1))
       (take-while #(time-core/before? % (time-core/now)))
       (reverse)))

(defn nokia-ami-name
  "Provides the name of the nokia base ami for the supplied date and type"
  [type date]
  (format "centos-6-x86_64-%s-%s-release"
          (time-format/unparse (time-format/formatter "MMddYYYY") date)
          type))

(defn latest-nokia-ami
  "Returns the latest nokia base ami image-id. Accepts server type of either :ebs or :instance.
   Defaults to :ebs if not specified."
  [virt-type]
  {:pre [(#{:hvm :para} virt-type)]}
  ({:hvm "ami-892fe1fe" :para "ami-672ce210"} virt-type))

(defn ent-ami-name-base
  "Returns the base part of an ami name"
  [virt-type]
  (format "entertainment-base-al-%s-" (name virt-type)))

(defn ent-public-name-base
  "Returns the base part of the public ami name"
  [virt-type]
  (format "entertainment-public-al-%s-" (name virt-type)))

(defn entertainment-base-ami-id
  "Returns the id of the latest entertainment base ami"
  [virt-type]
  (-> (awsclient/owned-images-by-name (str (ent-ami-name-base virt-type) "*"))
      last
      :image-id))

(defn entertainment-public-ami-id
  "Returns the id of the latest entertainment public ami"
  [virt-type]
  (-> (awsclient/owned-images-by-name (str (ent-public-name-base virt-type) "*"))
      last
      :image-id))
