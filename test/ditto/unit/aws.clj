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
                  (owned-images-by-name ..name..) => '({:Name "ent-exploud-0.9-1-2013-10-24_18-41-23"}
                                                       {:Name "ent-exploud-0.10-1-2013-10-24_18-41-23"}
                                                       {:Name "ent-exploud-0.11-1-2013-10-24_18-41-23"})
                  (provided
                   ;; Shame midje doesn't support varargs!
                   (aws anything anything anything anything anything
                        anything anything anything anything anything) =>
                        (json/generate-string
                         {:Images [{:Name "ent-exploud-0.10-1-2013-10-24_18-41-23"}
                                   {:Name "ent-exploud-0.9-1-2013-10-24_18-41-23"}
                                   {:Name "ent-exploud-0.11-1-2013-10-24_18-41-23"}]})))

            (fact "owned-images-by-name sorts by major version first"
                  (owned-images-by-name ..name..) => '({:Name "ent-exploud-0.1-1-2013-10-24_18-41-23"}
                                                       {:Name "ent-exploud-0.9-1-2013-10-24_18-41-23"}
                                                       {:Name "ent-exploud-0.14-1-2013-10-24_18-41-23"}
                                                       {:Name "ent-exploud-1.3-1-2013-10-24_18-41-23"})
                  (provided
                   (aws anything anything anything anything anything
                        anything anything anything anything anything) =>
                        (json/generate-string
                         {:Images [{:Name "ent-exploud-0.1-1-2013-10-24_18-41-23"}
                                   {:Name "ent-exploud-0.14-1-2013-10-24_18-41-23"}
                                   {:Name "ent-exploud-1.3-1-2013-10-24_18-41-23"}
                                   {:Name "ent-exploud-0.9-1-2013-10-24_18-41-23"}]})))

            (fact "owned-images-by-name sorts by date if all else is equal"
                  (owned-images-by-name ..name..) => '({:Name "ent-exploud-0.10-1-2012-10-24_18-41-23"}
                                                       {:Name "ent-exploud-0.10-1-2013-10-24_18-38-23"}
                                                       {:Name "ent-exploud-0.10-1-2013-10-24_18-41-23"})
                  (provided
                   (aws anything anything anything anything anything
                        anything anything anything anything anything) =>
                        (json/generate-string
                         {:Images [{:Name "ent-exploud-0.10-1-2013-10-24_18-38-23"}
                                   {:Name "ent-exploud-0.10-1-2012-10-24_18-41-23"}
                                   {:Name "ent-exploud-0.10-1-2013-10-24_18-41-23"}]})))

            (fact "owned-images-by-name sorts basic ami names alphabetically"
                  (owned-images-by-name ..name..) => '({:Name "entertainment-base-2012-10-24_00-00-03"}
                                                       {:Name "entertainment-base-2013-10-24_00-00-03"}
                                                       {:Name "entertainment-base-2013-10-25_00-00-03"})
                  (provided
                   (aws anything anything anything anything anything
                        anything anything anything anything anything) =>
                        (json/generate-string
                         {:Images [{:Name "entertainment-base-2013-10-24_00-00-03"}
                                   {:Name "entertainment-base-2012-10-24_00-00-03"}
                                   {:Name "entertainment-base-2013-10-25_00-00-03"}]}))))
