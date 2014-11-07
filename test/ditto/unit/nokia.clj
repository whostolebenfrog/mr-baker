(ns ditto.unit.nokia
  (:require [ditto
             [awsclient :as awsclient]
             [nokia :refer :all]]
            [midje.sweet :refer :all]))

(fact-group
 :unit

 (fact "entertainment-base-ami-id returns the latest ami from aws"
       (entertainment-base-ami-id :hvm) => ..latest..
       (provided
        (awsclient/owned-images-by-name anything) => [..oldest.. ..old.. {:image-id ..latest..}]))

 (fact "entertainment-public-ami-id returns the latest public ami"
       (entertainment-public-ami-id ..virt-type..) => ..public..
       (provided
        (awsclient/owned-images-by-name anything) => [..old.. {:image-id ..public..}])))
