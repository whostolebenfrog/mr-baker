(ns ditto.packer
  (:require [clojure.java.shell :as shell]
            [me.raynes.conch :as conch]
            [cheshire.core :as json]))

(conch/programs packer)

;; TODO - check for the word Error: in here somewhere, if appears then fail
(defn packer-build
  "Builds the template and returns the ami-id from the output"
  [template-path]
  (let [packer-out (packer "build" template-path)]
    (prn packer-out)
    (->> packer-out
         (re-matches #"(?s).*AMIs were created.*(ami-[A-Za-z0-9]+).*$")
         (second))))

;; TODO - needs to return the full response
(defn build
  "Build the provided template and respond with the created ami id"
  [template]
  (let [file-name (str "/tmp/" (java.util.UUID/randomUUID))]
    (try
      (spit file-name template)
      (let [{:keys [exit-code stdout] :as xx} (packer "validate" file-name {:verbose true})]
        (prn "printing validation")
        (prn xx)
        (prn @exit-code)
        (prn stdout)
        (if-not (pos? @exit-code)
          {:status 200
           :body (json/generate-string {:status 200 :message (packer-build file-name)})}
          {:status 400
           :body (json/generate-string {:status 400 :message "Invalid template file"})}))
      (finally (clojure.java.io/delete-file file-name)))))
