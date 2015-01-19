(ns baker.dynamic-routes
  (:require [compojure.core :as compojure]
            [clout.core :as clout]))

(def handlers (atom []))

(defn route-handler [req]
  (some (fn [handler] (handler req)) @handlers))

(defn register-builder
  "Register a new builder under the /bake path"
  [route handler]
  (let [route (compojure/make-route :post (str "/bakex/" route) handler)]
    (swap! handlers conj route)))
