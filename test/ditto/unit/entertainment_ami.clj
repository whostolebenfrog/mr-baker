(ns ditto.unit.entertainment-ami
  (:require [ditto
             [entertainment-ami :refer :all]
             [bake-common :refer :all]
             [aws :as aws]
             [nokia :as nokia]]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clj-time.core :as core-time]))

(fact-group :unit

  (fact "ent-ami-name returns the name including the time"
        (ent-ami-name :hvm) => "entertainment-base-hvm-2013-10-15_00-00-00"
        (provided
         (core-time/now) => (core-time/date-time 2013 10 15)))

  (fact "ebs template validates"
        (against-background
         (ent-ami-name ..virt-type..) => ..ami-name..
         (motd ..parent-ami..) => ..motd..)

        (let [template (ebs-template ..parent-ami.. ..virt-type..)
              {:keys [ami_name iam_instance_profile instance_type region
                      security_group_id source_ami temporary_key_pair_name
                      ssh_timeout ssh_username subnet_id type vpc_id] :as x}
              (-> template :builders (first))
              provisioners (:provisioners template)]

          ami_name => ..ami-name..
          iam_instance_profile => "baking"
          instance_type => "t1.micro"
          region => "eu-west-1"
          security_group_id => (has-prefix "sg-")
          source_ami => ..parent-ami..
          temporary_key_pair_name => "nokia-{{uuid}}"
          ssh_timeout => "5m"
          ssh_username =>"nokia"
          subnet_id => (has-prefix "subnet-")
          type => (has-prefix "amazon-")
          vpc_id => (has-prefix "vpc")

          provisioners => (contains [ruby-193 ..motd.. ent-yum-repo cloud-final
                                     user-cleanup puppet-clean puppet]
                                    :in-any-order :gaps-ok)))

  (fact "create-base-ami returns a json string of the packer template"
        (create-base-ami ..virt-type..) => ..json..
        (provided
         (ebs-template ..parent-ami.. ..virt-type..) => ..packer-template..
         (json/generate-string ..packer-template..) => ..json..
         (nokia/entertainment-base-ami-id ..virt-type..) => ..parent-ami..)))
