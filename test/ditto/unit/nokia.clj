(ns ditto.unit.nokia
  (:require [ditto
             [nokia :refer :all]
             [aws :as aws]]
            [midje.sweet :refer :all]))

(fact-group :unit
  (fact "Latest-nokia-ami returns"
        (latest-nokia-ami) => "ent-ditto-0.25-1-2013-10-15_10-21-19"
        (provided (nokia-ami-name :ebs anything) => "ent-ditto-0.25-1-2013-10-15_10-21-19"
                  (aws/private-images-by-name anything) => [{:ImageId "ent-ditto-0.25-1-2013-10-15_10-21-19"}])))