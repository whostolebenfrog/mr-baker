(ns ditto.puppet
  (:require [clojure.string :as string]
            [me.raynes.conch :as conch]
            [environ.core :refer [env]]
            [clojure.java.io :as io]))

(conch/programs ssh hostname ls pwd)

;; This is the real one
;(def puppet-username "janitor")
(def puppet-username "bakins")

(def fully-qualified-hostname
  (string/trim-newline (hostname "--fqdn")))

(def puppet-host 
  (env :service-puppet-host))

(def temp-key-file "/tmp/bakins-key.pem")

;; This is the real one
;(def ssh-command
;  (str puppet-username "@" puppet-host " -i " temp-key-file " " fully-qualified-hostname))
(def ssh-command
  (str puppet-username "@" puppet-host " -i " temp-key-file))

(defn move-key-file
  "Copies the SSH key file to a temporary folder where the SSH shell command can read it."
  []
  (let [key (slurp (io/resource "bakins-key.pem"))]
    (spit temp-key-file key)))

(defn call-puppet
  "There must be a better name for this..."
  []
  (try
    (move-key-file)
    (let [response (ssh ssh-command '(pwd))]
      (prn (str "RESPONSE: " response)))
    (finally
      (io/delete-file temp-key-file)))
  )