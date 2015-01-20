(ns baker.awsclient
  (:require [amazonica.aws
             [ec2 :as ec2]
             [securitytoken :as sts]
             [autoscaling :as autoscaling]]
            [cheshire.core :as json]
            [clojure.core.memoize :as mem]
            [clojure.set :refer [union difference intersection]]
            [clojure.tools.logging :refer [info]]
            [environ.core :refer [env]]))

;; TODO - have to make this configurable, currently tied to our accounts
(def environments
  {:poke {:account-id (env :aws-dev-account-id)
          :assume-role? false}
   :prod {:account-id (env :aws-prod-account-id)
          :account-arn (env :aws-prod-role-arn)
          :assume-role? true}})

(def ^:private proxy-details
  (let [proxy-host (env :aws-proxy-host)
        proxy-port (env :aws-proxy-port)]
    (when (and proxy-host proxy-port)
      {:client-config {:proxy-host proxy-host
                       :proxy-port (Integer/valueOf proxy-port)}})))

(defn alternative-credentials-if-necessary
  "Attempts to assume a role, if necessary, returning the credentials or nil if current role is to be used."
  [environment-name]
  (let [environment (environments environment-name)]
    (when (:assume-role? environment)
      (:credentials (sts/assume-role {:role-arn (:account-arn environment) :role-session-name "ditto"})))))

(defn config
  [environment region]
  (merge (alternative-credentials-if-necessary environment)
         {:endpoint region}
         proxy-details))

(defn ami-name-comparator
  "Sort amis by date generated"
  [a b]
  (let [splitter (partial re-matches #"^.*(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}).*$")]
    (if (splitter a)
        (let [[_ date-a] (splitter a)
              [_ date-b] (splitter b)]
          (compare date-a date-b))
        (compare a b))))

(defn owned-amis-by-name
  "Returns a list of images owned by the current account and filtered by the supplied name.
   Accepts * as a wild card.

   Returns an array of matching images or nil.

   Images are returned sorted oldest first."
  ([name]
     (owned-amis-by-name :poke "eu-west-1" name))
  ([environment region name]
     (sort-by :name
              ami-name-comparator
              (-> (ec2/describe-images (config environment region)
                                       :owner ["self"]
                                       :filters [{:name "name" :values [name]}])
                  :images
                  seq))))

(defn service-amis
  "Returns the images for a service in the default environment and region"
  [name]
  (owned-amis-by-name (str "ent*-" name "-*")))

(defn service-ami-ids
  "Returns the list of ami ids for the supplied service in the default environment and region"
  [name]
  (map :image-id (service-amis name)))

(defn deregister-ami
  "Deregister an ami. Returns true if successful, otherwise false"
  ([service image-id]
     (deregister-ami :poke "eu-west-1" service image-id))
  ([environment region service image-id]
     (info (format "Deregistering ami %s for service %s" image-id service))
     (try
       (ec2/deregister-image (config environment region) :image-id image-id)
       true
       (catch Exception e false))))

(defn tag-ami
  "Set tags on the instance in the environment we are allowing access to. Sadly
   amazon doesn't automatically copy tags when making an ami available."
  ([ami tags]
     (tag-ami ami tags :prod "eu-west-1"))
  ([ami tags environment region]
     (info (format "Tagging ami: %s with tags %s and env %s region %s" ami tags environment region))
     (ec2/create-tags (config environment region)
                      :resources [ami]
                      :tags (mapv (fn [[k v]] {:key (name k) :value v}) tags))
     (info "finished tagging ami")))

(defn allow-prod-access-to-ami
  "Allows prod access to the supplied ami"
  ([ami tags]
     (allow-prod-access-to-ami :poke "eu-west-1" ami tags))
  ([environment region ami tags]
     (ec2/modify-image-attribute (config environment region)
                                 :image-id ami
                                 :operation-type "add"
                                 :user-ids [(env :service-prod-account)]
                                 :attribute "launchPermission")
     (tag-ami ami tags :prod region)))

(defn allow-prod-access-to-service
  "Allows prod access to the amis for a service."
  [service]
  (map
   (comp allow-prod-access-to-ami :image-id)
   (service-amis service)))

(defn launch-configurations
  "Returns a list of all launch-configurations"
  ([]
     (launch-configurations :poke "eu-west-1"))
  ([environment region]
     (let [lcs (autoscaling/describe-launch-configurations (config environment region))]
       (concat (:launch-configurations lcs) (launch-configurations environment region (:next-token lcs)))))
  ([environment region token]
     (when token
       (let [lcs (autoscaling/describe-launch-configurations (config environment region)
                                                             :next-token token)]
         (concat (:launch-configurations lcs) (launch-configurations environment region (:next-token lcs)))))))

(def active-amis
  "Returns a list of active amis for the supplied environment and region"
  (mem/ttl (fn [environment region]
             (set (map :image-id (launch-configurations environment region))))
           :ttl/threshold (* 1000 30)))

(defn all-active-amis
  "Returns a set of active amis from the set of amis provided"
  []
  (union (active-amis :poke "eu-west-1")
                     (active-amis :prod "eu-west-1")))

(defn filter-active-amis
  "Returns the supplied set of amis with any in use amis removed"
  [amis]
  {:pre [set? amis]}
  (difference amis (all-active-amis)))

(defn active-amis-for-service
  "Returns the active amis for a service"
  [name environment region]
  (intersection (set (service-ami-ids name))
                            (active-amis environment region)))
