(defproject clojureauth "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [compojure "1.6.1"]
                 [hiccup "2.0.0-alpha1"]
                 [buddy "2.0.0"]
                 [ring "1.7.1"]
                 [ring/ring-devel "1.7.1"]
                 [ring/ring-defaults "0.3.2"]]
  :plugins [[lein-ring "0.12.4"]]
  :ring {:handler clojureauth.handler/app}
  :main clojureauth.handler
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
