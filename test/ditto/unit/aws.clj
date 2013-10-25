(ns ditto.unit.aws
  (:require [ditto.aws :refer :all]
            [cheshire.core :as json]
            [midje.sweet :refer :all]))

(fact-group :unit
            (fact "allow-prod-access-to-service allows access for all amis"
                  (allow-prod-access-to-service ..name..) => [true true]
                  (provided
                   (service-images ..name..) => [{:ImageId ..ami1..}
                                                 {:ImageId ..ami2..}]
                   (allow-prod-access-to-ami ..ami1..) => true
                   (allow-prod-access-to-ami ..ami2..) => true
                   (allow-prod-access-to-ami anything) => false :times 0))

            (fact "owned-images-by-name returns the list of images sorted numerically"
                  (owned-images-by-name ..name..) => '("ent-exploud-0.9-1-2013-10-24_18-41-23"
                                                       "ent-exploud-0.10-1-2013-10-24_18-41-23"
                                                       "ent-exploud-0.11-1-2013-10-24_18-41-23")
                  (provided
                   ;; Shame midje doesn't support varargs!
                   (aws anything anything anything anything anything
                        anything anything anything anything anything) =>
                        (json/generate-string
                         {:Images ["ent-exploud-0.10-1-2013-10-24_18-41-23"
                                   "ent-exploud-0.11-1-2013-10-24_18-41-23"
                                   "ent-exploud-0.9-1-2013-10-24_18-41-23"]}))))
