(ns ditto.entertainment-ami
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [clj-time.core :as time-core]
            [clj-time.format :as time-format]
            [clojure.java.io :as io]))

(defn shell [& cmds]
  "Accepts a series of strings to run as shell comands. Runs commands with -x shebang and
   with sudo."
  {:type "shell"
   :execute_command "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
   :inline_shebang "/bin/sh -x"
   :inline cmds})

(defn ent-ami-name
  "Returns the ami name for date/time now"
  []
  (str "entertainment-base-" (time-format/unparse
                              (time-format/formatter "YYYY-MM-dd_HH-mm-ss")
                              (time-core/now))))

(def ent-yum-repo
  "Set up the entertainment yum repo"
  (shell (str "echo \""
              (slurp (io/resource "nokia-internal.repo"))
              "\" >> /etc/yum.repos.d/nokia-internal.repo")
         "echo \"iam_role=1\" >> /etc/yum/pluginconf.d/nokia-s3yum.conf"
         "yum-config-manager --enable nokia-epel >> /var/log/baking.log 2>&1"))

(def puppet
  "Set up puppet and run once, blocking"
  (shell "export LD_LIBRARY_PATH=/opt/rh/ruby193/root/usr/lib64"
         "PUPPETD=\"PATH=/opt/rh/ruby193/root/usr/local/bin/:/opt/rh/ruby193/root/usr/bin/:/sbin:/usr/sbin:/bin:/usr/bin /opt/rh/ruby193/root/usr/local/bin/puppet\""
         "yum install -y puppet >> /var/log/baking.log 2>&1"
         "scl enable ruby193 ' /opt/rh/ruby193/root/usr/local/bin/puppet agent --onetime --no-daemonize --server puppetaws.brislabs.com'"
         "rm -rf /var/lib/puppet/ssl"))

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

(def cloud-final
  "run cloud final just before last, not last"
  (shell "chkconfig cloud-final off"
         "sudo sed -i \"s/# chkconfig:   - 99 01/# chkconfig:   - 98 01/\" /etc/rc.d/init.d/cloud-final"
         "chkconfig cloud-final on"))

(defn ami-template
  "Generate a new ami template"
  [parent-ami]
  {:builders [{:access_key (env :service-aws-access-key)
               :ami_name (ent-ami-name)
               :iam_instance_profile "baking"
               :instance_type "t1.micro"
               :region "eu-west-1"
               :secret_key (env :service-aws-secret-key)
               :security_group_id "sg-c453b4ab"
               :source_ami parent-ami
               :temporary_key_pair_name "nokia-{{uuid}}"
               :ssh_timeout "5m"
               :ssh_username "nokia"
               :subnet_id "subnet-bdc08fd5"
               :type "amazon-ebs"
               :vpc_id "vpc-7bc88713"}]
   :provisioners [(motd parent-ami)
                  ent-yum-repo
                  ruby-193
                  puppet
                  cloud-final]})

(defn create-base-ami
  "Creates a new entertainment base-ami from the parent ami id"
  [parent-ami]
  (json/generate-string (ami-template parent-ami)))
