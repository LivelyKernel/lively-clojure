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

(defn close-box [$morph target-id]
  {:id "closeBox"
   :morph {:Position {:x 10 :y 10}
           :onMouseEnter (fn [self _] 
                           (let [x-box (-> self :>> :submorphs first)]
                             (om/update! x-box [:morph :Visible] true)))
           :onMouseLeave (fn [self _] 
                           (let [x-box (-> self :>> :submorphs first)]
                             (om/update! x-box [:morph :Visible] false)))
           :onClick (fn [self _]
                      (let [->target ($morph target-id)]
                        (morphic/remove-morph ($morph "World") target-id)))}
   :shape {:ShapeClass "Ellipse"
           :Extent {:x control-height :y control-height}
           :Fill "#ff6052"}
   :submorphs [{:morph {:Position {:x -4 :y -8}
                        :TextString "×"
                        :FontSize 10 
                        :AllowInput false
                        :Visible false}
                :shape {:ShapeClass "Text"}}]})

(defn min-box [$morph target-id]
  {:id "minBox"
   :morph {:Position {:x 30 :y 10}
           :onMouseEnter (fn [self _] 
                           (let [x-box (-> self :>> :submorphs first)]
                             (om/update! x-box [:morph :Visible] true)))
           :onMouseLeave (fn [self _] 
                           (let [x-box (-> self :>> :submorphs first)]
                             (om/update! x-box [:morph :Visible] false)))
           :onClick (fn [self]
                      ; Fixme: Double hopping not yet possible, as parent refs are local to morphs
                      (let [->target ($morph target-id)
                            width (-> ->target :shape :Extent :x)]
                        (morphic/set-extent ->target {:x width :y 30})))}
   :shape {:ShapeClass "Ellipse"
           :Extent {:x control-height :y control-height}
           :Fill "#ffbe06"}
   :submorphs [{:morph {:Position {:x -4 :y -8.5}
                        :TextString "−"
                        :FontSize 10 
                        :AllowInput false
                        :Visible false}
                :shape {:ShapeClass "Text"}}]})

(defn window-bar [$morph target-id]
  {:id "windowbar"
   :morph {:Position {:x 0 :y 0}}
   :shape {:Extent {:x 200 :y 30}}
   :submorphs [(name-morph target-id) (min-box $morph target-id) (close-box $morph target-id)]})

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

(defn create-window [{name :name, position :position, extent :extent, $morph :$morph}] 
  {:id name
   :morph {:Position position
           :isDraggable true}
   :shape {:Extent extent
           :BorderColor "grey"
           :Fill "linear-gradient(to bottom, #f0f0f0, #e9e9e9)"
           :BorderRadius 10
           :BorderWidth 1
           :DropShadow true}
   :submorphs [(resize-button extent), (window-bar $morph name)]})
