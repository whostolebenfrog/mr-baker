(ns ditto.unit.onix
  (:require [ditto.onix :refer :all]
            [clj-http.client :as client]
            [midje.sweet :refer :all]))

(fact-group :unit
            (fact "Onix returns truthy if service exists in onix"
                  (service-exists? ..name..) => truthy
                  (provided (client/head (contains ..name..) anything) => {:status 200}))

            (fact "Onix returns falsey if service doesn't exist in onix"
                  (service-exists? ..name..) => falsey
                  (provided (client/head (contains ..name..) anything) => {:status 404})))
