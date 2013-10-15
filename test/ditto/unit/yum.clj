(ns ditto.unit.yum
  (:use ditto.yum)
  (:require [midje.sweet :refer :all]
            [clj-http.client :as client]))

(fact-group :unit
            (fact "rpm-exists? returns true for a 200 response"
                  (rpm-exists? ..url..) => true
                  (provided (client/head ..url.. anything) => {:status 200}))

            (fact "get-last-iteration returns nil if no iterations are found"
                  (get-latest-iteration ..name.. ..version..) => nil
                  (provided
                   (rpm-version ..version.. 1) => ..iversion..
                   (rpm-version ..version.. anything) => nil
                   (rpm-url ..name.. ..iversion..) => ..url..
                   (rpm-exists? ..url..) => false))

            (fact "get-last-iteration returns highest available rpm"
                  (get-latest-iteration ..name.. ..version..) => ..i-latest..
                  (provided
                   (rpm-version ..version.. 1) => ..i-prev..
                   (rpm-version ..version.. 2) => ..i-latest..
                   (rpm-version ..version.. 3) => ..i-not-found..
                   (rpm-version ..version.. anything) => nil
                   (rpm-url ..name.. ..i-prev..) => ..url-prev..
                   (rpm-url ..name.. ..i-latest..) => ..url-latest..
                   (rpm-url ..name.. ..i-not-found..) => ..url-not-found..
                   (rpm-exists? ..url-prev..) => true
                   (rpm-exists? ..url-latest..) => true
                   (rpm-exists? ..url-not-found..) => false)))
