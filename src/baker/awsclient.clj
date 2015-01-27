(ns baker.awsclient
  (:require [baker.common :as common]
            [amazonica.aws
             [ec2 :as ec2]
             [securitytoken :as sts]
             [autoscaling :as autoscaling]]
            [cheshire.core :as json]
            [clojure.core.memoize :as mem]
            [clojure.set :refer [union difference intersection]]
            [clojure.tools.logging :refer [info]]
            [environ.core :refer [env]]))

(def home-account
  "The home account for this service, created images will be owned by this account, and
  nomally baker will be running here. It is required that no assume role is needed
  to make calls against this account. That is it must be able to make calls via IAM
  or access keys directly."
  (env :home-aws-account))

(def home-region
  "The aws region of the home account"
  (env :home-aws-region))

(def additional-accounts
  "Any other accounts that baker needs to be aware of, if you are deploying images created by
  baker to these accounts then baker needs to know about them so that it can make the images
  available to them and tag them appropriately. You will need to set up permissions such that
  baker can assume role into these accounts and perform the requierd actions. See docs for
  more info on the permissions required.

  If you only have a single aws account then this can simply be nil or an empty array.

  Additional accounts are defined as json objects in an array with the format of
  {'account-number' : 'arn-for-assume-role', 'account-number2' .. snip ..}"
  (when-let [add-accounts (env :additional-aws-accounts)]
    (json/parse-string add-accounts)))

(def ^:private proxy-details
  "Proxy details if they are defined in the environment otherwise assumed as not required"
  (let [proxy-host (env :aws-proxy-host)
        proxy-port (env :aws-proxy-port)]
    (when (and proxy-host proxy-port)
      {:client-config {:proxy-host proxy-host
                       :proxy-port (Integer/valueOf proxy-port)}})))

(defn alternative-credentials-if-necessary
  "Attempts to assume a role, if necessary, returning the credentials or nil if current role is to be used."
  [account-number]
  (when (not= account-number home-account)
    (:credentials (sts/assume-role {:role-arn (additional-accounts account-number) :role-session-name "ditto"}))))

(defn config
  "Returns a config map suitable for the request, combining proxy details, region and
  assume role credentials if required."
  ([] (config home-region))
  ([region] (merge {:endpoint region} proxy-details))
  ([region account-number]
     (merge (alternative-credentials-if-necessary account-number)
            {:endpoint region}
            proxy-details)))

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
  "Returns a list of images owned by the home account and filtered by the supplied name.
   Accepts * as a wild card.

   Returns an array of matching images or nil.

   Images are returned sorted oldest first."
  ([name]
     (sort-by :name
              ami-name-comparator
              (-> (ec2/describe-images (config)
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
     (info (format "Deregistering ami %s for service %s" image-id service))
     (try
       (ec2/deregister-image (config) :image-id image-id)
       true
       (catch Exception e false))))

(defn tag-ami
  "Set tags on the instance in the environment we are allowing access to. Sadly
   amazon doesn't automatically copy tags when making an ami available."
  ([ami tags region account-number]
     (when tags
       (info (format "Tagging ami: %s with tags %s on account %s in region %s" ami tags account-number region))
       (ec2/create-tags (config region account-number)
                        :resources [ami]
                        :tags (mapv (fn [[k v]] {:key (name k) :value v}) tags))
       (info "finished tagging ami"))))

(defn make-ami-available-to-additional-accounts
  "Allows prod access to the supplied ami"
  ([ami tags]
     (doseq [account-number (keys additional-accounts)]
       (ec2/modify-image-attribute (config)
                                   :image-id ami
                                   :operation-type "add"
                                   :user-ids [account-number]
                                   :attribute "launchPermission")
       (tag-ami ami tags home-region account-number))))

(defn allow-prod-access-to-service
  "Allows prod access to the amis for a service."
  [service]
  (common/response
   (for [ami (service-ami-ids service)]
        (make-ami-available-to-additional-accounts ami nil))))

(defn launch-configurations
  "Returns a list of all launch-configurations"
  ([region account-number]
     (let [lcs (autoscaling/describe-launch-configurations (config region account-number))]
       (concat (:launch-configurations lcs) (launch-configurations region account-number (:next-token lcs)))))
  ([region account-number token]
     (when token
       (let [lcs (autoscaling/describe-launch-configurations (config region account-number)
                                                             :next-token token)]
         (concat (:launch-configurations lcs) (launch-configurations region account-number (:next-token lcs)))))))

(def active-amis
  "Returns a list of active amis for the supplied account-number in the home region"
  (mem/ttl (fn [account-number]
             (set (map :image-id (launch-configurations home-region account-number))))
           :ttl/threshold (* 1000 30)))

(defn all-active-amis
  "Returns a set of active amis from the set of amis provided"
  []
  (apply union
         (active-amis home-account)
         (map active-amis (keys additional-accounts))))

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
