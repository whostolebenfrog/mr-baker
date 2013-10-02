(ns ditto.bake-common
  (:require [environ.core :refer [env]]))

(defn shell [& cmds]
  "Accepts a series of strings to run as shell comands. Runs commands with -x shebang and
   with sudo."
  {:type "shell"
   :execute_command "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
   :inline_shebang "/bin/sh -x"
   :inline cmds})

(defn maybe-with-keys
  "Includes access keys in the map if iam authentication isn't enabled.
   For example, when not running in aws."
  [builder]
  (merge builder (when-not (Boolean/valueOf (env :service-packer-use-iam))
                                 {:access_key (env :service-aws-access-key)
                                  :secret_key (env :service-)})))
