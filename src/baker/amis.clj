(ns baker.amis
  (:require [baker.awsclient :as awsclient]))

(defn parent-ami
  "Returns the latest amaazon linux parent ami, accepts a virt-type (:para or :hvm)"
  [virt-type]
  {:pre [(#{:hvm :para} virt-type)]}
  ({:hvm "ami-6e7bd919" :para "ami-9c7ad8eb"} virt-type))

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
  (-> (awsclient/owned-amis-by-name (str (ent-ami-name-base virt-type) "*"))
      last
      :image-id))

(defn entertainment-public-ami-id
  "Returns the id of the latest entertainment public ami"
  [virt-type]
  (-> (awsclient/owned-amis-by-name (str (ent-public-name-base virt-type) "*"))
      last
      :image-id))
