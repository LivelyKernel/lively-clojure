(ns cljs-workspace.morph
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [goog.dom :as gdom]
            [cljs-workspace.draggable :as draggable :refer [clicked-morph]])
  (:import [goog.events EventType]))

(enable-console-print!)

(defn make-cursor 
  ([*atom]
     (om/to-cursor @*atom *atom []))
  ([*atom kws]
     (om/to-cursor @*atom *atom kws)))

(def html5TransformProperty "-webkit-transform")
(def html5TransformOriginProperty "-webkit-transform-origin")

; Event handling

(def right-click-behavior (atom (fn [e state] (prn "No Right Click Behavior!"))))

(def right-click-event (atom nil))

(defn not-yet-handled [event]
  (if (= @right-click-event (.-timeStamp event))
    false
    (do
      (reset! right-click-event (.-timeStamp event))
      (prn (.-timeStamp event) " ... was not yet handled!")
      true)))

(defn handle-enter [e ->self]
  (when-let [cb (get-in @->self [:morph :onMouseEnter])]
              (cb ->self)))

(defn handle-leave [e ->self]
  (when-let [cb (get-in @->self [:morph :onMouseLeave])]
              (cb ->self)))

(defn handle-click [e ->self]
  ; add/remove morph from preserve list
  ; (branch-merge/toggle-preserve state)
  ; handle the custom behavior
  (when (and (not-yet-handled e) (.-altKey e))
    (@right-click-behavior e @->self))
  (when-let [cb (get-in @->self [:morph :onClick])] 
              (cb ->self)))

; morph property to CSS property translators

(defn get-fill-css [value]
  {"background" value})

(defn get-position-css [value]
   {"position" "absolute" 
    "left" (:x value)
    "top" (:y value)})

(defn get-extent-css [value]
  {"height" (:y value)
   "width" (:x value)})

(defn get-border-width-css [value]
  {"borderWidth" value
   "borderStyle" "solid"})

(defn get-opacity-css [value]
  {"opacity" value})

(defn get-border-color-css [value]
  {"borderColor" value
   "borderStyle" "solid"})

(defn get-border-radius-css [value]
  {"borderRadius" (str value "px")})

(defn get-border-style-css [value])

(defn get-drop-shadow-css [value]
  (when value {"boxShadow" "0 18px 40px 10px rgba(0, 0, 0, 0.36)"}))

(defn get-transform-css [{rotation :Rotation, scale :Scale, pivot :PivotPoint, 
                          :or {rotation 0, scale 1, pivot {:x 0, :y 0}}}]
  {html5TransformProperty (str "rotation(" rotation ") scale(" scale ")")
   html5TransformOriginProperty (str (:x pivot) "px " (:y pivot) "px")})

(defn get-visibility-css [value]
  {"visibility" (if value "visible" "hidden")})

; translation from morph data to CSS style prop

(defn dict->js [dict]
  (apply js-obj (apply concat (seq dict))))

(defn extract-morph-css [->self]
  (apply merge 
     (let [morph-props (get ->self :morph)]
       (map (fn [[prop value]]               
          (let [value value]
             (case prop 
              :Position (get-position-css value)
              (:Scale :Rotation :PivotPoint) (get-transform-css morph-props)
              :Visible (get-visibility-css value)
              ;; more to come... 
              nil))) morph-props))))

(defn extract-shape-css [->self]
  (apply merge 
       (map (fn [[prop value]]               
          (let [value value]
             (case prop 
              :Extent (get-extent-css value)
              :BorderWidth (get-border-width-css value)
              :BorderColor (get-border-color-css value)
              :Fill (get-fill-css value)
              :Opacity (get-opacity-css value)
              :BorderRadius (get-border-radius-css value)
              :BorderStyle (get-border-style-css value)
              :DropShadow (get-drop-shadow-css value)
              ;; more to come... 
                nil))) (get ->self :shape))))

; morph component + rendering functions

; multimethod for shape
(defmulti shape (fn [app owner] (get-in app [:shape :ShapeClass])))

; multimethod for morph
(defmulti morph (fn [app owner] (get-in app [:morph :MorphClass])))

; utilities

(defn add-points [point & points]
  (reduce (fn [p1 p2] {:x (+ (p1 :x) (p2 :x)) :y (+ (p1 :y) (p2 :y))}) point points))

