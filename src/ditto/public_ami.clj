(ns ditto.public-ami
  "Creates the public base ami instance, this is pretty much the base instance
   with puppet enabled so that real auth works. The base instance can't have puppet
   enabled as it turns on ldap based auth with breaks packers auth."
  (:require [ditto
             [bake-common :refer :all]
             [entertainment-ami :as base]
             [aws :as aws]
             [nokia :as nokia]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [cheshire.core :as json]
            [environ.core :refer [env]]))

(defn ami-name
  [virt-type]
  "Returns the ami name for now"
  (str "entertainment-public-"
       (name virt-type)
       "-"
       (time-format/unparse (time-format/formatter "YYYY-MM-dd_HH-mm-ss") (time-core/now))))

(defn motd
  [source-ami]
  "Set the message of the day"
  (shell "echo -e \"\\nEntertainment Public AMI\" >> /etc/motd"
         (format "echo -e \"\\nBake date: %s\" >> /etc/motd"
                 (time-format/unparse (time-format/formatters :date-time-no-ms) (time-core/now)))
         (format "echo -e \"\\nSource AMI: %s\\n\" >> /etc/motd" source-ami)))

(def numel-on
  "Switch on Numel integration"
  (shell "yum install -y numel-integration"))

(def puppet-on
  "Enable puppet on the public base instance"
  (shell "chkconfig puppet on"))

(defn public-ami
  "Provides the template for the public-ami"
  [source-ami virt-type]
  (let [builder (maybe-with-keys
                 {:ami_name (ami-name virt-type)
                  :iam_instance_profile "baking"
                  :instance_type (instance-type-for-virt-type virt-type)
                  :region "eu-west-1"
                  :run_tags {:name "Public AMI Bake"
                             :owner "ditto"
                             :description "Temp instance used to bake the public ent ami"}
                  :tags {:name "Entertainment Public AMI"
                         :service "public"}
                  :security_group_id "sg-c453b4ab"
                  :source_ami source-ami
                  :ssh_timeout "5m"
                  :ssh_username "nokiarebake"
                  :subnet_id "subnet-bdc08fd5"
                  :temporary_key_pair_name "nokiarebake-{{uuid}}"
                  :type "amazon-ebs"
                  :vpc_id "vpc-7bc88713"})]
    {:builders [builder]
     :provisioners [(motd source-ami)
                    numel-on
                    puppet-on]}))

(defn create-public-ami
  "Creates a public ami from the latest base entertainment ami
   Enabled puppet and sets the motd"
  [virt-type]
  (json/generate-string (public-ami (nokia/latest-nokia-ami virt-type) virt-type)))
