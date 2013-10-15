(ns ditto.unit.web
  "Test the web namespace. We're using these in place of rest-driver tests"
  (:require [ditto
             [web :refer :all]
             [yum :as yum]
             [bake-service-ami :as service-ami]
             [packer :as packer]
             [onix :as onix]]
            [midje.sweet :refer :all]
            [cheshire.core :as json]))

(defn request
  "Creates a compojure request map and applies it to our routes.
   Accepets method, resource and optionally an extended map"
  [method resource & [{:keys [params]
                  :or {:params {}}}]]
  (let [{:keys [body] :as res} (app {:request-method method
                                     :uri (str "/1.x/" resource)
                                     :params params})]
    (cond-> res
            (instance? java.io.InputStream body)
            (assoc :body (json/parse-string (slurp body))))))

(fact-group [:unit :general]

  (fact "Ping pongs"
        (request :get "ping") => (contains {:body "pong" :status 200}))

  (fact "Status returns true"
        (request :get "status") => (contains {:status 200})))

(fact-group [:unit :service-baking]

  (fact "Service must exist to be baked"
        (request :post "bake/serv/0.13") => (contains {:status 404})
        (provided (yum/get-latest-iteration "serv" "0.13") => "0.13-1"
                  (onix/service-exists? "serv") => false))

  (fact "Service rpm must exist to be baked"
        (request :post "bake/serv/0.13") => (contains {:status 404})
        (provided (yum/get-latest-iteration "serv" "0.13") => nil))

  (fact "Bake service gets the latest iteration"
        (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
        (provided (yum/get-latest-iteration "serv" "0.13") => "0.13-1"
                  (onix/service-exists? "serv") => true
                  (service-ami/create-service-ami "serv" "0.13-1") => ..template..
                  (packer/build ..template..) => "template")))
