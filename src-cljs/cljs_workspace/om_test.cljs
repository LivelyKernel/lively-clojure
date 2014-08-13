(ns cljs-workspace
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            ; [clojure.browser.repl :as repl]
            ))

(enable-console-print!)

(println "Hello :)")

; (repl/connect "http://localhost:9000/repl")

(def app-state (atom {:text "Hello world!"}))

(om/root
  (fn [app owner]
    (dom/h1 nil (:text app)))
  app-state
  {:target (. js/document (getElementById "app"))})
