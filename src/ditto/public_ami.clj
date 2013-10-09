(ns ditto.public-ami
  "Creates the public base ami instance, this is pretty much the base instance
   with puppet enabled so that real auth works. The base instance can't have puppet
   enabled as it turns on ldap based auth with breaks packers auth."
  (:require [ditto
             [bake-common :refer :all]
             [entertainment-ami :as base]
             [aws :as aws]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [cheshire.core :as json]
            [environ.core :refer [env]]))

(defn entertainment-public-ami-id
  "Returns the id of the latest entertainment public ami"
  []
  (-> (aws/owned-images-by-name "entertainment-public-*")
      (last)
      :ImageId))

(defn ami-name
  []
  "Returns the ami name for now"
  (str "entertainment-public-"
       (time-format/unparse (time-format/formatter "YYYY-MM-dd_HH-mm-ss") (time-core/now))))

(defn motd
  [source-ami]
  "Set the message of the day"
  (shell "echo -e \"\\nEntertainment Public AMI\" >> /etc/motd"
         (format "echo -e \"\\nBake date: %s\" >> /etc/motd"
                 (time-format/unparse (time-format/formatters :date-time-no-ms) (time-core/now)))
         (format "echo -e \"\\nSource AMI: %s\\n\" >> /etc/motd" source-ami)))

(def puppet-on
  "Enable puppet on the public base instance"
  (shell "chkconfig puppet on"))

(defn create-public-ami
  "Creates a public ami from the latest base entertainment ami
   Enabled puppet and sets the motd"
  []
  (let [parent-ami (base/entertainment-base-ami-id)
        builder (-> {:access_key (env :service-aws-access-key)
                     :ami_name (ami-name)
                     :iam_instance_profile "baking"
                     :instance_type "t1.micro"
                     :region "eu-west-1"
                     :secret_key (env :service-aws-secret-key)
                     :security_group_id "sg-c453b4ab"
                     :source_ami parent-ami
                     :temporary_key_pair_name "nokiarebake-{{uuid}}"
                     :ssh_timeout "5m"
                     :ssh_username "nokiarebake"
                     :subnet_id "subnet-bdc08fd5"
                     :type "amazon-ebs"
                     :vpc_id "vpc-7bc88713"}
                    (maybe-with-keys))]
    (json/generate-string {:builders [builder]
                           :provisioners [(motd parent-ami)
                                          puppet-on]})))
