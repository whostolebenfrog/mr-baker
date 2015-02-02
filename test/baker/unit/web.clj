(ns baker.unit.web
  "Test the web namespace. We're using these in place of rest-driver tests"
  (:require [baker
             [web :refer :all]
             [awsclient :as awsclient]
             [amis :as amis]]
            [baker.builders
             [bake-example-template :as example]]
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

(fact-group
 :unit
 "Bakes can be performed, locked and unlocked"

 (fact "When lock is not set, bakes can be run"
       (request :post "bake/example/test/1.0.1/hvm") => (contains {:status 200})
       (provided (example/bake-example-ami "test" "1.0.1" nil :hvm) => {:status 200 :body "x"}))

 (fact "Lock can be set and removed"
       (request :post "lock" {:body {"message" "lock message"}
                              :content-type "application/json"})
       (request :post "bake/example/lockedtest/1.0.1/hvm") => (contains {:status 503
                                                                         :body (has-suffix "lock message")})

       (request :delete "lock") => (contains {:status 204})
       (request :post "bake/example/unlockedtest/1.0.1/hvm") => (contains {:status 200})
       (provided (example/bake-example-ami "unlockedtest" "1.0.1" nil :hvm) => {:status 200 :body "x"})))

(fact-group
 :unit
 "Bakes generate templates"

 (fact "Chroot example generates a template"
       (let [response (request :post "bake/chroot-example/test/1.0.1/para" {:params {"dry-run" true}})
             template (json/parse-string (:body response) true)
             builder (-> template :builders first)
             provisioner (-> template :provisioners first)]
         (:ami_name builder) => (contains "para")
         (:ami_name builder) => (contains "1.0.1")
         (:ami_name builder) => (contains "test")
         (:source_ami builder) => not-empty
         (:ami_virtualization_type builder) => "paravirtual"
         (:type builder) => "amazon-chroot"

         (:type provisioner) => "shell"
         (-> provisioner :inline first) => (contains "wget")
         (-> provisioner :inline first) => (contains "test")
         (-> provisioner :inline first) => (contains "1.0.1")
         (-> provisioner :inline second) => (contains "yum -y install")))

 (fact "EBS example generates a template"
       (let [response (request :post "bake/example/test/1.0.1/hvm" {:params {"dry-run" true}})
             template (json/parse-string (:body response) true)
             builder (-> template :builders first)
             provisioner (-> template :provisioners first)]

         (:ami_name builder) => (contains "hvm")
         (:ami_name builder) => (contains "1.0.1")
         (:ami_name builder) => (contains "test")
         (:iam_instance_profile builder) => "baking"
         (:instance_type builder) => "t2.micro"
         (:security_group_id builder) => "security-group-id-placeholder"
         (:source_ami builder) => not-empty
         (:ssh_timeout builder) => "5m"
         (:ssh_username builder) => "ec2-user"
         (:subnet_id builder) => "target-subnet-id-placeholder"
         (:region builder) => "eu-west-1"
         (:type builder) => "amazon-ebs"
         (:vpc_id builder) => "vpc-id-placeholder"

         (:type provisioner) => "shell"
         (-> provisioner :inline first) => (contains "wget")
         (-> provisioner :inline first) => (contains "test")
         (-> provisioner :inline first) => (contains "1.0.1")
         (-> provisioner :inline second) => (contains "yum -y install"))))
