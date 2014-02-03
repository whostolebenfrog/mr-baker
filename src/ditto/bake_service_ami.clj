(ns ditto.bake-service-ami
  (:require [ditto
             [entertainment-ami :as base]
             [bake-common :refer :all]
             [onix :as onix]]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [clojure.string :as str]))

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

(defn rpm-full-name
  [service-name service-version rpm-name]
  (let [name (or rpm-name service-name)]
    (format "%s-%s.noarch.rpm" name service-version)))

(defn service-rpm
  "Install the service rpm on to the machine"
  [service-name service-version rpm-name]
  (let [rpm-full-name (rpm-full-name service-name service-version rpm-name)]
    (shell (str "wget http://yumrepo.brislabs.com/ovimusic/" rpm-full-name)
           (str "yum -y install " rpm-full-name)
           (str "rm -fv " rpm-full-name))))

(def numel-on
  "Switch on Numel integration"
  (shell "yum install -y numel-integration"))

(def puppet-on
  "Enable puppet once we're done"
  (shell "chkconfig puppet on"))

(defn custom-shell-commands
  "If the service defines custom shell commands "
  [service-name service-version]
  (when-let [commands (onix/shell-commands service-name)]
    (->> commands
         (map (fn [c] (str/replace c "{{version}}" service-version)))
         (apply shell))))

(def clear-var-log-messages
  "Clears /var/log/messages"
  (shell "cat /dev/null > /var/log/messages"))

(defn service-template
  "Generates a new ami template for the service"
  [service-name service-version rpm-name]
  (let [builder (maybe-with-keys
                 {:ami_name (service-ami-name service-name service-version)
                  :iam_instance_profile "baking"
                  :instance_type "t1.micro"
                  :region "eu-west-1"
                  :source_ami (base/entertainment-base-ami-id)
                  :temporary_key_pair_name "nokiarebake-{{uuid}}"
                  :ssh_timeout "5m"
                  :ssh_username "nokiarebake"
                  :subnet_id "subnet-bdc08fd5"
                  :type "amazon-ebs"
                  :vpc_id "vpc-7bc88713"})]
    {:builders [builder]
     :provisioners (filter
                    identity
                    [(motd service-name service-version)
                     (service-rpm service-name service-version rpm-name)
                     (custom-shell-commands service-name service-version)
                     clear-var-log-messages
                     numel-on
                     puppet-on])}))

(defn create-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version rpm-name]
  (json/generate-string (service-template service-name service-version rpm-name)))
