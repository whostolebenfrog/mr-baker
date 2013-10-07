(ns ditto.bake-service-ami
  (:require [ditto
             [entertainment-ami :as base]
             [bake-common :refer :all]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [clj-time
             [core :as time-core]
             [format :as time-format]]))

(def onix-base-url
  (env :service-onix-url))

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

(defn rpm-name
  [service-name service-version]
  (format "%s-%s.noarch.rpm" service-name service-version))

(defn rpm-url
  [service-name service-version]
  (str "http://yumrepo.brislabs.com/ovimusic/" (rpm-name service-name service-version)))

(defn service-rpm
  "Install the service rpm on to the machine"
  [service-name service-version]
  (let [rpm-name (rpm-name service-name service-version)]
    (shell (str "wget http://yumrepo.brislabs.com/ovimusic/" rpm-name)
           (str "yum -y install " rpm-name)
           (str "rm -fv " rpm-name))))

(defn props-path
  "Returnst the path of the properties file"
  [service-name environment]
  (str "/opt/service-props/" service-name "/" environment ".properties"))

(defn trim-number
  [service-name]
  "Name without the version number"
  (re-find #".+[^0-9]" service-name))

(def puppet-on
  "Enable puppet once we're done"
  (shell "chkconfig puppet on"))

(defn service-template
  "Generates a new ami template for the service"
  [service-name service-version]
  (let [builder (-> {:ami_name (service-ami-name service-name service-version)
                     :iam_instance_profile "baking"
                     :instance_type "t1.micro"
                     :region "eu-west-1"
                     :secret_key (env :service-aws-secret-key)
                     :source_ami (base/entertainment-base-ami-id)
                     :temporary_key_pair_name "nokiarebake-{{uuid}}"
                     :ssh_timeout "5m"
                     :ssh_username "nokiarebake"
                     :subnet_id "subnet-bdc08fd5"
                     :type "amazon-ebs"
                     :vpc_id "vpc-7bc88713"}
                    (maybe-with-keys))]
    {:builders [builder]
     :provisioners [(motd service-name service-version)
                    (service-rpm service-name service-version)
                    puppet-on]}))

(defn create-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version]
  (json/generate-string (service-template service-name service-version)))

(defn ami-exists?
  "Returns true if the ami exists in the brislabs yumrepo; otherwise returns false."
  [service-name service-version]
  (let [response (client/head (rpm-url service-name service-version) {:throw-exceptions false})
        status (:status response)]
    (= status 200)))

(defn service-exists?
  "Returns true if the service is known to onix; otherwise returns false."
  [service-name]
  (let [app-url (str onix-base-url "/1.x/applications/" service-name)
        response (client/get app-url {:throw-exceptions false})
        status (:status response)]
    (= status 200)))
