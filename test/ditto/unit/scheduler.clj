(ns ditto.unit.scheduler
  (:require [ditto
             [scheduler :refer :all]
             [onix :as onix]]
            [midje.sweet :refer :all]))

(fact-group :unit
            (fact "kill-amis kills amis for all onix applications"
                  (kill-amis) => truthy
                  (provided
                   (kill-amis-for-application ..app1..) => nil :times 1
                   (kill-amis-for-application ..app2..) => nil :times 1
                   (onix/get-applications) => [..app1.. ..app2..])))
