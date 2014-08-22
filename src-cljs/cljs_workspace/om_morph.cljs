(ns cljs-workspace.morph
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [goog.dom :as gdom]
            [cljs-workspace.draggable :as draggable]
            ; [clojure.browser.repl :as repl]
            )
  (:import [goog.events EventType]))

(enable-console-print!)

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

(defn dict->js [dict]
  (apply js-obj (apply concat (seq dict))))

(defn extract-style [state]
  (apply merge 
        (map (fn [[prop value]] 
            (case prop 
              :Fill (get-fill value)
              :Position (get-position value)
              :Extent (get-extent value)
              :BorderWidth (get-border-width value)
              :BorderColor (get-border-color value)
              ;; more to come... 
              nil
              )) state)))

; morph component + rendering functions

(defmulti shape (fn [app owner] (get-in app  [:shape :ShapeClass])))
(defmulti morph (fn [app owner] (get-in app [:morph :MorphClass])))

; utilities

(defn render-submorphs [app]
  (dom/div #js {:className "originNode"}
    (apply dom/div nil
      (om/build-all morph (:submorphs app)))))

; multimethod for morph

(defmethod morph "Text" [app owner]
  (reify
    om/IRender
    (render [this]
      (let [style (dict->js (extract-style (:morph app)))]
        (dom/div  #js {:style style
                     :className "morphNode" } (shape app owner))))))

(defmethod morph :default [app owner]
  (reify
    om/IRender
    (render [this]
      (let [style (dict->js (extract-style (:morph app)))]
        (dom/div  #js {:style style
                     :className "morphNode"
                     :onMouseDown #(draggable/start-dragging % app owner)
                     :onMouseUp #(draggable/stop-dragging % app)
                    } 
                 (shape app owner))))))

;; multi method for shape
(defn handle-input [e owner app]
  (let [span (om/get-node owner "myInput")]
    (prn (.-innerText span))
    (om/update! app [:morph :TextString] (.-innerText span))))

(defn save-input [e owner app]
  (prn (.-key e))
  false)

(defn create-text-node [app owner]
    (dom/div #js {:className "visible Selection"
                  :contentEditable true
                  :onMouseDown #(swap! clicked-morph (@app :id))
                  :onInput #(handle-input % owner app)
                  ; :onKeyDown #(when (.-metaKey %) (save-input % owner app))} (dom/span #js {
                    :ref "myInput"} (get-in app [:morph :TextString])))

(defmethod shape "Ellipse" [app owner]
  (let [style (extract-style (:shape app))]
    ;; we apply some customizations to the style, to make the shape elliptical
    (let [ellipse-style (assoc style 
                          "border-radius" (str (style "width") " /" (style "height")))]
      (dom/div #js {:style (dict->js ellipse-style)} (render-submorphs app)))))

(defmethod shape "Text" [app owner]
  (prn "Rendering Text Shape")
  (let [style (dict->js (extract-style (:shape app)))]
    (dom/div #js {:style style
                  :className "Morph Text"} (create-text-node app owner))))

(defmethod shape :default [app owner]
  (let [style (extract-style (:shape app))]
    (dom/div #js {:style (dict->js style)} (render-submorphs app))))