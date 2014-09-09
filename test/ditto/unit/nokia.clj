(ns ditto.unit.nokia
  (:require [ditto
             [nokia :refer :all]
             [aws :as aws]]
            [midje.sweet :refer :all]))

(fact-group
 :unit

 (future-fact "Remove this if we stick with amazon linux or put back"
       (latest-nokia-ami :para) => "ent-ditto-0.25-1-2013-10-15_10-21-19"
       (provided (nokia-ami-name "ebs" anything) => "ent-ditto-0.25-1-2013-10-15_10-21-19"
                 (aws/private-images-by-name anything) => [{:ImageId "ent-ditto-0.25-1-2013-10-15_10-21-19"}]))

 (fact "entertainment-base-ami-id returns the latest ami from aws"
       (entertainment-base-ami-id :hvm) => ..latest..
       (provided
        (aws/owned-images-by-name anything) => [..oldest.. ..old.. {:ImageId ..latest..}]))

 (fact "entertainment-public-ami-id returns the latest public ami"
       (entertainment-public-ami-id ..virt-type..) => ..public..
       (provided
        (aws/owned-images-by-name anything) => [..old.. {:ImageId ..public..}])))
