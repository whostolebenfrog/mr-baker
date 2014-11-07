(ns ditto.unit.entertainment-ami
  (:require [ditto
             [entertainment-ami :refer :all]
             [bake-common :refer :all]
             [nokia :as nokia]]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clj-time.core :as core-time]))

(fact-group :unit

  (fact "ent-ami-name returns the name including the time"
        (ent-ami-name :hvm) => "entertainment-base-al-hvm-2013-10-15_00-00-00"
        (provided
         (core-time/now) => (core-time/date-time 2013 10 15)))

  (fact "ebs template validates"
        (against-background
         (ent-ami-name :para) => ..ami-name..
         (motd ..parent-ami..) => ..motd..
         (instance-type-for-virt-type :para) => "t1.micro")

        (let [template (ebs-template ..parent-ami.. :para)
              {:keys [ami_name iam_instance_profile instance_type region
                      security_group_id source_ami temporary_key_pair_name
                      ssh_timeout ssh_username subnet_id type vpc_id]}
              (-> template :builders (first))
              provisioners (:provisioners template)]

          ami_name => ..ami-name..
          iam_instance_profile => "baking"
          instance_type => "t1.micro"
          region => "eu-west-1"
          security_group_id => (has-prefix "sg-")
          source_ami => ..parent-ami..
          ssh_timeout => "5m"
          ssh_username =>"ec2-user"
          subnet_id => (has-prefix "subnet-")
          type => (has-prefix "amazon-")
          vpc_id => (has-prefix "vpc")

          provisioners => (contains [..motd.. ent-yum-repo cloud-final
                                     puppet-clean puppet]
                                    :in-any-order :gaps-ok)))

  (fact "ebs template contains additional device mapping when hvm"
        (against-background
         (motd ..parent-ami..) => ..motd..)

        (let [template (ebs-template ..parent-ami.. :hvm)
              {:keys [ami_block_device_mappings]} (-> template :builders (first))]
          ami_block_device_mappings => (contains {:device_name "/dev/xvda"
                                                  :volume_size "10"
                                                  :delete_on_termination true})))

  (fact "create-base-ami returns a json string of the packer template"
        (create-base-ami ..virt-type..) => ..json..
        (provided
         (ebs-template ..parent-ami.. ..virt-type..) => ..packer-template..
         (json/generate-string ..packer-template..) => ..json..
         (nokia/latest-nokia-ami ..virt-type..) => ..parent-ami..)))
