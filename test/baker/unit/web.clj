(ns baker.unit.web
  "Test the web namespace. We're using these in place of rest-driver tests"
  (:require [baker
             [web :refer :all]
             [awsclient :as awsclient]
             [amis :as amis]]
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

 (fact "Status returns true"
       (let [{:keys [status body]} (request :get "status")]
         status => 200
         body => (contains {:success true}))))


;; TODO - test lockable bakes etc in here, use a default route from our example

(fact-group
 :unit
 (fact "Get latest amis returns amis for parent, base and public"
       (:body (request :get "amis")) => (contains {:parent-para "parent-para"
                                                   :parent-hvm "parent-hvm"
                                                   :base-hvm "base-hvm"
                                                   :base-para "base-para"
                                                   :public-hvm "public-hvm"
                                                   :public-para "public-para"} :in-any-order)
       (provided (amis/parent-ami :para) => "parent-para"
                 (amis/parent-ami :hvm) => "parent-hvm"
                 (amis/base-ami-id :hvm) =>  "base-hvm"
                 (amis/base-ami-id :para) =>  "base-para"
                 (amis/public-ami-id :hvm) => "public-hvm"
                 (amis/public-ami-id :para) => "public-para"))

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
 "Removing amis"

 (fact "Calling delete on an ami deregisters the image"
       (request :delete "service/amis/ami-id") => (contains {:status 204 :body (has-suffix "deleted successfully")})
       (provided (awsclient/deregister-ami "service" "ami-id") => true))

 (fact "Resource returns 500 when the ami fails to be removed"
       (request :delete "service/amis/ami-id") => (contains {:status 500 :body (has-prefix "Failed to remove ")})
       (provided (awsclient/deregister-ami "service" "ami-id") => false)))
