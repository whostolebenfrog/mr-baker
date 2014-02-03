(ns ditto.unit.bake-service-ami
  (:require [ditto
             [bake-service-ami :refer :all]
             [bake-common :refer :all]
             [entertainment-ami :as base]
             [onix :as onix]]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clj-time.core :as core-time]))

(fact-group :unit
  (fact "service-ami-name returns the service name with the date"
        (service-ami-name "name" "version") => "ent-name-version-2013-10-15_00-00-00"
        (provided
         (core-time/now) => (core-time/date-time 2013 10 15)))

  (fact "rpm-full-name returns the rpm name"
        (rpm-full-name "name" "version" nil) => "name-version.noarch.rpm")

  (fact "rpm-full-name allows overriding of the default rpm name"
        (rpm-full-name "name" "version" "rpm") => "rpm-version.noarch.rpm")

  (fact "service-rpm installs the service"
        (let [name (rpm-full-name "name" "version" nil)
              {:keys [type inline]} (service-rpm "name" "version" nil)]

          type => "shell"
          (first inline) => (has-prefix "wget")
          (first inline) => (has-suffix name)
          (second inline) => (has-prefix "yum -y install")
          (second inline) => (has-suffix name)))

  (fact "puppet on enables puppet"
        (let [{:keys [type inline]} puppet-on]

          type => "shell"
          (first inline) => "chkconfig puppet on"))

  (fact "custom-shell-commands calls onix and uses that result"
        (against-background
         (onix/shell-commands "app") => ["echo woo" "do something"])
        (let [{:keys [type inline]} (custom-shell-commands "app" "1.1")]
          type => "shell"
          (first inline) => "echo woo"
          (second inline) => "do something"))

  (fact "custom-shell-commands replaces `{{version}}` in any script"
        (against-background
         (onix/shell-commands "app") => ["echo {{version}}"])
        (let [{:keys [inline]} (custom-shell-commands "app" "1.2.3")]
          (first inline) => "echo 1.2.3"))

  (fact "packer template validates"
        (against-background
         (service-ami-name ..name.. ..version..) => ..ami-name..
         (base/entertainment-base-ami-id) => ..base-ami..)
        (let [template (service-template ..name.. ..version.. ..rpm..)
              {:keys [ami_name iam_instance_profile instance_type region
                      secret_key source_ami temporary_key_pair_name ssh_timeout
                      ssh_username subnet_id type vpc_id]}
              (-> template :builders (first))
              provisioners (:provisioners template)]

          ami_name => ..ami-name..
          iam_instance_profile => "baking"
          instance_type => "t1.micro"
          region  => "eu-west-1"
          source_ami => ..base-ami..
          temporary_key_pair_name => "nokiarebake-{{uuid}}"
          ssh_timeout => "5m"
          ssh_username => "nokiarebake"
          subnet_id => (has-prefix "subnet")
          type => (has-prefix "amazon")
          vpc_id => (has-prefix "vpc")))

  (fact "create-service-ami returns a json string of the packer template"
        (create-service-ami ..name.. ..version.. ..rpm..) => ..json..
        (provided
         (service-template ..name.. ..version.. ..rpm..) => ..template..
         (json/generate-string ..template..) => ..json..)))
