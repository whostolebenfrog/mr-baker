(ns baker.unit.amis
  (:require [baker
             [awsclient :as awsclient]
             [amis :refer :all]]
            [midje.sweet :refer :all]))

(fact-group
 :unit

 (fact "entertainment-base-ami-id returns the latest ami from aws"
       (entertainment-base-ami-id :hvm) => ..latest..
       (provided
        (awsclient/owned-amis-by-name anything) => [..oldest.. ..old.. {:image-id ..latest..}]))

 (fact "entertainment-public-ami-id returns the latest public ami"
       (entertainment-public-ami-id ..virt-type..) => ..public..
       (provided
        (awsclient/owned-amis-by-name anything) => [..old.. {:image-id ..public..}])))
