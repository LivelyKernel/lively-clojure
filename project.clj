(defproject cljs-workspace "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]

  :source-paths ["src-clj"]
    
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2342"]
                 [http-kit "2.1.16"]
                 [compojure "1.1.5"]
                 [ring "1.3.1"]
				 [fogus/ring-edn "0.2.0"]
                 [om "0.6.5"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [cljs-tooling "0.1.3"]
                 [om-sync "0.1.1"]
                 [com.cemerick/piggieback "0.1.3"]]

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :cljsbuild {:builds [{:id "om-test"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/om-test.js"
                                   :output-dir "resources/public/js/"
                                   :optimizations :whitespace
                                   :source-map "resources/public/js/om-test.js.map"}}]})
