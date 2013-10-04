(ns ditto.packer
  (:require [me.raynes.conch :as conch]
            [me.raynes.conch.low-level :as sh]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [ring.util.servlet :as ring-servlet])
  (:import (java.io FileInputStream InputStream File PipedInputStream PipedOutputStream)
           (javax.servlet.http HttpServletResponse)))

(defn flush-on-timer
  "Flushes the output stream of the supplied http servlet response every second."
  [response]
  (try
    (Thread/sleep 1000)
    (.flushBuffer response)
    (flush-on-timer response)
    (catch Exception e)))

;; We need to alter the behavior of compojure to flush streams every second
;; rather than the default of every ~32KB (completely useless for our purposes)
;; This allows us to stream response data as it arrives, rather than
;; just sending packer's response in one go after about 5 minutes of waiting!
(alter-var-root
  #'ring-servlet/set-body
  (fn [v] (fn [^HttpServletResponse response, body]
          (cond
           (string? body)
           (with-open [writer (.getWriter response)]
             (.print writer body))
           (seq? body)
           (with-open [writer (.getWriter response)]
             (doseq [chunk body]
               (.print writer (str chunk))
               (.flush writer)))
           (instance? PipedInputStream body)
           (try
             (with-open [^PipedInputStream b body]
               (future (flush-on-timer response))
               (io/copy b (.getOutputStream response)))
             (catch Exception e))
           (instance? InputStream body)
           (with-open [^InputStream b body]
             (io/copy b (.getOutputStream response)))
           (instance? File body)
           (let [^File f body]
             (with-open [f-stream (FileInputStream. f)]
               (with-open [^InputStream b f-stream]
                 (io/copy b (.getOutputStream response)))))
           (nil? body)
           nil
           :else
           (throw (Exception. ^String (format "Unrecognized body: %s" body)))))))

;; Extend conch redirectable protocol to handle PipedOutputStream
;; Allows us to pass an output stream to write the response to
(extend-type PipedOutputStream
  conch/Redirectable
  (redirect [out-stream options k proc]
    (doseq [line (get proc k)]
      (let [bytes (.getBytes line)]
        (.write out-stream bytes 0 (count bytes))
        (.flush out-stream)))
    (.close out-stream)))

(defn packer-build
  "Builds the template and returns the ami-id from the output

   Note: We have to specifiy the exact path of packer here as another program called
   packer is already on the path and is required for auth purposes"
  [template-path]
  (conch/let-programs
   [packer "/opt/packer/packer"]
   (let [out-stream (PipedOutputStream.)
         in-stream  (PipedInputStream. out-stream)]
     (future (packer "build" template-path {:out out-stream :timeout (* 1000 60 30)}))
     in-stream)))

(defn build
  "Build the provided template and respond with the created ami id"
  [template]
  (conch/let-programs [packer "/opt/packer/packer"]
    (let [file-name (str "/tmp/" (java.util.UUID/randomUUID))]
      (spit file-name template)
      (let [{:keys [exit-code stdout stderr] :as x} (packer "validate" file-name {:verbose true})]
          (if-not (pos? @exit-code)
            (packer-build file-name)
            {:status 400 :body (json/generate-string
                                {:message "Invalid template file"
                                 :out stdout
                                 :error stderr})})))))
