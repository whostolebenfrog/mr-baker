(ns baker.unit.yum
  (:use baker.yum)
  (:require [midje.sweet :refer :all]
            [clj-http.client :as client]))

(fact-group
 :unit

 (fact "rpm-full-name returns the rpm name"
       (rpm-full-name "name" "version" nil) => "name-version.noarch.rpm")

 (fact "rpm-full-name allows overriding of the default rpm name"
       (rpm-full-name "name" "version" "rpm") => "rpm-version.noarch.rpm")


 (fact "rpm-exists? returns true for a 200 response"
       (rpm-exists? ..url..) => true
       (provided (client/head ..url.. anything) => {:status 200}))

 (fact "get-last-iteration returns nil if no iterations are found"
       (get-latest-iteration ..name.. ..version.. nil) => nil
       (provided
        (rpm-version ..version.. 1) => ..iversion..
        (rpm-version ..version.. anything) => nil
        (rpm-url ..name.. nil ..iversion..) => ..url..
        (rpm-exists? ..url..) => false))

 (fact "get-last-iteration returns highest available rpm"
       (get-latest-iteration ..name.. ..version.. nil) => ..i-latest..
       (provided
        (rpm-version ..version.. 1) => ..i-prev..
        (rpm-version ..version.. 2) => ..i-latest..
        (rpm-version ..version.. 3) => ..i-not-found..
        (rpm-version ..version.. anything) => nil
        (rpm-url ..name.. nil ..i-prev..) => ..url-prev..
        (rpm-url ..name.. nil ..i-latest..) => ..url-latest..
        (rpm-url ..name.. nil ..i-not-found..) => ..url-not-found..
        (rpm-exists? ..url-prev..) => true
        (rpm-exists? ..url-latest..) => true
        (rpm-exists? ..url-not-found..) => false)))
