(ns baker.unit.builders.bake-public-ami
  (:require [baker
             [bake-common :refer :all]
             [amis :as amis]]
            [baker.builders
             [bake-public-ami :refer :all]]
            [midje.sweet :refer :all]
            [clj-time.core :as core-time]))

(fact-group
 :unit

 (fact "ami-name contains the name and time"
       (ami-name :hvm) => "entertainment-public-hvm-2013-10-15_00-00-00"
       (provided
        (core-time/now) => (core-time/date-time 2013 10 15)))

 (fact "public-ami generates a packer template"
       (against-background
        (amis/entertainment-base-ami-id ..virt-type..) => ..source..
        (instance-type-for-virt-type ..virt-type..) => "t1.micro")
       (let [template (public-ami ..source.. ..virt-type..)
             {:keys [ami_name iam_instance_profile instance_type region
                     security_group_id source_ami temporary_key_pair_name
                     ssh_timeout ssh_username subnet_id type vpc_id]}
             (-> template (:builders) (first))
             provisioners (:provisioners template)]

         ami_name => (ami-name ..virt-type..)
         iam_instance_profile => "baking"
         instance_type => "t1.micro"
         region => "eu-west-1"
         security_group_id => (has-prefix "sg-")
         source_ami => ..source..
         ssh_timeout => "5m"
         ssh_username => "ec2-user"
         subnet_id => (has-prefix "subnet-")
         type => (has-prefix "amazon")
         vpc_id => (has-prefix "vpc")))

 (fact "create-public-ami generates a json map of the public-ami template"
       (create-public-ami ..virt-type..) => ..json..
       (provided
        (public-ami ..source.. ..virt-type..) => ..json..
        (amis/entertainment-base-ami-id ..virt-type..) => ..source..)))
