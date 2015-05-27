(defproject cljs-workspace "0.1.0-SNAPSHOT"
  :description "Experimental combination of om and the morphic paradigm for live and direct development of visual applications"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License",
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]
  :source-paths ["src/clj" "src/cljs"]
  :dependencies
  [[org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-3269"]
   [org.rksm/cloxp-com "0.1.5"]
   [http-kit "2.1.19"]
   [compojure/compojure "1.3.2"]
   [ring "1.3.1"]
   [fogus/ring-edn "0.2.0"]
   [om "0.8.0-rc1"]
   [org.clojure/core.async "0.1.303.0-886421-alpha"]
   [cljs-tooling "0.1.3"]
   [jamesmacaulay/zelkova "0.4.0"]
   [prismatic/om-tools "0.3.0" :exclusions [org.clojure/clojure]]])
