(ns ditto.entertainment-ami
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]))

(defn shell [& cmds]
  {:type "shell"
   :execute_command "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
   :inline_shebang "/bin/sh -x"
   :inline cmds})

;; TODO - alphabetize
;; TODO - ami name, with timestamp, follow pattern?
;; TODO - fn to return the name of the lastest ent ami for use with service baking
(defn ebs-builder
  "Generate a new ami builder"
  [parent-ami]
  {:type "amazon-ebs"
   :access_key (env :service-aws-access-key)
   :secret_key (env :service-aws-secret-key)
   :region "eu-west-1"
   :source_ami parent-ami
   :instance_type "t1.micro"
   :ssh_username "nokia"
   :ami_name "ditto-ami-testing {{timestamp}}"
   :ssh_timeout "5m"
   :security_group_id "sg-c453b4ab"
   :vpc_id "vpc-7bc88713"
   :subnet_id "subnet-bdc08fd5"
   :iam_instance_profile "baking"
   :ssh_keypair_pattern "nokia-%s"})

(def upload-repo-file
  {:type "file"
   :source "ami-scripts/nokia-internal.repo"
   :destination "/tmp/nokia-internal.repo"})

(def append-repo-file
  (shell "cat /tmp/nokia-internal.repo >> /etc/yum.repos.d/nokia-internal.repo"
         "echo \"iam_role=1\" >> /etc/yum/pluginconf.d/nokia-s3yum.conf"))

(def enable-nokia-repo
  (shell "yum-config-manager --enable nokia-epel >> /var/log/baking.log 2>&1"))

;; TODO - "echo PUPPET_SERVER=puppetaws.brislabs.com >> /etc/sysconfig/puppet"
  ;; this needs to go in the service baking stuff
(def puppet
  (shell "export LD_LIBRARY_PATH=/opt/rh/ruby193/root/usr/lib64"
         "PUPPETD=\"PATH=/opt/rh/ruby193/root/usr/local/bin/:/opt/rh/ruby193/root/usr/bin/:/sbin:/usr/sbin:/bin:/usr/bin /opt/rh/ruby193/root/usr/local/bin/puppet\""
         "yum install -y puppet >> /var/log/baking.log 2>&1"
         "scl enable ruby193 ' /opt/rh/ruby193/root/usr/local/bin/puppet agent --onetime --no-daemonize --server puppetaws.brislabs.com'"
         "rm -rf /var/lib/puppet/ssl"))

(def ruby-193
  (shell "yum install -y ruby193"
         "yum install -y ruby193-rubygem-puppet"
         "yum install -y ruby193-rubygem-ruby-shadow"))

;; TODO - add the time
(defn motd [parent-ami]
  (shell "echo -e \"\\nEntertainment Base AMI\" >> /etc/motd"
         "echo -e \"\\nBake date : TODO\" >> /etc/motd"
         (format "echo -e \"\\nSource AMI: %s\\n\" >> /etc/motd" parent-ami)))

;; TODO - make the repo steps one part?
(defn ami-template
  "Generate a new ami template"
  [parent-ami]
  {:builders [(ebs-builder parent-ami)]
   :provisioners [(motd parent-ami)
                  upload-repo-file
                  append-repo-file
                  enable-nokia-repo
                  ruby-193
                  puppet]})

(defn create-base-ami
  "Creates a new entertainment base-ami from the parent ami id"
  [parent-ami]
  (json/generate-string (ami-template parent-ami)))
