(ns baker.lock)

(def lock (atom false))

(defn lockable-bake
  "Bake the ami if the service isn't locked"
  [bake]
  (if-not @lock
    (bake)
    {:status 503
     :headers {"Content-Type" "text/plain"}
     :body (str "Service is temporarily locked with message: " @lock)}))

(defn lock-builders
  "Lock all builders (useful before redploying)"
  [req]
  (let [message (get-in req [:body "message"])]
    (reset! lock (or message "Baker is locked, no reason was supplied."))
    (str "Baker is locked and won't accept new builds: " @lock)))

(defn unlock-builders
  "Remove the builders lock"
  []
  (reset! lock false)
  {:status 204})
