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
  For example, when not running in aws. For this to actually produce valid
   packer templates the two required keys will need to be available as environment
   variables. Real keys should not be included in the project.clj for obvious reasons."
  [builder]
  (merge builder (when-not (Boolean/valueOf (env :service-packer-use-iam))
                   {:access_key (env :service-aws-access-key)
                    :secret_key (env :service-aws-secret-key)})))

(defn instance-type-for-virt-type
  "Provides the correct instance type to bake for a given virt-type, :hvm or :para"
  [virt-type]
  ({:hvm "t2.micro" :para "t1.micro"} virt-type))

(defn virtualisation-type-long
  [virt-type]
  ({:hvm "hvm" :para "paravirtual"} virt-type))
