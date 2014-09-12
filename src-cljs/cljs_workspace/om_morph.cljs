(ns cljs-workspace.morph
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [goog.dom :as gdom]
            [cljs-workspace.draggable :as draggable :refer [clicked-morph]]
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

; property accessors

(defn find-morph-path
  ([morph-model id] (find-morph-path morph-model id []))
  ([morph-model id path]
    (when-let [submorph (get-in morph-model path)]
      (if (== id (submorph :id)) 
        path
        (if-let [submorphs (submorph :submorphs)]
          (some #(find-morph-path morph-model id 
            (concat path [:submorphs %])) (range (count submorphs)))
          nil)))))

(defn get-prop-path [model id attrPath]
  (into [] (concat (find-morph-path @model id) attrPath)))

(defn set-position [model id pos]
  (let [prop-path (get-prop-path model id [:morph :Position])]
    (swap! model assoc-in prop-path pos)))

(defn add-morph [model id morph]
  (let [prop-path (get-prop-path model id [:submorphs])
        submorphs (get-in @model prop-path)]
    (swap! model assoc-in prop-path (conj submorphs morph))))

(defn remove-morph [model id morph-id]
  (let [prop-path (get-prop-path model id [:submorphs])
        submorphs (get-in @model prop-path)]
    (swap! model assoc-in prop-path (into [] (filter #(not= (% :id) morph-id) submorphs)))))

(defn set-fill [model id color]
  (let [prop-path (get-prop-path model id [:shape :Fill])]
    (swap! model assoc-in prop-path color)))

(defn set-extent [model id extent]
  (let [prop-path (get-prop-path model id [:shape :Extent])]
    (swap! model assoc-in prop-path extent)))

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
                     :onClick #(when-let [cb (get-in @app [:morph :onClick])] (cb %))
                     :onMouseDown #(draggable/start-dragging % app owner)
                     :onMouseUp #(draggable/stop-dragging % app)
                    } 
                 (shape app owner))))))

;; multi method for shape
(defn handle-input [e owner app]
  (let [span (om/get-node owner "myInput")]
    (prn (.-innerText span))
    (om/update! app [:morph :TextString] (.-innerText span) :update)))

(defn save-input [e owner app]
  (prn (.-key e))
  false)

(defn create-text-node [app owner]
    (dom/div #js {:className "visible Selection"
                  :contentEditable true
                  :onMouseDown #(swap! clicked-morph (fn [_] @app :id))
                  :onInput #(handle-input % owner app)
                  ; :onKeyDown #(when (.-metaKey %) (save-input % owner app))} (dom/span #js {
                    :ref "myInput"} (get-in app [:morph :TextString])))

(defn to-svg-attr [elements]
  (let [car (first elements)
        cdr (rest elements)]
    (str "M" (car :x) "," (car :y) " "
      (reduce str (map #(str "L" (% :x) "," (% :y) " ") cdr)))))

(defn render-path-node [app owner]
  (let [vertices (get-in app [:shape :PathElements])
        ulx (apply min (map #(% :x) vertices))
        uly (apply min (map #(% :y) vertices))
        lrx (apply max (map #(% :x) vertices))
        lry (apply max (map #(% :y) vertices))
        half-stroke (/ (get-in app [:shape :StrokeWidth]) 2)
        w (Math/abs (- ulx lrx))
        h (Math/abs (- uly lry))]
  (dom/svg #js { :style #js {:position "absolute" 
                             :width (+ w (* 2 half-stroke)) 
                             :height (+ h (* 2 half-stroke)) 
                             :fill "none" 
                             :viewBox [(unchecked-negate half-stroke) 
                                       (unchecked-negate half-stroke) 
                                       (+ w half-stroke) (+ h half-stroke)] }}
    (dom/path #js {:strokeWidth (get-in app [:shape :StrokeWidth]) 
                   :stroke (get-in app [:shape :Fill]) 
                   :d (to-svg-attr vertices)}))))

(defmethod shape "Path" [app owner]
  (let [style (extract-style (:shape app))]
      (dom/div #js {:style (dict->js style) :className "Morph Path"} 
        ; we render a svg sub-tag into which we place a 
        ; <path> that covers the given PathElements
        (render-path-node app owner)
        (render-submorphs app))))
  
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