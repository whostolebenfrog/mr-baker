(ns baker.unit.builders.web
  (:require [baker
             [amis :as amis]
             [onix :as onix]
             [packer :as packer]
             [web :refer :all]]
            [baker.builders
             [bake-example-template :as example]]
            [baker.unit.web :refer :all]
            [cheshire.core :as json]
            [midje.sweet :refer :all]))

;; TODO
(comment (fact-group
  :unit

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
        => (contains {:status 204}))))

(comment (fact-group
  :unit
  (fact "Passing dry-run to bake entertainment ami just returns the ami definition,
         it doesn't perform the task"
        (:body (request :post "bake/entertainment-ami/hvm" {:params {:dryrun true}})) => "\"ami-definition\""
        (provided (base/create-base-ami :hvm) => "ami-definition"))

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
                  (yum/get-latest-iteration "baker" "0.97" "bakerrpm") => "version"))))
