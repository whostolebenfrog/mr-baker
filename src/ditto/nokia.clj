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
          (name type)))

(defn latest-nokia-ami
  "Returns the latest nokia base ami image-id. Accepts server type of either :ebs or :instance"
  [server-type]
  (let [ami-names (take 2 (map (partial nokia-ami-name server-type) (past-wednesdays)))]
    (->> (map aws/private-images-by-name ami-names)
         (some identity)
         (first)
         :ImageId)))
