(ns cljs-workspace.morph.clock
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph :as morphic])
  (:import [goog.events EventType]))

(def PI js/Math.PI)

(defn get-current-time
  "current time as a map"
  []
  (let [d (js/Date.)]
    {:hours (.getHours d)
     :minutes (.getMinutes d)
     :seconds (.getSeconds d)}))

(defn angle-for-hour [hour]
  (* (+ -0.25 (/ hour 12)) PI 2))

(defn create-second-pointer [radius]
  {:id "SecondPointer"
   :morph {:Position {:x 0 :y -1.5}}
   :shape {:Fill "red"
           :StrokeWidth 2
           :Extent {:x (* 0.85 radius) :y 3}
        ;   :PathElements [{:x -1.5 :y (* radius 0.25)}, 
        ;                   {:x 0 :y (* (- radius) 0.85)},
        ;                   {:x 1.5 :y (* radius 0.25)}]
           }
   :submorphs []})

(defn create-minute-pointer [radius]
  {:id "MinutePointer"
   :morph {:Position {:x 0 :y -2}}
   :shape {:Fill "darkblue"
           :StrokeWidth 2
           :Extent {:x (* .7 radius) :y 4}}
   :submorphs []})

(defn create-hour-pointer [radius]
  {:id "HourPointer"
   :morph {:Position {:x 0 :y -2.5}}
   :shape {:Fill "darkblue"
           :StrokeWidth 2
           :Extent {:x (* .5 radius) :y 5}}
   :submorphs []})

(defn create-hour-label [label pos]
  {:id (str label "h")
   :morph {:Position pos
           :TextString label
           :FontFamily "Arial"
           :AllowInput false
           :FontSize 9}
   :shape {:ShapeClass "Text"
           :Extent {:x 17 :y 17}}
   :submorphs []})

(defn create-labels [radius]
  (mapv #(create-hour-label % (morphic/point-from-polar (* radius .8) (angle-for-hour %))) (range 1 13)))

(defn create-clock [$morph bounds]
  (let [radius (/ (bounds :x) 2)
        submorphs (conj (create-labels radius) 
                        (create-hour-pointer radius) 
                        (create-minute-pointer radius) 
                        (create-second-pointer radius))]
    {:id "Clock"
     :morph {:Position {:x 300 :y 50}
             :isDraggable true
             :fps 1
             :step (fn [self]
                     (let [{:keys [hours minutes seconds]} (get-current-time)
                           minutes (+ minutes (/ seconds 60))
                           hours (+ hours (/ minutes 60))]
                       (prn (* (/ minutes 60) 2 PI))
                       (morphic/set-rotation ($morph "HourPointer") (angle-for-hour hours) {:x 0 :y 0})
                       (morphic/set-rotation ($morph "MinutePointer") (* (+ -0.25 (/ minutes 60)) 2 PI) {:x 0 :y 0})
                       (morphic/set-rotation ($morph "SecondPointer") (* (+ -0.25 (/ seconds 60)) 2 PI) {:x 0 :y 0})))}
     :shape {:Extent bounds
             :ShapeClass "Ellipse"
             :BorderWidth 4
             :BorderColor "darkgrey"
             :Fill "linear-gradient(to bottom, #f0f0f0, #e9e9e9)"
             :DropShadow true}
     :submorphs submorphs}))