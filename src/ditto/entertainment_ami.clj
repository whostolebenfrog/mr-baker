(ns ditto.entertainment-ami
  (:require [ditto
             [bake-common :refer :all]
             [aws :as aws]
             [nokia :as nokia]]
            [clojure.tools.logging :refer [info warn error]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [clojure.java.io :as io]))

(defn ent-ami-name
  "Returns the ami name for date/time now"
  [virt-type]
  (str (nokia/ent-ami-name-base virt-type)
       (time-format/unparse (time-format/formatter "YYYY-MM-dd_HH-mm-ss") (time-core/now))))

(def ent-yum-repo
  "Set up the entertainment yum repo"
  (shell (str "echo \""
              (slurp (io/resource "nokia-internal.repo"))
              "\" >> /etc/yum.repos.d/nokia-internal.repo")
         "echo \"iam_role=1\" >> /etc/yum/pluginconf.d/nokia-s3yum.conf"
         "yum-config-manager --enable nokia-epel >> /var/log/baking.log 2>&1"))

(def puppet
  "Set up puppet and run once, blocking

   We also need to do all our cleanup in this step as we don't have root after this has run!
   Due to puppet setting up the various auth concerns."
  (shell "mkdir -p /var/lock/linux_stats"
         "touch /var/lock/linux_stats/lock"
         "export LD_LIBRARY_PATH=/opt/rh/ruby193/root/usr/lib64"
         "PUPPETD=\"PATH=/opt/rh/ruby193/root/usr/local/bin/:/opt/rh/ruby193/root/usr/bin/:/sbin:/usr/sbin:/bin:/usr/bin /opt/rh/ruby193/root/usr/local/bin/puppet\""
         "yum install -y puppet >> /var/log/baking.log 2>&1"
         "scl enable ruby193 ' /opt/rh/ruby193/root/usr/local/bin/puppet agent --onetime --no-daemonize --server puppetaws.brislabs.com'"
         "rm -rf /var/lib/puppet/ssl"
         "echo \"nokiarebake 	ALL=(ALL)	NOPASSWD: ALL\" >> /etc/sudoers"
         "rm /tmp/script.sh"
         "rm -f /etc/cron.d/linuxstats"
         "rm -f /var/lock/linux_stats/lock"))

(def ruby-193
  "Install ruby-193 - required to run puppet faster"
  (shell "yum install -y ruby193"
         "yum install -y ruby193-rubygem-puppet"
         "yum install -y ruby193-rubygem-ruby-shadow"))

(defn motd [parent-ami]
  "Set the message of the day"
  (shell "echo -e \"\\nEntertainment Base AMI\" >> /etc/motd"
         (format "echo -e \"\\nBake date: %s\" >> /etc/motd"
                 (time-format/unparse (time-format/formatters :date-time-no-ms) (time-core/now)))
         (format "echo -e \"\\nSource AMI: %s\\n\" >> /etc/motd" parent-ami)))

(def encrypted-sector
  "Install the one time mount encrypted sector"
  (shell "yum install -y otm"))

(def chroot-killer
  "Install the one time mount encrypted sector"
  (shell "yum install -y chrootkiller"))

(def jq
  "Install the one time mount encrypted sector"
  (shell "yum install -y jq"))

(def cloud-final
  "Make sure cloud-final runs as early as possible, but not after the services"
  (shell "chkconfig cloud-final off"
         "sudo sed -i \"s/# chkconfig:   - 99 01/# chkconfig:   - 96 01/\" /etc/rc.d/init.d/cloud-final"
         "chkconfig cloud-final on"))

(def user-cleanup
  "Cleanup the nokia baking user and reset the lock file so that a new one is created on next bake"
  (shell "rm /var/lib/nokia-tools/init.lock"))

(def dhcp-retry
  "Set's the DHCP to retry as centos sets a low timeout and sometimes amazon takes too long. This means the box fails to start."
  (shell "echo PERSISTENT_DHCLIENT=yes >> /etc/sysconfig/network-scripts/ifcfg-eth0"))

(def puppet-clean
  "Ensure that puppet holds no record for this IP (hostname). Due to the recycling of IPs
   we need to clean puppet for the IP we are currently using on startup."
  (shell "yum install -y facter"
         "mkdir -p /opt/puppet-clean/.ssh"
         (str "echo \""
              (slurp (io/resource "janitor_rsa"))
              "\" > /opt/puppet-clean/.ssh/janitor_rsa")
         (str "echo \""
              (slurp (io/resource "puppet_clean_host"))
              "\" > /etc/init.d/puppet_clean_host")
         "chmod +x /etc/init.d/puppet_clean_host"
         "chmod 600 /opt/puppet-clean/.ssh/janitor_rsa"
         "chkconfig --add puppet_clean_host"
         "/etc/init.d/puppet_clean_host"))

(def yum-clean-all
  "Cleans yum's various caches"
  (shell "yum clean all"))

(defn ebs-template
  "Generate a new ami ebs backed packer builder template"
  [parent-ami virt-type]
  (let [builder (maybe-with-keys
                 {:ami_name (ent-ami-name virt-type)
                  :ami_block_device_mappings (concat
                                              (when (= virt-type :hvm)
                                                [{:device_name "/dev/xvda"
                                                   :delete_on_termination true
                                                   :volume_size "10"}])
                                              [{:device_name "/dev/sdb"
                                                :virtual_name "ephemeral0"}
                                               {:device_name "/dev/sdc"
                                                :virtual_name "ephemeral1"}
                                               {:device_name "/dev/sdd"
                                                :virtual_name "ephemeral2"}
                                               {:device_name "/dev/sde"
                                                :virtual_name "ephemeral3"}])
                  :iam_instance_profile "baking"
                  :instance_type (instance-type-for-virt-type virt-type)
                  :region "eu-west-1"
                  :run_tags {:name "Base AMI Bake"
                             :owner "ditto"
                             :description "Temp instance used to bake the base ent ami"}
                  :tags {:name "Entertainment Base AMI"
                         :service "base"}
                  :security_group_id "sg-c453b4ab"
                  :source_ami parent-ami
                  :ssh_timeout "5m"
                  :ssh_username "nokia"
                  :subnet_id "subnet-bdc08fd5"
                  :temporary_key_pair_name "nokia-{{uuid}}"
                  :type "amazon-ebs"
                  :vpc_id "vpc-7bc88713"})]
    {:builders [builder]
     :provisioners [(motd parent-ami)
                    ent-yum-repo
                    yum-clean-all
                    ruby-193
                    encrypted-sector
                    chroot-killer
                    cloud-final
                    jq
                    user-cleanup
                    dhcp-retry
                    puppet-clean
                    puppet]}))

(defn create-base-ami
  "Creates a new entertainment base-ami from the parent ami id"
  [virt-type]
  (let [parent-ami (nokia/latest-nokia-ami virt-type)]
    (info (format "Creating base ami definition from nokia parent: %s and Type: %s" parent-ami virt-type))
    (json/generate-string (ebs-template parent-ami virt-type))))
