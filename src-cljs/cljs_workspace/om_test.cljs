(ns cljs-workspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            ; [clojure.browser.repl :as repl]
            )
  (:import [goog.events EventType]))

(enable-console-print!)

(println "Hello :)")

; (repl/connect "http://localhost:9000/repl")

; now parse a description from a stringified buildSpec

; sample buildSpec

(def clicked-morph
  (atom nil))

(def app-state 
         (atom {:id 1
                :text "Hi!"
                :morph {}
                :shape {:BorderWidth 5
                        :BorderColor "rgb(0,255,0)"
                        :Fill "rgb(255,0,0)"
                        :Position {:x 0 :y 0}
                        :Extent {:x 100 :y 100}}
                :submorphs [{:id 2
                             :morph {}
                             :shape {:Fill "rgb(0,0,255)"
                                     :Position {:x 42 :y 42}
                                     :Extent {:x 42 :y 42}}
                             :submorphs []}]}))

; morph property to CSS property translators

(defn get-fill [value]
  {"background" value})

(defn get-position [value]
   {"position" "absolute" 
    "left" (:x value)
    "top" (:y value)})

(defn get-extent [value]
  {"height" (:y value)
   "width" (:x value)})

(defn get-border-width [value]
  {"border-width" value
   "border-style" "solid"})

(defn get-border-color [value]
  {"border-color" value
   "border-style" "solid"})

; translation from morph data to CSS style prop

(defn convertToJsObj [dict]
  (apply js-obj (apply concat (seq dict))))

(defn extract-props [state]
    (convertToJsObj {"style" 
      (convertToJsObj (apply merge 
        (map (fn [[prop value]] 
            (case prop 
              :Fill (get-fill value)
              :Position (get-position value)
              :Extent (get-extent value)
              :BorderWidth (get-border-width value)
              :BorderColor (get-border-color value)
              ;; more to come... 
              )) state)))}))

; morph component + rendering functions

(declare shape)
(declare render-submorphs)

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type #(put! out %))
    out))

(defn morph [app owner]
  (reify
    om/IWillMount
    (will-mount [this]
      (let [mouse-move
              (async/map
                (fn [e] [(.-clientX e) (.-clientY e) (@app :id)])
                [(listen js/window EventType/MOUSEMOVE)])
            mouse-down
              (async/map
                (fn [e] (swap! clicked-morph (fn [_] (@app :id))))
                [(listen js/window EventType/MOUSEDOWN)])
            mouse-up
              (async/map
                (fn [e] (swap! clicked-morph (fn [_] (@app :id))))
                [(listen js/window EventType/MOUSEUP)])]
        (go (while true
              (om/update! app :mouse (<! mouse-move))))))
    om/IRender
    (render [this]
      (prn @clicked-morph)
      (dom/div (extract-props (:morph app)) (shape app)))))

(defn shape [app]
  (dom/div (extract-props (:shape app)) (render-submorphs app)))

(defn render-submorphs [app]
  (dom/div #js {:className "originNode"}
    (apply dom/div nil
      (om/build-all morph (:submorphs app)))))

(om/root
  morph
  app-state
  {:target (. js/document (getElementById "app"))})