(ns baker.builders.scheduler
  "Register any schedules function calls here, e.g. to automatically bake
  the base ami every thursday"
  (:require [baker
             [common :as common]
             [packer :as packer]]
            [baker.builders.bake-example-template :as example])
  (:import [org.joda.time DateTimeConstants]))

(defn bake-base-ami
  "Bake the base ami"
  []
  (-> (example/bake-example-ami "example" "1.0.1" false :hvm)
      (packer/build)
      (common/output-piped-input-stream)))

(def scheduled-builders
  [{:handler bake-base-ami :run-weekly-on (DateTimeConstants/THURSDAY) :desc "Weekly base ami scheduler"}])
