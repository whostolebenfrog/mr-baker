(ns baker.unit.builders.web
  (:require [baker
             [amis :as amis]
             [onix :as onix]
             [packer :as packer]
             [web :refer :all]]
            [baker.builders
             [yum :as yum]
             [bake-service-ami :as service-ami]
             [bake-public-ami :as public-ami]
             [bake-base-ami :as base]]
            [baker.unit.web :refer :all]
            [cheshire.core :as json]
            [midje.sweet :refer :all]))

(fact-group
 :unit

 (fact "Service must exist to be baked"
       (request :post "bake/serv/0.13") => (contains {:status 404})
       (provided (onix/service-exists? "serv") => false))

 (fact "Service rpm must exist to be baked"
       (request :post "bake/serv/0.13") => (contains {:status 404})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => nil))

 (fact "Bake service gets the latest iteration"
       (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :para nil) => ..template..
                 (packer/build ..template.. "serv") => "template"))

 (fact "Bake service accepts embargo tags"
       (request :post "bake/serv/0.13" {:params {:embargo "prod"}}) => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :para "prod") => ..template..
                 (packer/build ..template.. "serv") => "template"))

(fact "Bake service passes the virt type if supplied"
       (request :post "bake/serv/0.13" {:params {:virt-type "hvm"}}) => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :hvm nil) => ..template..
                 (packer/build ..template.. "serv") => "template"))

 (fact "Bake service attempts to get an overridden RPM name from Onix"
       (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => "other"
                 (yum/get-latest-iteration "serv" "0.13" "other") => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" "other" :para nil) => ..template..
                 (packer/build ..template.. "serv") => "template"))

 (fact "Service returns 503 if baker is locked"
       (request :post "lock") => (contains {:status 200 :body (contains "no reason was supplied")})
       (request :post "bake/serv/0.13") => (contains {:status 503})
       (request :delete "lock") => (contains {:status 204})
       (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true :times 1
                 (onix/rpm-name "serv") =>  ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1" :times 1
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :para nil) => ..template.. :times 1
                 (packer/build ..template.. "serv") => "template" :times 1))

 (fact "Service can be locked with a message"
       (request :post "lock" {:body {:message "locky lock"}
                              :content-type "application/json"})
       => (contains {:status 200 :body (contains "locky lock")})
       (request :post "bake/serv/0.13")
       => (contains {:body (contains "locky lock")})
       (request :delete "lock")
       => (contains {:status 204})))

(fact-group
 :unit
 (fact "Passing dry-run to bake entertainment ami just returns the ami definition,
         it doesn't perform the task"
       (:body (request :post "bake/entertainment-ami/hvm" {:params {:dryrun true}})) => "\"ami-definition\""
       (provided (base/create-base-ami :hvm) => "ami-definition"))

 (fact "Calling bake entertainment bakes a new entertainment ami"
       (:body (request :post "bake/entertainment-ami/para")) => "packer-response"
       (provided (base/create-base-ami :para) => "ami-definition"
                 (packer/build "ami-definition") => "packer-response"))

 (fact "Calling bake public bakes a new public ami"
       (:body (request :post "bake/public-ami/hvm")) => "packer-response"
       (provided (public-ami/create-public-ami :hvm) => "ami-definition"
                 (packer/build "ami-definition") => "packer-response"))

 (fact "Baking an entertainment-ami generates a real template"
       (-> (request :post "bake/entertainment-ami/hvm" {:params {:dryrun true}})
           :body
           (json/parse-string true)
           :builders
           first
           :ami_name) => (contains "hvm")
       (provided
        (amis/parent-ami anything) => "base-ami-id"))

 (fact "Baking a service generates a real template"
       (-> (request :post "bake/baker/0.97" {:params {:dryrun true}})
           :body
           (json/parse-string true)
           :builders) => vector?
           (provided (amis/base-ami-id anything) => "base-ami-id"
                     (onix/service-exists? "baker") => true
                     (onix/rpm-name "baker") => "bakerrpm"
                     (yum/get-latest-iteration "baker" "0.97" "bakerrpm") => "version"))

 (fact "Baking a service with the virt-type param switches the virtualisation type"
       (-> (request :post "bake/baker/0.97" {:params {:dryrun true :virt-type "hvm"}})
           :body
           (json/parse-string true)
           :builders
           first) => (contains {:source_ami "base-ami-id-hvm" :ami_name (contains "hvm")})
           (provided (amis/base-ami-id :hvm) => "base-ami-id-hvm"
                     (onix/service-exists? "baker") => true
                     (onix/rpm-name "baker") => "bakerrpm"
                     (yum/get-latest-iteration "baker" "0.97" "bakerrpm") => "version")))
