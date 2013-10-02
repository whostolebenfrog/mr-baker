(ns ditto.packer
  (:require [me.raynes.conch :as conch]
            [me.raynes.conch.low-level :as sh]
            [cheshire.core :as json]))

;; TODO - it's painful to handle and means we have to return a 200 response
;; but I'd really like to stream the response here.

;; TODO - ssh handshake fail gives us a 200 response here?! does packer return a non 0 exit code?
;; do we need to scan the output for the word error?

;; We have to specifiy the exact path of packer here as another program called packer
;; is already on the path and is required for auth purposes
(defn packer-build
  "Builds the template and returns the ami-id from the output"
  [template-path]
  (conch/let-programs [packer "/opt/packer/packer"]
    (let [{:keys [exit-code stdout]} (packer "build" template-path {:verbose true
                                                                    :timeout (* 1000 60 30)})
          out-list (clojure.string/split stdout #"\n")]
      (if-not (pos? @exit-code)
        {:status 200 :body out-list}
        {:status 400
         :body (json/generate-string {:message "Unknown error creating ami"
                                      :packer-output out-list})}))))

(defn build
  "Build the provided template and respond with the created ami id"
  [template]
  (conch/let-programs [packer "/opt/packer/packer"]
    (let [file-name (str "/tmp/" (java.util.UUID/randomUUID))]
      (try
        (spit file-name template)
        (let [{:keys [exit-code stdout stderr] :as x} (packer "validate" file-name {:verbose true})]
          (if-not (pos? @exit-code)
            (packer-build file-name)
            {:status 400
             :body (json/generate-string {:message "Invalid template file"
                                          :out stdout
                                          :error stderr})}))
        (finally (clojure.java.io/delete-file file-name))))))
