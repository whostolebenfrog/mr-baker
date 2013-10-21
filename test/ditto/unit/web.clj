(ns ditto.unit.web
  "Test the web namespace. We're using these in place of rest-driver tests"
  (:require [ditto
             [web :refer :all]
             [aws :as aws]
             [yum :as yum]
             [bake-service-ami :as service-ami]
             [public-ami :as public-ami]
             [packer :as packer]
             [scheduler :as scheduler]
             [nokia :as nokia]
             [entertainment-ami :as base]
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
            (assoc :body (json/parse-string (slurp body) true)))))

(fact-group :unit

  (fact "Ping pongs"
        (request :get "ping") => (contains {:body "pong" :status 200}))

  (fact "Status returns true if all dependencies met"
        (against-background (scheduler/job-is-scheduled? "baker") => true
                            (scheduler/job-is-scheduled? "killer") => true)
        (let [{:keys [status body]} (request :get "status")]
          status => 200
          body => (contains {:success true})))

  (fact "Status returns false if scheduler is down"
        (against-background (scheduler/job-is-scheduled? "baker") => false
                            (scheduler/job-is-scheduled? "killer") => true)
        (let [{:keys [status body]} (request :get "status")]
          status => 500
          body => (contains {:success false})))

  (fact "Health check returns status"
        (app {:request-method :get :uri "/healthcheck"}) => (contains {:status 200})
        (provided (scheduler/job-is-scheduled? "baker") => true
                            (scheduler/job-is-scheduled? "killer") => true)))

(fact-group :unit

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

(fact-group :unit
  (fact "Get latest amis returns amis for nokia base, base and public"
        (:body (request :get "amis")) => {:nokia-base "nokia-base"
                                          :ent-base "ent-base"
                                          :ent-public "ent-public"}
        (provided (nokia/latest-nokia-ami) => "nokia-base"
                  (base/entertainment-base-ami-id) =>  "ent-base"
                  (public-ami/entertainment-public-ami-id) => "ent-public"))

  (fact "latest service amis searches for service amis, returns the first
         10 of the reversed list"
        (against-background (aws/service-images "ditto") =>
                            (map (fn [x] {:Name x :ImageId x}) (range 20 0 -1)))
        (let [{amis :body} (request :get "amis/ditto")]
          (count amis) => 10
          amis => (contains [{:ImageId 1 :Name 1}
                             {:ImageId 2 :Name 2}
                             {:ImageId 10 :Name 10}] :gaps-ok))))

(fact-group :unit
  (fact "Passing dry-run to bake entertainment ami just returns the ami definition,
         it doesn't perform the task"
        (:body (request :post "bake/entertainment-ami" {:params {:dryrun true}})) => "ami-definition"
        (provided (nokia/latest-nokia-ami) => ..latest-ami..
                  (base/create-base-ami ..latest-ami..) => "ami-definition"))

  (fact "Calling bake entertainment bakes a new entertainment ami"
        (:body (request :post "bake/entertainment-ami")) => "packer-response"
        (provided (nokia/latest-nokia-ami) => ..latest-ami..
                  (base/create-base-ami ..latest-ami..) => "ami-definition"
                  (packer/build "ami-definition") => "packer-response"))

  (fact "Calling bake public bakes a new public ami"
        (:body (request :post "bake/public-ami")) => "packer-response"
        (provided (public-ami/create-public-ami) => "ami-definition"
                  (packer/build "ami-definition") => "packer-response")))
