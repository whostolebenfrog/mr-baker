(ns baker.unit.bake-common
  (:require [baker
             [bake-common :refer :all]]
            [midje.sweet :refer :all]
            [environ.core :refer [env]]))

(fact-group :unit
  (fact "shell is of type shell"
        (:type (shell ..cmd..)) => "shell")

  (fact "shell chmods us"
        (:execute_command (shell ..cmd..)) => (has-prefix "chmod +x"))

  (fact "shell inlines cmds in order"
        (:inline (shell ..cmd1.. ..cmd2..)) => (contains [..cmd1.. ..cmd2..])))

(fact-group :unit
  (fact "maybe with keys adds keys if not in iam mode"
        (maybe-with-keys {}) => (contains {:access_key ..access..
                                           :secret_key ..secret..})
        (provided
         (env :service-packer-use-iam) => "false"
         (env :service-aws-secret-key) => ..secret..
         (env :service-aws-access-key) => ..access..))

  (fact "maybe with keys does not add keys in iam mode"
        (maybe-with-keys {}) => {}
        (provided
         (env :service-packer-use-iam) => "true")))
