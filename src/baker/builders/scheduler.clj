(ns baker.builders.scheduler
  "Register any schedules function calls here, e.g. to automatically bake
  the base ami every thursday"
  (:require [baker
             [common :as common]
             [packer :as packer]])
  (:import [org.joda.time DateTimeConstants]))

;; TODO
(defn bake-base-ami
  "Bake the base ami"
  []
  (comment (-> (base/create-base-ami virt-type)
               (packer/build)
               (common/output-piped-input-stream))))

(def scheduled-builders
  [{:handler bake-base-ami :run-weekly-on (DateTimeConstants/THURSDAY) :desc "Weekly base ami scheduler"}])
