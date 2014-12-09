(ns ditto.entertainment-ami
  (:require [ditto
             [bake-common :refer :all]
             [nokia :as nokia]]
            [clojure.tools.logging :refer [info warn error]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-time
             [core :as time-core]
             [format :as time-format]]
            [clojure.java.io :as io]
            [clojure.data.codec.base64 :as b64]))

(defn ent-ami-name
  "Returns the ami name for date/time now"
  [virt-type]
  (str (nokia/ent-ami-name-base virt-type)
       (time-format/unparse (time-format/formatter "YYYY-MM-dd_HH-mm-ss") (time-core/now))))

(def ent-yum-repo
  "Set up the entertainment yum repo"
  (shell (str "echo \""
              (slurp (io/resource "nokia-internal.repo"))
              "\" >> /etc/yum.repos.d/nokia-internal.repo")))

(def puppetlabs-repo
  "Set up the entertainment yum repo"
  (shell (str "echo \""
              (slurp (io/resource "puppetlabs.repo"))
              "\" >> /etc/yum.repos.d/puppetlabs.repo")))

(def puppetlabs-deps-repo
  "Set up the entertainment yum repo"
  (shell (str "echo \""
              (slurp (io/resource "puppetlabsdeps.repo"))
              "\" >> /etc/yum.repos.d/puppetlabsdeps.repo")))

(def puppet
  "Set up puppet and run once, blocking

   We also need to do all our cleanup in this step as we don't have root after this has run!
   Due to puppet setting up the various auth concerns."
  (shell "yum install -y puppet-3.2.4"
         "puppet agent --onetime --no-daemonize --server puppetaws.brislabs.com"
         "rm -rf /var/lib/puppet/ssl"))

(defn motd [parent-ami]
  "Set the message of the day"
  (shell "echo -e \"\\nEntertainment Base AMI\" >> /etc/motd"
         (format "echo -e \"\\nBake date: %s\" >> /etc/motd"
                 (time-format/unparse (time-format/formatters :date-time-no-ms) (time-core/now)))
         (format "echo -e \"\\nSource AMI: %s\\n\" >> /etc/motd" parent-ami)))

(def encrypted-sector
  "Install the one time mount encrypted sector"
  (shell "yum install -y otm"))

(def cloud-final
  "Make sure cloud-final runs as early as possible, but not after the services"
  (shell "chkconfig cloud-final off"
         "sed -i \"s/# chkconfig:   2345 98 50/# chkconfig:   2345 96 50/\" /etc/rc.d/init.d/cloud-final"
         "chkconfig cloud-final on"))

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

(def s3iam
  "Set up s3iam authentication plugin for yum"
  (shell (str "echo \""
              (-> "s3iam.py" io/resource slurp .getBytes b64/encode String.)
              "\" | base64 --decode >> /usr/lib/yum-plugins/s3iam.py")
         (str "echo \""
              (-> "s3iam.conf" io/resource slurp .getBytes b64/encode String.)
              "\" | base64 --decode >> /etc/yum/pluginconf.d/s3iam.conf")))

(def install-patches
  "Yum update with just the amazon linux repos. This applies security and other updates for us."
  (shell "yum update -y"))

(def encfs
  "Install encfs from the amazon linux extended repos. Leaving this disabled for now and installing
   this as a one-off. Maybe we could consider leaving them enabled. It's probably safe to do so as
   yum update should only be run at the start of this base image bake, before the extended repos
   are added. One to think about."
  (shell "yum install --enablerepo=epel -y fuse-encfs"))

(def utils
  "Install some utils to share the joy amongst all boxes"
  (shell "yum install htop jq iftop ack-grep -y"))

(def gem-prerequisites
  "These two are required for some of the ruby gems to work. Gems being gems it won't install them."
  (shell "yum install gcc ruby-devel -y"))

(def ruby-gems
  "Install some ruby gems that we need for puppet"
  (shell "gem install ruby-shadow json"
         "gem install puppet --version 3.2.4"))

(def fix-pam-ldap
  "Creates a directory required by PAM LDAP because it's bad and references mental paths like
   /lib/security/../../lib64/security :-/"
  (shell "mkdir -p /lib/security"))

(def lock-puppet-ssh-auth
  "Creates a lock file that suppresses puppet from running its ssh auth module"
  (shell "mkdir -p /var/lock/ditto"
         "touch /var/lock/ditto/ssh"))

(def lock-puppet-sensu
  "Creates a lock file that suppresses puppet from running its sensu module"
  (shell "mkdir -p /var/lock/ditto"
         "touch /var/lock/ditto/sensu"))

(def python-pip
  "Removes existing python if installed, block amzn-main repo's version and install our own. This
   stops puppet from being an ass where it looks for python-pip and finds python26-pip installed.
   Despite them both actually being the same"
  (shell "yum remove -y python-pip"
         "sed -i '/report_instanceid=yes/a exclude=python26-pip' /etc/yum.repos.d/amzn-main.repo"
         "yum install -y python-pip"))

(defn provisioners
  [parent-ami]
  [(motd parent-ami)
   install-patches
   ent-yum-repo
   puppetlabs-repo
   puppetlabs-deps-repo
   s3iam
   yum-clean-all
   encfs
   utils
   encrypted-sector
   gem-prerequisites
   ruby-gems
   cloud-final
   fix-pam-ldap
   python-pip
   lock-puppet-ssh-auth
   lock-puppet-sensu
   puppet-clean
   puppet])

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
                                              [])
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
                  :ssh_username "ec2-user"
                  :subnet_id "subnet-bdc08fd5"
                  :type "amazon-ebs"
                  :vpc_id "vpc-7bc88713"})]
    {:builders [builder]
     :provisioners (provisioners parent-ami)}))

(defn create-base-ami
  "Creates a new entertainment base-ami from the parent ami id"
  [virt-type]
  (let [parent-ami (nokia/latest-nokia-ami virt-type)]
    (info (format "Creating base ami definition from nokia parent: %s and Type: %s" parent-ami virt-type))
    (json/generate-string (ebs-template parent-ami virt-type))))
