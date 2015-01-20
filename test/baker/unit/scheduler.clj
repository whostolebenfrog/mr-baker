(ns baker.unit.scheduler
  (:require [baker
             [scheduler :refer :all]
             [awsclient :as awsclient]
             [onix :as onix]]
            [midje.sweet :refer :all]
            [clj-time.core :as core-time])
  (:import [org.joda.time DateTimeConstants]))

(fact-group
 :unit

 (fact "kill-amis kills amis for all onix applications"
       (kill-amis) => nil
       (provided
        (kill-amis-for-application ..app1..) => nil :times 1
        (kill-amis-for-application ..app2..) => nil :times 1
        (onix/get-applications) => [..app1.. ..app2..]))

 (fact "kill amis for application does nothing if 5 or less amis"
       (kill-amis-for-application ..app..) => nil
       (provided
        (awsclient/service-ami-ids ..app..) => [..1.. ..2.. ..3.. ..4.. ..5..]
        (awsclient/deregister-ami anything anything) => nil :times 0
        (awsclient/filter-active-amis #{}) => #{}))

 (def seven-amis [1 2 3 4 5 6 7])

 (fact "kill amis for application kills the oldest amis"
       (kill-amis-for-application ..app..) => nil
       (provided
        (awsclient/service-ami-ids ..app..) => seven-amis
        (awsclient/deregister-ami ..app.. 1) => nil :times 1
        (awsclient/deregister-ami ..app.. 2) => nil :times 1
        (awsclient/deregister-ami anything anything) => nil :times 0
        (awsclient/filter-active-amis #{1 2}) => [2 1]))

 (fact "kill amis for application doesn't kill live amis"
       (kill-amis-for-application ..app..) => nil
       (provided
        (awsclient/service-ami-ids ..app..) => seven-amis
        (awsclient/deregister-ami ..app.. 2) => nil :times 0
        (awsclient/deregister-ami ..app.. 1) => nil :times 1
        (awsclient/deregister-ami anything anything) => nil :times 0
        (awsclient/filter-active-amis #{1 2}) => #{1}))

 (fact "ms-until-next-day works when before thursday"
       (against-background
        (core-time/now) => (core-time/date-time 2013 10 14))
       (let [now (core-time/now)
             ms (ms-until-next-day DateTimeConstants/THURSDAY)
             resolved (core-time/plus now (core-time/millis ms))]
         (.getDayOfWeek resolved) => 4
         (core-time/in-days (core-time/interval now resolved)) => #(< % 7)
         (.getMillisOfDay resolved) => 0))

 (fact "ms-until-next-day works when on thursday"
       (against-background
        (core-time/now) => (core-time/date-time 2013 10 17))
       (let [now (core-time/now)
             ms (ms-until-next-day DateTimeConstants/THURSDAY)
             resolved (core-time/plus now (core-time/millis ms))]
         (.getDayOfWeek resolved) => 4
         (core-time/in-days (core-time/interval now resolved)) => #(<= % 7)
         (.getMillisOfDay resolved) => 0))

 (fact "ms-until-next-day works when after thursday"
       (against-background
        (core-time/now) => (core-time/date-time 2013 10 18))
       (let [now (core-time/now)
             ms (ms-until-next-day DateTimeConstants/THURSDAY)
             resolved (core-time/plus now (core-time/millis ms))]
         (.getDayOfWeek resolved) => 4
         (core-time/in-days (core-time/interval now resolved)) => #(<= % 7)
         (.getMillisOfDay resolved) => 0)))
