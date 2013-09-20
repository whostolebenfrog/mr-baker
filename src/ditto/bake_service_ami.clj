(ns ditto.bake-service-ami
  (:require [ditto
             [entertainment-ami :as base]
             [bake-common :refer :all]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]))

(defn service-ami-name
  "Returns the ami name for the service with date/time now"
  [service-name service-version]
  (str "ent-" service-name "-"
       service-version "-"
       (time-format/unparse
        (time-format/formatter "YYYY-MM-dd_HH-mm-ss")
        (time-core/now))))

(defn motd
  "Set up the message of the day"
  [service-name service-version]
  (shell (format "echo -e \"\\nEntertainment %s AMI\" >> /etc/motd" service-name)
         (format "echo -e \"\\nBake date: %s\" >> /etc/motd"
                 (time-format/unparse (time-format/formatters :date-time-no-ms) (time-core/now)))
         (format "echo -e \"\\nService: %s %s\\n\" >> /etc/motd" service-name service-version)))

;; TODO - this is going to be replaced with a call to get this from tyranitar
(defn service-rpm
  "Install the service rpm on to the machine"
  [service-name service-version]
  (let [rpm-name (format "%s-%s.noarch.rpm" service-name service-version)]
    (shell (str "wget http://yumrepo.brislabs.com/ovimusic/" rpm-name)
           (str "yum -y install " rpm-name)
           (str "rm -fv " rpm-name))))

(defn props-path
  "Returnst the path of the properties file"
  [service-name environment]
  (str "/opt/service-props/" service-name "/" environment ".properties"))

;; TODO - will go away when we make things more consistent
(defn trim-number
  [service-name]
  "Name without the version number"
  (re-find #".+[^0-9]" service-name))

;; TODO - this is going to be replaced with a call to get this from tyranitar
(defn mv-service-properties
  "Copy over the service properties"
  [service-name]
  (let [service-name (trim-number service-name)]
   (shell (str "mkdir -p /usr/local/" service-name "/etc")
          (str "mv /tmp/dev.properties /usr/local/" service-name "/etc/dev.properties")
          (str "mv /tmp/prod.properties /usr/local/" service-name "/etc/prod.properties")
          (str "chkconfig " service-name " off"))))

(defn upload-service-properties
  [service-name environment]
  "Upload the properties files"
  {:type "file"
   :source (props-path service-name environment)
   :destination (str "/tmp/" environment ".properties")})

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
               :source_ami (base/entertainment-base-ami-id :ebs)
               :tags {:Name (service-ami-name service-name service-version) :Baking "Baking"}
               :temporary_key_pair_name "nokia2-{{uuid}}"
               :ssh_timeout "5m"
               :ssh_username "nokia2"
               :subnet_id "subnet-bdc08fd5"
               :type "amazon-ebs"
               :vpc_id "vpc-7bc88713"}]
   :provisioners [(motd service-name service-version)
                  (service-rpm service-name service-version)
                  (upload-service-properties service-name "dev")
                  (upload-service-properties service-name "prod")
                  (mv-service-properties service-name)]})

(defn create-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version]
  (json/generate-string (service-template service-name service-version)))
