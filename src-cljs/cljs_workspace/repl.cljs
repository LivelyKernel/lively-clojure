(ns cljs-workspace.repl
  (:require [clojure.browser.repl :as repl]))

(enable-console-print!)

(def port 9050)

(repl/connect (str "http://localhost:" port "/repl"))
(print (str "repl connected on port " port))