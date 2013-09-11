(ns ditto.packer
  (:require [me.raynes.conch :as conch]
            [cheshire.core :as json]))

(conch/programs packer)

;; TODO - add time outs for the packer task so it can't lock indefinitely
;; TODO - check for the word Error: in here somewhere, if appears then fail
;; TODO - it looks like the ami can fail occasionally. In that case we need to exit
   ;; packer (using the timeout) and clean up the ami manually.
(defn packer-build
  "Builds the template and returns the ami-id from the output"
  [template-path]
  (let [packer-out (packer "build" template-path)]
    (prn packer-out)
    (->> packer-out
         (re-matches #"(?s).*AMIs were created.*(ami-[A-Za-z0-9]+).*$")
         (second))))

;; TODO - return the output from the packer build
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
