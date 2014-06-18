(defproject ditto "0.94"
  :description "Ditto service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Ditto"

  :dependencies [[compojure "1.1.8" :exclusions [javax.servlet/servlet-api]]
                 [ring-middleware-format "0.3.2"]
                 [ring/ring-jetty-adapter "1.2.2" :exclusions [org.eclipse.jetty/jetty-server]]
                 [ring/ring-json "0.3.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.eclipse.jetty/jetty-server "8.1.15.v20140411"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [com.ovi.common.logging/logback-appender "0.0.45" :exclusions [commons-logging/commons-logging]]
                 [com.yammer.metrics/metrics-logback "2.2.0"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.25"]
                 [clj-http "0.7.8"]
                 [cheshire "5.3.1"]
                 [clj-time "0.7.0"]
                 [environ "0.5.0"]
                 [nokia/ring-utils "1.2.4"]
                 [metrics-clojure "1.0.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [overtone/at-at "1.2.0"]
                 [me.raynes/conch "0.7.0"]]

  :profiles {:dev {:dependencies [[junit "4.11"]
                                  [midje "1.6.3"] ]
                   :plugins [[lein-rpm "0.0.4"]
                             [lein-midje "3.0.1"]
                             [lein-ancient "0.5.5"]
                             [lein-kibit "0.0.8"]]}}

  :plugins [[lein-ring "0.8.6"]
            [lein-environ "0.4.0"]
            [lein-release "1.0.73"]]

  :resource-paths ["resources" "shared" "ami-scripts" "puppet"]

  ;; development token values
  :env {:environment-name "Dev"
        :service-name "ditto"
        :service-port "8080"
        :service-url "http://localhost:%s/1.x"
        :environment-entertainment-graphite-host "graphite.brislabs.com"
        :environment-entertainment-graphite-port "8080"
        :service-graphite-post-interval "1"
        :service-graphite-post-unit "MINUTES"
        :service-graphite-enabled "DISABLED"
        :service-production "false"

        :service-aws-access-key "key"
        :service-aws-secret-key "secret"
        :service-puppet-host "puppetaws.brislabs.com"

        :service-packer-use-iam "false"

        :service-onix-url "http://onix.brislabs.com:8080"
        :service-asgard-url "http://asgard.brislabs.com:8080"
        :service-yum-url "http://yumrepo.brislabs.com/ovimusic"

        :service-prod-account "269544559808"}

  :lein-release {:release-tasks [:clean :uberjar :pom :rpm]
                 :clojars-url "clojars@clojars.brislabs.com:"}

  :ring {:handler ditto.web/app
         :main ditto.setup
         :port ~(Integer.  (get (System/getenv) "SERVICE_PORT" "8080"))
         :init ditto.setup/setup
         :browser-uri "/1.x/status"}

  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}

  :uberjar-name "ditto.jar"

  :rpm {:name "ditto"
        :summary "RPM for Ditto service"
        :copyright "Nokia 2013"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.6.0_31-fcs" "packer"]
        :mappings [{:directory "/usr/local/jetty"
                    :filemode "444"
                    :username "jetty"
                    :groupname "jetty"
                    :sources {:source [{:location "target/ditto.jar"}]}}
                   {:directory "/usr/local/jetty/bin"
                    :filemode "744"
                    :username "jetty"
                    :groupname "jetty"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/usr/local/deployment/ditto/bin"
                    :filemode "744"
                    :sources {:source [{:location "scripts/dmt"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "744"
                    :username "jetty"
                    :groupname "jetty"
                    :sources {:source [{:location "scripts/service/jetty"}]}}]}

  :main ditto.setup)
