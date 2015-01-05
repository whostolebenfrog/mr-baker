(defproject ditto "0.152-SNAPSHOT"
  :description "Mr-Baker the AMI maker."

  :dependencies [[amazonica "0.2.28" :exclusions [com.fasterxml.jackson.core/jackson-annotations]]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-http "0.7.9"]
                 [clj-time "0.8.0"]
                 [compojure "1.2.1"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [joda-time "2.5"]
                 [me.raynes/conch "0.7.0"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.7"]
                 [net.logstash.logback/logstash-logback-encoder "3.3"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.memoize "0.5.6"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]
                 [ring/ring-json "0.3.1"]]

  :exclusions [commons-logging
               joda-time
               log4j]

  :profiles {:dev {:dependencies [[junit "4.11"]
                                  [midje "1.6.3"] ]
                   :plugins [[lein-rpm "0.0.4"]
                             [lein-midje "3.0.1"]
                             [lein-ancient "0.5.5"]
                             [lein-kibit "0.0.8"]]}}

  :plugins [[lein-ring "0.8.13"]
            [lein-environ "1.0.0"]
            [lein-release "1.0.5"]]

  :resource-paths ["resources" "shared" "ami-scripts" "puppet"]

  ;; development token values
  :env {:environment-name "dev"
        :service-name "ditto"
        :service-port "8080"
        :graphite-host "graphite.brislabs.com"
        :graphite-port "8080"
        :graphite-post-interval-seconds "60"
        :graphite-enabled "DISABLED"
        :production "false"

        :service-aws-access-key "key"
        :service-aws-secret-key "secret"

        :service-packer-use-iam "true"

        :service-onix-url "http://onix.brislabs.com"
        :service-yum-url "http://yumrepo.brislabs.com/ovimusic"

        :service-prod-account "269544559808"

        :aws-dev-account-id "dev-account-id"
        :aws-prod-account-id "prod-account-id"
        :aws-prod-role-arn "prod-role-arn"
        :aws-proxy-host "172.16.42.42"
        :aws-proxy-port "8080"}

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :ring {:handler ditto.web/app
         :main ditto.setup
         :port ~(Integer/valueOf  (get (System/getenv) "SERVICE_PORT" "8080"))
         :init ditto.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :uberjar-name "ditto.jar"

  :rpm {:name "ditto"
        :summary "RPM for Ditto service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs" "packer"]
        :mappings [{:directory "/usr/local/ditto"
                    :filemode "444"
                    :username "ditto"
                    :groupname "ditto"
                    :sources {:source [{:location "target/ditto.jar"}]}}
                   {:directory "/usr/local/ditto/bin"
                    :filemode "744"
                    :username "ditto"
                    :groupname "ditto"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/ditto"
                                        :destination "ditto"}]}}]}

  :main ditto.setup)
