(ns baker.unit.amis
  (:require [baker
             [awsclient :as awsclient]
             [amis :refer :all]]
            [midje.sweet :refer :all]))

(fact-group
 :unit

 (fact "base-ami-id returns the latest ami from aws"
       (base-ami-id :hvm) => ..latest..
       (provided
        (awsclient/owned-amis-by-name anything) => [..oldest.. ..old.. {:image-id ..latest..}]))

 (fact "public-ami-id returns the latest public ami"
       (public-ami-id ..virt-type..) => ..public..
       (provided
        (awsclient/owned-amis-by-name anything) => [..old.. {:image-id ..public..}])))
