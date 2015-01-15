(ns baker.unit.web
  "Test the web namespace. We're using these in place of rest-driver tests"
  (:require [baker
             [web :refer :all]
             [awsclient :as awsclient]
             [yum :as yum]
             [bake-service-ami :as service-ami]
             [public-ami :as public-ami]
             [packer :as packer]
             [scheduler :as scheduler]
             [amis :as amis]
             [entertainment-ami :as base]
             [onix :as onix]]
            [midje.sweet :refer :all]
            [cheshire.core :as json])
  (:import [java.io ByteArrayInputStream]))

(defn request
  "Creates a compojure request map and applies it to our routes.
   Accepets method, resource and optionally an extended map"
  [method resource & [{:keys [params body content-type]
                       :or {:params {}}}]]
  (let [{:keys [body] :as res} (app (merge {:request-method method
                                            :uri (str "/" resource)
                                            :params params}
                                           (when body {:body (ByteArrayInputStream.
                                                              (.getBytes
                                                               (json/generate-string body)))})
                                           (when content-type {:content-type content-type})))]
    (cond-> res
            (instance? java.io.InputStream body)
            (assoc :body (json/parse-string (slurp body) true)))))

(fact-group
 :unit

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

(fact-group
 :unit

 (fact "Service must exist to be baked"
       (request :post "bake/serv/0.13") => (contains {:status 404})
       (provided (onix/service-exists? "serv") => false))

 (fact "Service rpm must exist to be baked"
       (request :post "bake/serv/0.13") => (contains {:status 404})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => nil))

 (fact "Bake service gets the latest iteration"
       (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :para nil) => ..template..
                 (packer/build ..template.. "serv") => "template"))

 (fact "Bake service accepts embargo tags"
       (request :post "bake/serv/0.13" {:params {:embargo "prod"}}) => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :para "prod") => ..template..
                 (packer/build ..template.. "serv") => "template"))

(fact "Bake service passes the virt type if supplied"
       (request :post "bake/serv/0.13" {:params {:virt-type "hvm"}}) => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :hvm nil) => ..template..
                 (packer/build ..template.. "serv") => "template"))

 (fact "Bake service attempts to get an overridden RPM name from Onix"
       (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true
                 (onix/rpm-name "serv") => "other"
                 (yum/get-latest-iteration "serv" "0.13" "other") => "0.13-1"
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" "other" :para nil) => ..template..
                 (packer/build ..template.. "serv") => "template"))

 (fact "Service returns 503 if baker is locked"
       (request :post "lock") => (contains {:status 200 :body (contains "no reason was supplied")})
       (request :post "bake/serv/0.13") => (contains {:status 503})
       (request :delete "lock") => (contains {:status 204})
       (request :post "bake/serv/0.13") => (contains {:body "template" :status 200})
       (provided (onix/service-exists? "serv") => true :times 1
                 (onix/rpm-name "serv") =>  ..rpm-name..
                 (yum/get-latest-iteration "serv" "0.13" ..rpm-name..) => "0.13-1" :times 1
                 (service-ami/create-chroot-service-ami "serv" "0.13-1" ..rpm-name.. :para nil) => ..template.. :times 1
                 (packer/build ..template.. "serv") => "template" :times 1))

 (fact "Service can be locked with a message"
       (request :post "lock" {:body {:message "locky lock"}
                              :content-type "application/json"})
       => (contains {:status 200 :body (contains "locky lock")})
       (request :post "bake/serv/0.13")
       => (contains {:body (contains "locky lock")})
       (request :delete "lock")
       => (contains {:status 204})))

(fact-group
 :unit
 (fact "Get latest amis returns amis for parent, base and public"
       (:body (request :get "amis")) => (contains {:parent-para "parent-para"
                                                   :parent-hvm "parent-hvm"
                                                   :ent-base-hvm "ent-base-hvm"
                                                   :ent-base-para "ent-base-para"
                                                   :ent-public-hvm "ent-public-hvm"
                                                   :ent-public-para "ent-public-para"} :in-any-order)
       (provided (amis/parent-ami :para) => "parent-para"
                 (amis/parent-ami :hvm) => "parent-hvm"
                 (amis/entertainment-base-ami-id :hvm) =>  "ent-base-hvm"
                 (amis/entertainment-base-ami-id :para) =>  "ent-base-para"
                 (amis/entertainment-public-ami-id :hvm) => "ent-public-hvm"
                 (amis/entertainment-public-ami-id :para) => "ent-public-para"))

 (fact "latest service amis searches for service amis, returns the first
         10 of the reversed list"
       (against-background (awsclient/service-amis "baker") =>
                           (map (fn [x] {:name x :image-id x}) (range 20 0 -1)))
       (let [{amis :body} (request :get "amis/baker")]
         (count amis) => 10
         amis => (contains [{:image-id 1 :name 1}
                            {:image-id 2 :name 2}
                            {:image-id 10 :name 10}] :gaps-ok))))

(fact-group
 :unit
 (fact "Passing dry-run to bake entertainment ami just returns the ami definition,
         it doesn't perform the task"
       (:body (request :post "bake/entertainment-ami/hvm" {:params {:dryrun true}})) => "\"ami-definition\""
       (provided (base/create-base-ami :hvm) => "ami-definition"))

 (fact "Calling bake entertainment bakes a new entertainment ami"
       (:body (request :post "bake/entertainment-ami/para")) => "packer-response"
       (provided (base/create-base-ami :para) => "ami-definition"
                 (packer/build "ami-definition") => "packer-response"))

 (fact "Calling bake public bakes a new public ami"
       (:body (request :post "bake/public-ami/hvm")) => "packer-response"
       (provided (public-ami/create-public-ami :hvm) => "ami-definition"
                 (packer/build "ami-definition") => "packer-response"))

 (fact "Baking an entertainment-ami generates a real template"
       (-> (request :post "bake/entertainment-ami/hvm" {:params {:dryrun true}})
           :body
           (json/parse-string true)
           :builders
           first
           :ami_name) => (contains "hvm")
       (provided
        (amis/parent-ami anything) => "base-ami-id"))

 (fact "Baking a service generates a real template"
       (-> (request :post "bake/baker/0.97" {:params {:dryrun true}})
           :body
           (json/parse-string true)
           :builders) => vector?
           (provided (amis/entertainment-base-ami-id anything) => "base-ami-id"))

 (fact "Baking a service with the virt-type param switches the virtualisation type"
       (-> (request :post "bake/baker/0.97" {:params {:dryrun true :virt-type "hvm"}})
           :body
           (json/parse-string true)
           :builders
           first) => (contains {:source_ami "base-ami-id-hvm" :ami_name (contains "hvm")})
           (provided (amis/entertainment-base-ami-id :hvm) => "base-ami-id-hvm")))

(fact-group
 :unit
 "Removing amis"

 (fact "Calling delete on an ami deregisters the image"
       (request :delete "service/amis/ami-id") => (contains {:status 204 :body (has-suffix "deleted successfully")})
       (provided (awsclient/deregister-ami "service" "ami-id") => true))

 (fact "Resource returns 500 when the ami fails to be removed"
       (request :delete "service/amis/ami-id") => (contains {:status 500 :body (has-prefix "Failed to remove ")})
       (provided (awsclient/deregister-ami "service" "ami-id") => false)))
