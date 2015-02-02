(ns baker.unit.builders.bake-example-template
  (:require [baker
             [bake-common :refer :all]
             [amis :as amis]
             [onix :as onix]]
            [baker.builders
             [bake-example-template :refer :all]]
            [midje.sweet :refer :all]
            [clj-time.core :as core-time]))

(fact-group :unit
  (fact "service-ami-name returns the service name with the date"
        (service-ami-name "name" "version" "para") => "ent-name-version-para-2013-10-15_00-00-00"
        (provided
         (core-time/now) => (core-time/date-time 2013 10 15))))
