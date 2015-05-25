(ns cljs-workspace.morph.window
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph :as morphic])
  (:import [goog.events EventType]))

(def control-height 12)

(defn name-morph [name]
  {:id "nameMorph"
   :morph {:Position {:x 50 :y 5}
           :TextString name
           :FontFamily "Arial"
           :AllowInput false}
   :shape {:ShapeClass "Text"
           :Extent {:x 100 :y 20}}})

(def close-box
  {:id "closeBox"
   :morph {:Position {:x 10 :y 10}
           :onMouseEnter (fn [self _] 
                           (let [x-box (-> self :>> :submorphs (get 0))]
                             (morphic/toggle-visibility x-box)))
           :onMouseLeave (fn [self _] 
                           (let [x-box (-> self :>> :submorphs (get 0))]
                             (morphic/toggle-visibility x-box)))
           :onClick (fn [self _]
                      (let [->self (self :>>)
                             ->target (prn (om/path ->self))]
                        (morphic/remove-morph (get-in ->target :owner) (get @->target :id))))}
   :shape {:ShapeClass "Ellipse"
           :Extent {:x control-height :y control-height}
           :Fill "#ff6052"}
   :submorphs [{:morph {:Position {:x 2 :y -2}
                        :TextString "×"
                        :FontSize 10 
                        :AllowInput false
                        :Visible false}
                :shape {:ShapeClass "Text"}}]})

(def min-box
  {:id "minBox"
   :morph {:Position {:x 30 :y 10}
           :onMouseEnter (fn [->self _] 
                           (let [x-box (get-in ->self [:submorphs 0])]
                             (morphic/toggle-visibility x-box)))
           :onMouseLeave (fn [->self _] 
                           (let [x-box (get-in ->self [:submorphs 0])]
                             (morphic/toggle-visibility x-box)))
           :onClick (fn [->self]
                      (let [->target (get-in ->self [:owner :owner])
                            width (get @->target [:shape :Extent :x])]
                        (morphic/set-extent ->target {:x width :y 15})))}
   :shape {:ShapeClass "Ellipse"
           :Extent {:x control-height :y control-height}
           :Fill "#ffbe06"}
   :submorphs [{:morph {:Position {:x 2 :y -2}
                        :TextString "−"
                        :FontSize 10 
                        :AllowInput false
                        :Visible false}
                :shape {:ShapeClass "Text"}}]})

(def window-bar
  {:id "windowbar"
   :morph {:Position {:x 0 :y 0}}
   :shape {:Extent {:x 200 :y 30}}
   :submorphs [(name-morph "Workspace") min-box close-box]})

(defn resize-button [owner-extent]
  {:id "Button"
   :morph {:Position (morphic/add-points owner-extent {:x 5 :y 5}) 
           :isDraggable true
           :onDrag (fn [self _]
                     (let [->self (self :>>)
                           ->target (self :owner)]
                       (morphic/set-extent ->target (morphic/add-points {:x -5 :y -5} (get-in @->self [:morph :Position])))
                       true))}
   :shape {:ShapeClass "Image"
           :Extent {:x 15 :y 15}
           :url "http://lively-web.org/core/media/halos/resize.svg"}})

(defn create-window [{name :name, position :position, extent :extent}]
  {:id name
   :morph {:Position position
           :isDraggable true}
   :shape {:Extent extent
           :BorderColor "grey"
           :Fill "linear-gradient(to bottom, #f0f0f0, #e9e9e9)"
           :BorderRadius 10
           :BorderWidth 1
           :DropShadow true}
   :submorphs [(resize-button extent), window-bar]})
