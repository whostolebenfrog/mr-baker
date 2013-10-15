(ns ditto.unit.nokia
  (:require [ditto
             [nokia :refer :all]
             [aws :as aws]]
            [midje.sweet :refer :all]))

(fact-group :unit
            (fact "Latest-nokia-ami returns"
                  (latest-nokia-ami) => "latest-ami"
                  (provided (nokia-ami-name :ebs anything) => "ami-name"
                            (aws/private-images-by-name "ami-name") => [{:ImageId "latest-ami"}])))
