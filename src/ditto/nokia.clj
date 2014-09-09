(ns ditto.nokia
  (:require [ditto.aws :as aws]
            [clj-time
             [core :as time-core]
             [format :as time-format]
             [periodic :as time-periodic]]))

(defn past-wednesdays
  "Returns a seq of previous wednesdays orderered most recent first"
  []
  (->> (time-periodic/periodic-seq (time-core/date-time 2013 8 28) (time-core/weeks 1))
       (take-while #(time-core/before? % (time-core/now)))
       (reverse)))

(defn nokia-ami-name
  "Provides the name of the nokia base ami for the supplied date and type"
  [type date]
  (format "centos-6-x86_64-%s-%s-release"
          (time-format/unparse (time-format/formatter "MMddYYYY") date)
          type))

(defn latest-nokia-ami
  "Returns the latest nokia base ami image-id. Accepts server type of either :ebs or :instance.
   Defaults to :ebs if not specified."
  [virt-type]
  {:pre [(#{:hvm :para} virt-type)]}
  (let [virt-type-name (condp = virt-type
                         :para "ebs"
                         :hvm "hvm")
        ami-names (take 5 (map (partial nokia-ami-name virt-type-name)
                               (past-wednesdays)))]
    (->> (map aws/private-images-by-name ami-names)
         (some identity)
         (first)
         :ImageId)))

(defn entertainment-base-ami-id
  "Returns the id of the latest entertainment base ami"
  [virt-type]
  (-> (aws/owned-images-by-name (format "entertainment-base-%s*" (name virt-type)))
      (last)
      :ImageId))

(defn entertainment-public-ami-id
  "Returns the id of the latest entertainment public ami"
  [virt-type]
  (-> (aws/owned-images-by-name (format "entertainment-public-%s-*" (name virt-type)))
      (last)
      :ImageId))
