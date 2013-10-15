(ns ditto.unit.web
  "Test the web namespace. We're using these in place of rest-driver tests"
  (:require [ditto.web :refer :all]
            [midje.sweet :refer :all]))

(defn request
  "Creates a compojure request map and applies it to our routes.
   Accepets method, resource and optionally an extended map"
  [method resource & [{:keys [params]
                  :or {:params {}}}]]
  (app {:request-method method
        :uri (str "/1.x/" resource)
        :params params}))

(fact-group :unit
  (fact "Ping pongs"
        (request :get "ping") => (contains {:body "pong" :status 200}))

  ;; TODO: this needs to do more
  (fact "Status returns true"
        (request :get "status") => (contains {:status 200})) )
