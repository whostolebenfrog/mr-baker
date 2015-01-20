(ns baker.builders.scheduler
  "Register any schedules function calls here, e.g. to automatically bake
  the base ami every thursday"
  (:require [baker
             [common :as common]
             [packer :as packer]]
            [baker.builders
             [bake-base-ami :as base]
             [bake-public-ami :as public-ami]]
            [clojure.tools.logging :refer [info error]])
  (:import [org.joda.time DateTimeConstants]))

(defn bake-base-ami
  "Bake the base ami"
  [virt-type]
  (info (str "Starting scheduled bake of base ami: " virt-type))
  (-> (base/create-base-ami virt-type)
      (packer/build)
      (common/output-piped-input-stream)))

(defn bake-public-ami
  "Bake the public ami"
  [virt-type]
  (info (str "Starting scheduled bake of public ami: " virt-type))
  (-> (public-ami/create-public-ami virt-type)
      (packer/build)
      (common/output-piped-input-stream)))

(defn bake-amis
  "Bake a new base ami, followed by its public counterpart"
  []
  (try
    (bake-base-ami :hvm)
    (bake-base-ami :para)
    (bake-public-ami :hvm)
    (bake-public-ami :para)
    (catch Exception e
      (error e "Error while baking shared AMIs"))))

(def scheduled-builders
  [{:handler bake-amis :run-weekly-on (DateTimeConstants/THURSDAY) :desc "Weekly base ami scheduler"}])
