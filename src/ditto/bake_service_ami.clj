(ns ditto.bake-service-ami
  (:require [ditto
             [entertainment-ami :as base]
             [bake-common :refer :all]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]))

;; TODO - presumably we are going to have to start an instance for this baking task
   ;; when we provision the new base-ami

;; TODO _ put the service name in here!
(defn service-ami-name
  "Returns the ami name for the service with date/time now"
  [service-name]
  (str "entertainment-base-" (time-format/unparse
                              (time-format/formatter "YYYY-MM-dd_HH-mm-ss")
                              (time-core/now))))

(defn motd
  "Set up the message of the day"
  [service-name service-version]
  (shell (format "echo -e \"\\nEntertainment %s AMI\" >> /etc/motd" service-name)
         (format "echo -e \"\\nBake date: %s\" >> /etc/motd"
                 (time-format/unparse (time-format/formatters :date-time-no-ms) (time-core/now)))
         (format "echo -e \"\\nService: %s %s\\n\" >> /etc/motd" service-name service-version)))

(defn service-template
  "Generates a new ami template for the servivce"
  [service-name service-version]
  {:builders [{:access_key (env :service-aws-access-key)
               :ami_name (service-ami-name service-name service-version)
               :iam_instance_profile "baking"
               :instance_type "t1.micro"
               :region "eu-west-1"
               :secret_key (env :service-aws-secret-key)
               :security_group_id "sg-c453b4ab"
               :source_ami (base/entertainment-base-ami-id)
               :temporary_key_pair_name "nokia-{{uuid}}"
               :ssh_timeout "5m"
               :ssh_username "nokia"
               :subnet_id "subnet-bdc08fd5"
               :type "amazon-ebs"
               :vpc_id "vpc-7bc88713"}]
   :provisioners [(motd service-name service-version)]})

(defn create-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version]
  (json/generate-string (service-template service-name service-version)))
