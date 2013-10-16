(ns ditto.unit.scheduler
  (:require [ditto
             [scheduler :refer :all]
             [asgard :as asgard]
             [aws :as aws]
             [onix :as onix]]
            [midje.sweet :refer :all]
            [clj-time.core :as core-time]))

(fact-group :unit
  (fact "kill-amis kills amis for all onix applications"
        (kill-amis) => truthy
        (provided
         (kill-amis-for-application ..app1..) => nil :times 1
         (kill-amis-for-application ..app2..) => nil :times 1
         (onix/get-applications) => [..app1.. ..app2..]))

  (fact "kill amis for application does nothing if 5 or less amis"
        (kill-amis-for-application ..app..) => truthy
        (provided
         (aws/service-images ..app..) => [..1.. ..2.. ..3.. ..4.. ..5..]
         (aws/deregister-ami anything) => nil :times 0
         (asgard/active-amis-for-application ..app..) => ..other..))

  (def seven-amis [{:ImageId 1} {:ImageId 2} {:ImageId 3}
                   {:ImageId 4} {:ImageId 5} {:ImageId 6}
                   {:ImageId 7}])

  (fact "kill amis for application kills the oldest amis"
        (kill-amis-for-application ..app..) => truthy
        (provided
         (aws/service-images ..app..) => seven-amis
         (aws/deregister-ami ..app.. 1) => nil :times 1
         (aws/deregister-ami ..app.. 2) => nil :times 1
         (aws/deregister-ami anything) => nil :times 0
         (asgard/active-amis-for-application ..app..) => ..other..))

  (fact "kill amis for application doesn't kill live amis"
        (kill-amis-for-application ..app..) => truthy
        (provided
         (aws/service-images ..app..) => seven-amis
         (aws/deregister-ami ..app.. 2) => nil :times 0
         (aws/deregister-ami ..app.. 1) => nil :times 1
         (aws/deregister-ami anything) => nil :times 0
         (asgard/active-amis-for-application ..app..) => #{2}))

  (fact "ms-until-next-thursday works when before thursday"
        (against-background
         (core-time/now) => (core-time/date-time 2013 10 14))
        (let [now (core-time/now)
              ms (ms-until-next-thursday)
              resolved (core-time/plus now (core-time/millis ms))]
          (.getDayOfWeek resolved) => 4
          (core-time/in-days (core-time/interval now resolved)) => #(< % 7)
          (.getMillisOfDay resolved) => 0))

  (fact "ms-until-next-thursday works when on thursday"
        (against-background
         (core-time/now) => (core-time/date-time 2013 10 17))
        (let [now (core-time/now)
              ms (ms-until-next-thursday)
              resolved (core-time/plus now (core-time/millis ms))]
          (.getDayOfWeek resolved) => 4
          (core-time/in-days (core-time/interval now resolved)) => #(<= % 7)
          (.getMillisOfDay resolved) => 0))

  (fact "ms-until-next-thursday works when after thursday"
        (against-background
         (core-time/now) => (core-time/date-time 2013 10 18))
        (let [now (core-time/now)
              ms (ms-until-next-thursday)
              resolved (core-time/plus now (core-time/millis ms))]
          (.getDayOfWeek resolved) => 4
          (core-time/in-days (core-time/interval now resolved)) => #(<= % 7)
          (.getMillisOfDay resolved) => 0)))
