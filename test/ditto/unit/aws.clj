(ns ditto.unit.aws
  (:require [ditto.aws :refer :all]
            [midje.sweet :refer :all]))

(fact-group :unit
            (fact "allow-prod-access-to-service allows access for all amis"
                  (allow-prod-access-to-service ..name..) => [true true]
                  (provided
                   (service-images ..name..) => [{:ImageId ..ami1..}
                                                 {:ImageId ..ami2..}]
                   (allow-prod-access-to-ami ..ami1..) => true
                   (allow-prod-access-to-ami ..ami2..) => true
                   (allow-prod-access-to-ami anything) => false :times 0)))
