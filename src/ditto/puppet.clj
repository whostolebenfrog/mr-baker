(ns ditto.puppet
  (:require [clojure.string :as string]
            [me.raynes.conch :as conch]
            [environ.core :refer [env]]
            [clojure.java.io :as io]))

(conch/programs ssh hostname)

(def puppet-username "janitor")

;; The fully-qualified hostname of this box
;(def fully-qualified-hostname
;  (string/trim-newline (hostname "--fqdn")))

(def fully-qualified-hostname "ip-10-216-138-219.brislabs.com")

(def puppet-host 
  (env :service-puppet-host))

(def user-at-puppet
  (str puppet-username "@" puppet-host))

(def temp-key-file "/tmp/ditto_rsa")

(def use-key-file 
  (str "-i " temp-key-file))

(defn move-key-file
  "Copies the SSH key file to a temporary folder where the SSH shell command can read it."
  []
  (let [key (slurp (io/resource "ditto_rsa"))]
    (spit temp-key-file key)))

;; TODO fully-qualified-hostname will be passed in & must be formatted
(defn update-puppet-ip-record
  "Calls the Puppet server (via SSH) with the IP address of this box, so Puppet can update its records."
  []
  (try
    (move-key-file)
    (let [response (ssh use-key-file user-at-puppet fully-qualified-hostname)]
      response)
    (finally
      (io/delete-file temp-key-file))))