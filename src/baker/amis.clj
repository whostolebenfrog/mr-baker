(ns baker.amis
  (:require [baker
             [awsclient :as awsclient]
             [common :as common]]))

(defn parent-ami
  "Returns the latest amaazon linux parent ami, accepts a virt-type (:para or :hvm)"
  [virt-type]
  {:pre [(#{:hvm :para} virt-type)]}
  ({:hvm "ami-6e7bd919" :para "ami-9c7ad8eb"} virt-type))

;; TODO - if we rename these to baker-base-al-* etc then
;; I think it makes more sense than entertainment, it the
;; long term it would be better to make this more configureable
;; but having some default stuff and consistent naming makes
;; some stuff possible like the latest-amis list and
;; latest-service-amis - this would need to move to be under
;; the builders name space with a remove function for each type
;; of template. Nice but one for in the future.
(defn ami-name-base
  "Returns the base part of an ami name"
  [virt-type]
  (format "entertainment-base-al-%s-" (name virt-type)))

(defn public-name-base
  "Returns the base part of the public ami name"
  [virt-type]
  (format "entertainment-public-al-%s-" (name virt-type)))

(defn base-ami-id
  "Returns the id of the latest entertainment base ami"
  [virt-type]
  (-> (awsclient/owned-amis-by-name (str (ami-name-base virt-type) "*"))
      last
      :image-id))

(defn public-ami-id
  "Returns the id of the latest entertainment public ami"
  [virt-type]
  (-> (awsclient/owned-amis-by-name (str (public-name-base virt-type) "*"))
      last
      :image-id))

(defn latest-amis
  "Returns the latest amis that we know about"
  []
  (common/response {:parent-hvm (parent-ami :hvm)
                    :parent-para (parent-ami :para)
                    :base-hvm (base-ami-id :hvm)
                    :base-para (base-ami-id :para)
                    :public-hvm (public-ami-id :hvm)
                    :public-para (public-ami-id :para)}
                   "application/json" 200))

(defn latest-service-amis
  "Returns the list of amis for the supplied service name"
  [service-name]
  (->> (awsclient/service-amis service-name)
       (map #(select-keys % [:name :image-id]))
       (reverse)
       (take 10)))

(defn remove-ami
  [service ami]
  "Reregister the supplied ami"
  (if (awsclient/deregister-ami service ami)
      (common/response (format "%s deleted successfully" ami) "application/json" 204)
      (common/response (format "Failed to remove %s" ami) "application/json" 500)))
