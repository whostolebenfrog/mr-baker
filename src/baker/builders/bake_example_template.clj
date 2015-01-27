(ns baker.builders.bake-example-template
  (:require [baker
             [amis :as amis]
             [bake-common :refer :all]
             [common :as common]
             [onix :as onix]
             [packer :as packer]]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [clojure.string :as str]
            [radix.error :refer [error-response]]))

(defn service-ami-name
  "Returns the ami name for the service with date/time now"
  [service-name service-version virt-type]
  (str "ent-" service-name "-"
       service-version "-"
       (name virt-type) "-"
       (time-format/unparse
        (time-format/formatter "YYYY-MM-dd_HH-mm-ss")
        (time-core/now))))

;; TODO - need to make an example service to bake here
;; ideally lets still accept version and name
(defn service-rpm
  "Install the service rpm on to the machine"
  [service-name service-version rpm-name]
  (let [rpm-full-name "some download path TODO"]
    (cshell (str "wget -nv http://yumrepo.brislabs.com/ovimusic/" rpm-full-name)
            (str "yum -y install " rpm-full-name)
            (str "rm -fv " rpm-full-name))))

(defn provisioners
  "Returns a list of provisioners for the bake."
  [service-name service-version rpm-name]
  [(service-rpm service-name service-version rpm-name)])

;; TODO - rename
(defn chroot-service-template
  "Generates a new ami template for chroot bake of the service"
  [service-name service-version rpm-name source-ami virt-type embargo]
  (let [builder (maybe-with-keys
                 {:ami_name (service-ami-name service-name service-version virt-type)
                  :tags {:name (format "%s AMI" service-name)
                         :service service-name}
                  :source_ami source-ami
                  :ami_virtualization_type (virtualisation-type-long virt-type)
                  :type "amazon-chroot"})]
    {:builders [builder]
     :provisioners (provisioners service-name service-version rpm-name)}))

;; TODO - rename
(defn create-chroot-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version rpm-name virt-type]
  (chroot-service-template service-name service-version rpm-name
                           (amis/parent-ami virt-type)
                           virt-type))

;; TODO - fixyfix
;; TODO - add a onix enabled flag around service-exists?
;; TODO - flag around rpm-name
(defn bake-chroot-service-ami
  "Bake a new ami for the service name and version based on the latest base ent ami.
   If dry-run then only return the packer template, don't run it."
  [name version dry-run virt-type]
  {:pre [#{:para :hvm} virt-type]}
  (if (not (onix/service-exists? name))
    (error-response (str "The service '" name "' doesn't exist.") 404)
    (let [rpm-name (onix/rpm-name name)]
      (let [template (create-chroot-service-ami name version rpm-name virt-type)]
        (if dry-run
          (common/response (json/generate-string template))
          (common/response (packer/build template name)))))))
