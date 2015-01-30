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

(defn install-rpm
  "Install the service rpm on to the machine"
  [name version]
  (let [rpm-name (str name "-" version)]
   (cshell (str "wget -nv http://github.com/mixradio/baker/examples/" rpm-name)
           (str "yum -y install " rpm-name)
           (str "rm -fv " rpm-name))))

(defn provisioners
  "Returns a list of provisioners for the bake."
  [name version]
  [(install-rpm name version)])

(defn packer-template-chroot
  "Generates a new ami template for chroot bake of the service"
  [name version source-ami virt-type]
  (let [builder (maybe-with-keys
                 {:ami_name (service-ami-name name version virt-type)
                  :tags {:name (format "%s AMI" name)
                         :service name}
                  :source_ami source-ami
                  :ami_virtualization_type (virtualisation-type-long virt-type)
                  :type "amazon-chroot"})]
    {:builders [builder]
     :provisioners (provisioners name version)}))

(defn packer-template
  "Generates a new ami template for a standard ebs bake of the service. You may need
  to create new iam roles for baking depending on the requirements of your template. The
  example here provides a placeholder for vpc and security groups. If you have no vpc then
  ommit this entry along with the subnet_id, if your bake requires network access then you will need to provide a
  security group to allow this. ssh_username assumes amazon-linux in this example. "
  [name version source-ami virt-type]
  (let [builder (maybe-with-keys
                 {:ami_name (service-ami-name name version virt-type)
                  :iam_instance_profile "baking"
                  :instance_type (instance-type-for-virt-type virt-type)
                  :region "eu-west-1"
                  :run_tags {:name (format "%s AMI Bake" name)
                             :owner "baker"
                             :description (format "Temp instance used to bake the %s ent ami" name)}
                  :tags {:name (format "%s AMI" name)
                         :service name}
                  :security_group_id "security-group-id-placeholder"
                  :source_ami source-ami
                  :ssh_timeout "5m"
                  :ssh_username "ec2-user"
                  :subnet_id "target-subnet-id-placeholder"
                  :type "amazon-ebs"
                  :vpc_id "vpc-id-placeholder"})]
    {:builders [builder]
     :provisioners (provisioners name version)}))

(defn bake-example-ami-chroot
  "Bake a new ami for the service name and version based on the latest base ami.
  If dry-run then only return the packer template, don't run it.

  This bake uses a chroot packer template, this makes the bake faster and avoids
  starting up new instances to bake the template. However it does mean that baker
  must be running in aws for it to function on an instance of the same underlying
  OS as the target. It also doesn't work properly on some operating systems due
  to some limitations in packer. Amazon linux works very well but some RH based
  distros will fail without specific boot options."
  [name version dry-run virt-type]
  {:pre [#{:para :hvm} virt-type]}
  (if (not (onix/service-exists? name))
    (error-response (str "The service '" name "' doesn't exist.") 404)
    (let [template (packer-template-chroot name version
                                           (amis/parent-ami virt-type)
                                           virt-type)]
      (if dry-run
        (common/response (json/generate-string template))
        (common/response (packer/build template name))))))

(defn bake-example-ami
  "Bake a new ami for the service name and version based on the latest base ami.
   If dry-run then only return the packer template, don't run it."
  [name version dry-run virt-type]
  {:pre [#{:para :hvm} virt-type]}
  (if (not (onix/service-exists? name))
    (error-response (str "The service '" name "' doesn't exist.") 404)
    (let [template (packer-template name version
                                    (amis/parent-ami virt-type)
                                    virt-type)]
      (if dry-run
        (common/response (json/generate-string template))
        (common/response (packer/build template name))))))
