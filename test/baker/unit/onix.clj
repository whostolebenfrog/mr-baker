(ns baker.unit.onix
  (:require [baker.onix :refer :all]
            [clj-http.client :as client]
            [midje.sweet :refer :all]))

(fact-group :unit
  (fact "Onix returns truthy if service exists in onix"
        (service-exists? ..name..) => truthy
        (provided (client/head (contains ..name..) anything) => {:status 200}))

  (fact "Onix returns falsey if service doesn't exist in onix"
        (service-exists? ..name..) => falsey
        (provided (client/head (contains ..name..) anything) => {:status 404}))

  (fact "Get all applications returns the list of all applications from onix"
        (get-applications) => [..app1.. ..app2..]
        (provided (client/get (contains "application") anything) =>
                  {:status 200 :body {:applications [..app1.. ..app2..]}})))
