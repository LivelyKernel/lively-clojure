(ns cljs-workspace.makerspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.style :as gstyle]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph.window :refer [create-window]]
            [cljs-workspace.morph :as morphic]
            [cljs-workspace.repl :as repl]
            [rksm.cloxp-com.cloxp-client :as cloxp])
  (:import [goog.events EventType]))

(cloxp/start)

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
  {:target (. js/document (getElementById "app"))
   :state {:id "World"
         :morph {:Position {:x 0 :y 0}}
         :shape {:Extent {:x 1280 :y 800}
                 :BorderColor "darkgrey"
                 :BorderWidth 2
                 :Fill "lightgrey"}}})

(def world-workspace (create-window {:position {:x 100 :y 100} :name "Workspace" :extent {:x 200 :y 300}}))

(def space-cursor (morphic/make-cursor space-state))

(defn $morph [id]
  (morphic/find-morph space-state id))

(morphic/add-morph ($morph "World") world-workspace)