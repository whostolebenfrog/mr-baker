(defproject baker "0.161-SNAPSHOT"
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
        :service-name "baker"
        :service-port "8080"
        :graphite-host "graphite.example.com"
        :graphite-port "8080"
        :graphite-post-interval-seconds "60"
        :graphite-enabled "DISABLED"
        :production "false"
        :auto-reload "true"

        :lister-available "true"

        :aws-access-key "key"
        :aws-secret-key "secret"

        :packer-use-iam "true"

        :onix-url "http://onix.brislabs.com"
        :yum-url "http://yumrepo.brislabs.com/ovimusic"

        :home-aws-account "11111"
        :home-aws-region "eu-west-1"
        :additional-aws-accounts "{\"22222\" : \"arn:example\", \"33333\" : \"arn:again\"}"

        ;:aws-proxy-host "10.0.0.1"
        ;:aws-proxy-port "8080"
        }

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :uberjar-name "baker.jar"

  :rpm {:name "baker"
        :summary "RPM for Baker service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs" "packer"]
        :mappings [{:directory "/usr/local/baker"
                    :filemode "444"
                    :username "baker"
                    :groupname "baker"
                    :sources {:source [{:location "target/baker.jar"}]}}
                   {:directory "/usr/local/baker/bin"
                    :filemode "744"
                    :username "baker"
                    :groupname "baker"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/baker"
                                        :destination "baker"}]}}]}

  :main baker.setup)
