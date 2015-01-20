(ns baker.common
  "Just some common functions. Nothing exciting here. If you find yourself
  adding some exciting code then you really ought to think about whether or
  not common is a good name for it. Doesn't sound very common to me."
  (:require [clojure.tools.logging :refer [info]]))

(defn response
  "Accepts a body an optionally a content type and status. Returns a response object."
  [body & [content-type status]]
  {:status (or status 200)
   :headers {"Content-Type" (or content-type "application/json")}
   :body body})

(defn output-piped-input-stream
  "Writes the supplied output stream to the logger"
  [stream]
  (doseq [line (line-seq (clojure.java.io/reader stream))]
    (info line)))