(defn is-visible [morph]
  (let [visible (get-in morph [:morph :Visible])]
    (if (nil? visible)
      true
      visible)))

(defn render-submorphs [self]
  (dom/div #js {:className "originNode"}
    (apply dom/div nil
      (om/build-all morph (get self :submorphs) {:state {:owner-morph self}}))))

; property accessors
    
(defn find-morph-path
 ([morph-model id] 
  (if (contains? morph-model :coll) ; this is a hack to work with om-sync applied
      (into [:coll] (find-morph-path (morph-model :coll) id []))
      (into [] (find-morph-path morph-model id []))))
  ([morph-model id path]
    (when-let [submorph (get-in morph-model path)]
      (if (== id (submorph :id)) 
        path
        (if-let [submorphs (submorph :submorphs)]
          (some #(find-morph-path morph-model id 
            (concat path [:submorphs %])) (range (count submorphs)))
          nil)))))

(defn find-morph [*model id]
  (make-cursor *model (find-morph-path @*model id)))

(defn add-morph [->owner morph]
  (let [submorphs (or (get @->owner :submorphs) [])
        owned-morph (assoc morph :owner ->owner)]
    (om/update! ->owner :submorphs (conj submorphs owned-morph))
    ; we need to continue diving down the morph hierarchy and fix
    ; parent child refs as these could have been defined in
    ; the model previously but not yet materialized
    (when-let [submorphs ()])))

