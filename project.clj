(defproject slide-stainer "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [javax.xml.bind/jaxb-api "2.2.11"]
                 [devcards "0.2.6"]
                 [org.clojure/core.async "0.7.559"]
                 [reagent "0.7.0"]
                 [dvlopt/linux.gpio "1.0.0"]
                 [bidi "2.1.6"]
                 [ring "1.7.1"]
                 [ring-cors "0.1.12"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.3.2"]
                 [clj-http "3.9.1"]
                 [cljs-http "0.1.46"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [org.clojure/data.json "0.2.6"]
                 [clojure-future-spec "1.9.0"]
                 [incanter "1.9.3"]
                 [com.walmartlabs/lacinia "0.36.0-alpha-2"]
                 [com.ashafa/clutch "0.4.0"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.cemerick/url "0.1.1"]]

  :main slide-stainer.core

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-ring "0.12.5"]]

  :ring {:handler slide-stainer.core/app}
  
  :clean-targets ^{:protect false} ["resources/public/js"
                                    "target"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles
  {:dev
   {:dependencies []

    :plugins      [[lein-figwheel "0.5.15"]]
    }}

  :cljsbuild
  {:builds
   [{:id "devcards"
    :source-paths ["src"]   
    :figwheel { :devcards true } ;; <- note this
    :compiler { :main    "slide-stainer.core"
                :asset-path "js/devcards_out"
                :output-to  "resources/public/js/slide-stainer_devcards.js"
                :output-dir "resources/public/js/devcards_out"
                :source-map-timestamp true }}
    {:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "slide-stainer.core/reload"}
     :compiler     {:main                 slide-stainer.core
                    :optimizations        :none
                    :output-to            "resources/public/js/app.js"
                    :output-dir           "resources/public/js/dev"
                    :asset-path           "js/dev"
                    :source-map-timestamp true}}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            slide-stainer.core
                    :optimizations   :advanced
                    :output-to       "resources/public/js/app.js"
                    :output-dir      "resources/public/js/min"
                    :elide-asserts   true
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}

    ]})
