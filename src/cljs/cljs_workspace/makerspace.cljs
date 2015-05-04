(ns cljs-workspace.makerspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.style :as gstyle]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph :as morphic]
            [cljs-workspace.repl :as repl])
  (:import [goog.events EventType]))

(def resize-button
  {:id "Button"
   :morph {:Position {:x 205 :y 155} 
           :isDraggable true
           :onDrag (fn [->self _] 
                     (let [->owner (get @->self :owner)]
                       (morphic/set-extent ->owner (get-in @->self [:morph :Position])))
                     false)} ;; continue with usual drag handling
   :shape {:Extent {:x 10 :y 10}
           :BorderColor "orange"
           :Fill "yellow"}})

(def world-workspace
  {:id "Workspace"
   :morph {:Position {:x 100 :y 100}
           :isDraggable true}
   :shape {:Extent {:x 200 :y 150}
           :BorderColor "grey"
           :Fill "white"}})

; interesting scenario: how do we attach behavior to a morph?
(defn name-morph [name]
  {:id "nameMorph"
   :morph {:Position {:x 5 :y 10}
           :MorphClass "Text"
           :TextString name}
   :shape {:Extent {:x 100 :y 20}}})

(def min-box
  {:id "minBox"
   :morph {:Position {:x 0 :y 0}
           :MorphClass "Text"
           :TextString "X"}
   :shpae {:Extent {:x 17 :y 17}
           :Fill "red"
           :BorderColor "brown"
           :TextColor "white"}})

(def close-box
  {:id "closeBox"
   :morph {:Position {:x 0 :y 0}
           :MorphClass "Text"
           :TextString "-"}
   :shpae {:Extent {:x 17 :y 17}
           :Fill "grey"
           :BorderColor "darkgrey"}})

(def window-bar
  {:id "windowbar"
   :morph {:Position {:x 0 :y 0}}
   :shape {:Extent {:x 200 :y 30}
           :BorderColor "grey"
           :Fill "lightgrey"}
   :submorphs [(name-morph "Workspace") min-box close-box]})

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

(def space-cursor (morphic/make-cursor space-state))

(defn $morph [id]
  (morphic/find-morph space-state id))

(morphic/add-morph ($morph "World") world-workspace)
(morphic/add-morph ($morph "Workspace") resize-button)
(morphic/add-morph ($morph "Workspace") window-bar)
(morphic/add-morph ($morph "Workspace") mario)