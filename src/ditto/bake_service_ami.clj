(ns ditto.bake-service-ami
  (:require [ditto.entertainment-ami :as base]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]))

(defn service-ami-name
  "Returns the ami name for the service with date/time now"
  [service-name]
  (str "entertainment-base-" (time-format/unparse
                              (time-format/formatter "YYYY-MM-dd_HH-mm-ss")
                              (time-core/now))))

;; TODO
(defn motd
  "Set up the message of the day"
  [service-name service-version])


(defn service-template
  "Generates a new ami template for the servivce"
  [service-name service-version]
  {:builders [{:access_key (env :service-aws-access-key)
               :ami_name (service-ami-name service-name)
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
