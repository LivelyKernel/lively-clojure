(ns cljs-workspace.makerspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.style :as gstyle]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph.clock :refer [create-clock]]
            [cljs-workspace.morph.window :refer [create-window]]
            [cljs-workspace.morph :as morphic]
            [cljs-workspace.repl :as repl]
            [rksm.cloxp-com.cloxp-client :as cloxp])
  (:import [goog.events EventType]))

(cloxp/start)

(defn get-current-time
  "current time as a map"
  []
  (let [d (js/Date.)]
    {:hours (.getHours d)
     :minutes (.getMinutes d)
     :seconds (.getSeconds d)}))

(def space-state
  (atom {:id "World"
         :morph {:Position {:x 0 :y 0}}
         :shape {:Extent {:x 1280 :y 800}
                 :BorderColor "darkgrey"
                 :BorderWidth 2
                 :Fill "lightgrey"}}))

(def mario {:id "Mario"
            :shape {:ShapeClass "Image"
                    :url "http://www.veryicon.com/icon/png/Game/Super%20Mario/Super%20Paper%20Mario.png", 
                    :Extent {:x 100 :y 100}} 
            :morph {:isDraggable true 
                    :Position {:x 50 :y 50}}})

(om/root
  morphic/morph
  space-state
  {:target (. js/document (getElementById "app"))})

(defn space-cursor [] (om/root-cursor space-state))

(defn $morph [id]
  (morphic/find-morph (space-cursor) id))

(def world-workspace-a (create-window {:position {:x 100 :y 100} 
                                     :name "WorkspaceA" 
                                     :extent {:x 200 :y 300}
                                     :$morph $morph}))

(def world-workspace-b (create-window {:position {:x 300 :y 500} 
                                     :name "WorkspaceB" 
                                     :extent {:x 200 :y 300}
                                     :$morph $morph}))

(def clock (create-clock $morph {:x 501 :y 501}))

(morphic/add-morph ($morph "World") world-workspace-a)
(morphic/add-morph ($morph "World") clock)
(morphic/add-morph ($morph "World") world-workspace-b)

(morphic/start-stepping ($morph "Clock"))
