(ns ditto.bake-service-ami
  (:require [ditto
             [entertainment-ami :as base]
             [bake-common :refer :all]
             [nokia :as nokia]
             [onix :as onix]]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [clojure.string :as str]))

(defn service-ami-name
  "Returns the ami name for the service with date/time now"
  [service-name service-version virt-type]
  (str "ent-" service-name "-"
       service-version "-"
       (name virt-type) "-"
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

(def kill-chroot-prosses
  "Kill all processes in the chroot"
  (shell "/opt/chrootkiller"))

(defn custom-shell-commands
  "If the service defines custom shell commands "
  [service-name service-version]
  (when-let [commands (onix/shell-commands service-name)]
    (let [version (first (str/split service-version #"-" 2))]
      (->> commands
           (map (fn [c] (str/replace c "{{version}}" version)))
           (apply shell)))))

(def clear-var-log-messages
  "Clears /var/log/messages"
  (shell "cat /dev/null > /var/log/messages"))

(def yum-clean-all
  "Cleans yum's various caches"
  (shell "yum clean all"))

(defn provisioners
  "Returns a list of provisioners for the bake."
  [service-name service-version rpm-name]
  ( ->> [(motd service-name service-version)
         yum-clean-all
         (service-rpm service-name service-version rpm-name)
         (custom-shell-commands service-name service-version)
         clear-var-log-messages
         numel-on
         puppet-on]
        (filter identity)))

(defn service-template
  "Generates a new ami template for the service"
  [service-name service-version rpm-name source-ami virt-type]
  (let [builder (maybe-with-keys
                 {:ami_name (service-ami-name service-name service-version virt-type)
                  :iam_instance_profile "baking"
                  :instance_type (instance-type-for-virt-type virt-type)
                  :region "eu-west-1"
                  :run_tags {:name (format "%s AMI Bake" service-name)
                             :owner "ditto"
                             :description (format "Temp instance used to bake the %s ent ami" service-name)}
                  :tags {:name (format "%s AMI" service-name)
                         :service service-name}
                  :security_group_id "sg-c453b4ab"
                  :source_ami source-ami
                  :ssh_timeout "5m"
                  :ssh_username "nokiarebake"
                  :subnet_id (rand-nth ["subnet-bdc08fd5" "subnet-24df904c" "subnet-e6e4e0a0"])
                  :type "amazon-ebs"
                  :temporary_key_pair_name "nokiarebake-{{uuid}}"
                  :vpc_id "vpc-7bc88713"})]
    {:builders [builder]
     :provisioners (provisioners service-name service-version rpm-name)}))

(defn chroot-service-template
  "Generates a new ami template for chroot bake of the service"
  [service-name service-version rpm-name source-ami virt-type]
  (let [builder (maybe-with-keys
                 {:ami_name (service-ami-name service-name service-version virt-type)
                  :tags {:name (format "%s AMI" service-name)
                         :service service-name}
                  :source_ami source-ami
                  :type "amazon-chroot"})]
    {:builders [builder]
     :provisioners (concat (provisioners service-name service-version rpm-name) [kill-chroot-prosses])}))

(defn create-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version rpm-name virt-type]
  (json/generate-string (service-template service-name service-version rpm-name
                                          (nokia/entertainment-base-ami-id virt-type)
                                          virt-type)))

(defn create-chroot-service-ami
  "Creates a new ami for the supplied service and vesion"
  [service-name service-version rpm-name virt-type]
  (json/generate-string (chroot-service-template service-name service-version rpm-name
                                                 (nokia/entertainment-base-ami-id virt-type)
                                                 virt-type)))