(defn remove-morph [->owner morph-id]
  (let [submorphs (get @->owner :submorphs)]
    (om/update! ->owner :submorphs (into [] (filter #(not= (% :id) morph-id) submorphs)))))

(defn set-position [->morph pos]
  (om/update! ->morph [:morph :Position] pos))

(defn set-fill [->morph color]
  (om/update! ->morph [:shape :Fill] color))

(defn toggle-visibility [->morph]
  (if (is-visible ->morph)
    (om/update! ->morph [:morph :Visible] false)
    (om/update! ->morph [:morph :Visible] true)))

(defn set-extent [->morph extent]
  (om/update! ->morph [:shape :Extent] extent))

(defn set-border-color [->morph fill]
  (om/update! ->morph [:shape :BorderColor] fill))

(defn toggle-halo [->morph]
  (om/transact! ->morph [:morph :Halo] 
     (fn [halo] 
        (if halo
            false
            true))))

; render based on ad hoc definition of morph like frame
; to visualize the halo
(defn render-halo [app owner])

(defmethod morph :default [app owner]
  (reify
    om/IRender
    (render [this]
        (let [style (dict->js (extract-morph-css app))]
          (dom/div  #js {:style style
                       :className "morphNode"
                       :onClick #(handle-click % app)
                       :onMouseDown #(draggable/start-dragging % app owner)
                       :onMouseUp #(draggable/stop-dragging % app owner)
                       :onMouseEnter #(handle-enter % app)
                         :onMouseLeave #(handle-leave % app)} 
                   (when (get-in app [:morph :Halo])
                      (render-halo app owner))
                   (shape app owner))))))

;; multi method for shape

(defmethod shape "Image" [app owner]
  (let [style (dict->js (extract-shape-css app))]
    (dom/img #js {:style style
                  :draggable false
                  :src (get-in app [:shape :url])})))

  
(defmethod shape "Ellipse" [app owner]
  (let [style (extract-shape-css app)]
    ;; we apply some customizations to the style, to make the shape elliptical
    (let [ellipse-style (assoc style 
                               "borderRadius" (str (style "width") "px /" (style "height") "px"))]
      (dom/div #js {:style (dict->js ellipse-style)} (render-submorphs app)))))

(defmethod shape :default [app owner]
  (let [style (extract-shape-css app)]
    (dom/div #js {:style (dict->js style)} (render-submorphs app))))

; PATH MORPH

(defn handle-path-element [e]
  (case (e :type)
    :arc (let [[a b c] (e :anchors)] 
            (str "C" (a :x) "," (a :y) " " (b :x) "," (b :y) " " (c :x) "," (c :y) " "))
    (str "L" (e :x) "," (e :y) " ")))

(defn to-svg-attr [elements]
  (let [car (first elements)
        cdr (rest elements)]
    (str "M" (car :x) "," (car :y) " "
      (reduce str (map handle-path-element cdr)))))

(defn unpack [e]
  (case (e :type)
    :arc (e :anchors)
    e))

(defn render-path-node [app owner]
  (prn (flatten (map unpack (get-in app [:shape :PathElements]))))
  (let [vertices (flatten (map unpack (get-in app [:shape :PathElements])))
        minX (apply min (map #(% :x) vertices))
        minY (apply min (map #(% :y) vertices))
        maxX (apply max (map #(% :x) vertices))
        maxY (apply max (map #(% :y) vertices))
        half-stroke (/ (get-in app [:shape :StrokeWidth]) 2)
        w (Math/abs (- minX maxX))
        h (Math/abs (- minY maxY))]
  (dom/svg #js { :style #js {:position "absolute" 
                             :fill "none" }
                  :width (+ 1 w (* 2 half-stroke)) 
                  :height (+ 1 h (* 2 half-stroke))
                    :viewBox (str (- minX 1 half-stroke) " "
                                  (- minY 1 half-stroke) " "
                                  (+ maxX (* 2 half-stroke)) " "
                                  (+ maxY (* 2 half-stroke)))}
    (dom/path #js {:strokeWidth (get-in app [:shape :StrokeWidth]) 
                   :stroke (get-in app [:shape :Fill]) 
                   :d (to-svg-attr (get-in app [:shape :PathElements]))}))))

(defmethod shape "Path" [app owner]
  (let [style (extract-shape-css app)]
      (dom/div #js {:style (dict->js style) :className "Morph Path"} 
        ; we render a svg sub-tag into which we place a 
        ; <path> that covers the given PathElements
        (render-path-node app owner)
        (render-submorphs app))))

; TEXT MORPH

;;; spec-property -> css translation

(defn get-font-family-css [family-name]
  {"fontFamily" family-name})

(defn get-font-size-css [size]
  {"fontSize" (str size "pt")})

(defn get-max-text-height-css [height]
  {"maxHeight" (str height "px")})

(defn get-max-text-width-css [width])

(defn get-min-text-height-css [height])

(defn get-min-text-width-css [height])

(defn get-text-color-css [color]
  {"textColor" color})

(defn get-prevent-selection-css [value]
  (when (false? value) {"-webkit-user-select" "none"}))

(defn extract-text-css [->self]
     (apply merge 
       (map (fn [[prop value]]               
          (let [value value]
             (case prop 
               :FontFamily (get-font-family-css value)
               :FontSize (get-font-size-css value)
               :MaxTextHeight (get-max-text-height-css value)
               :MaxTextWidth (get-max-text-width-css value)
               :MinTextHeight (get-min-text-height-css value)
               :MinTextWidth (get-min-text-width-css value)
               :TextColor (get-text-color-css value)
               :AllowInput (get-prevent-selection-css value)
               ; :Padding ... this is actually default?
               :WordBreak ()
                ;   _WhiteSpaceHandling: {/*...*/},
               ;; more to come... 
                nil))) (get @->self :morph))))

;;; event handling

(defn handle-input [e owner app]
  (let [span (om/get-node owner "myInput")]
    (prn (.-innerText span))
    (om/update! app [:morph :TextString] (.-innerText span) :update)))

(defn save-input [e owner app]
  (prn (.-key e))
  false)

;;; rendering

(defn create-text-node [->text-morph owner]
    (dom/span #js {:className "visible Selection"
                  :contentEditable (nil? (get-in ->text-morph [:morph :AllowInput]))
                  :onMouseDown #(swap! clicked-morph (fn [_] @->text-morph :id))
                  :onInput #(handle-input % owner ->text-morph)
                  :style (dict->js (extract-text-css ->text-morph))
                  ; :onKeyDown #(when (.-metaKey %) (save-input % owner app))} (dom/span #js {
                    :ref "myInput"} (get-in ->text-morph [:morph :TextString])))

(defmethod shape "Text" [app owner]
  (let [style (extract-shape-css app)]
    (let [text-style (assoc style "cursor" "default")]
      (dom/div #js {:style (dict->js text-style)
                    :className "Morph Text"} (create-text-node app owner)))))

(defmethod morph "Text" [app owner]
  (reify
    om/IRender
    (render [this]
            (let [style (dict->js (extract-morph-css app))]
        (dom/div  #js {:style style
                       :className "morphNode" } (shape app owner))))))

;; ACE MORPH