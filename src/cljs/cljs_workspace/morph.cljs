(ns cljs-workspace.morph
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [goog.dom :as gdom]
            [cljs-workspace.draggable :as draggable :refer [clicked-morph]])
  (:import [goog.events EventType]))

(enable-console-print!)

(def constraints (atom []))

(def morph-channel (chan))

(def step-channel (chan))

(defn make-cursor 
  ([*atom]
     (om/to-cursor @*atom *atom []))
  ([*atom kws]
     (om/to-cursor @*atom *atom kws)))

(def html5TransformProperty "WebkitTransform")
(def html5TransformOriginProperty "WebkitTransformOrigin")

; Event handling

(declare toggle-halo!)

(def right-click-behavior 
  (atom 
   (fn [e ->morph]
     (toggle-halo! ->morph)
     )))

(def right-click-event (atom nil))

(defn not-yet-handled [event]
  (if (= @right-click-event (.-timeStamp event))
    false
    (do
      (reset! right-click-event (.-timeStamp event))
      (prn (.-timeStamp event) " ... was not yet handled!")
      true)))

(defn handle-resize [e self]
  (when-let [cb (-> self :>> :_compiled_methods :onResize)]
                (cb self)))

(defn handle-enter [e self]
  (when-let [cb (-> self :>> :_compiled_methods :onMouseEnter)]
                (cb self)))

(defn handle-leave [e self]
  (when-let [cb (-> self :>> :_compiled_methods :onMouseLeave)]
                (cb self)))

(defn handle-click [e self]
  ; add/remove morph from preserve list
  ; (branch-merge/toggle-preserve state)
  ; handle the custom behavior
  (let [->self (self :>>)]
  (when (and (not-yet-handled e) (.-altKey e))
    (@right-click-behavior e ->self))
  (when-let [cb (-> ->self :_compiled_methods :onClick)] (cb self))))

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
  {html5TransformProperty (str "rotate(" (mod (/ (* rotation 180) js/Math.PI) 360) "deg) scale(" scale "," scale ")")
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
              ;; more to come... :CSS for custom modifications to that very morph
                nil))) (get ->self :shape))))

; morph component + rendering functions

; multimethod for shape
(defmulti shape (fn [app owner] (get-in app [:shape :ShapeClass])))

; multimethod for morph
(defmulti morph (fn [app owner] (get-in app [:morph :MorphClass])))

; utilities

(defn point-from-polar [radius angle]
  {:x (* radius (.cos js/Math angle)) :y (* radius (.sin js/Math angle))})

(defn add-points [point & points]
  (reduce (fn [p1 p2] {:x (+ (p1 :x) (p2 :x)) :y (+ (p1 :y) (p2 :y))}) point points))

(defn is-visible [morph]
  (let [visible (get-in morph [:morph :Visible])]
    (if (nil? visible)
      true
      visible)))

; property accessors
    
(defn find-morph-path
 ([morph-model id] 
  (if (contains? morph-model :coll) ; this is a hack to work with om-sync applied
      (into [:coll] (find-morph-path (get morph-model :coll) id []))
      (into [] (find-morph-path morph-model id []))))
  ([morph-model id path]
    (when-let [submorph (get-in morph-model path)]
      (if (== id (get submorph :id)) 
        path
        (if-let [submorphs (get submorph :submorphs)]
          (some #(find-morph-path morph-model id 
            (concat path [:submorphs %])) (range (count submorphs)))
          nil)))))

(defn find-morph [->model id]
  (get-in ->model (find-morph-path @->model id)))

; SIDEEFFECTFUL SETTERS

(defn add-morph! [->owner morph]
  (let [submorphs (or (get @->owner :submorphs) [])]
    (om/update! ->owner :submorphs (conj submorphs morph) :new-morph)))

(defn remove-morph! [->owner morph-id]
  (let [submorphs (get @->owner :submorphs)
        new-submorphs (into [] (filter #(not= (% :id) morph-id) submorphs))]
    (om/update! ->owner :submorphs new-submorphs)))

(defn set-rotation! [->morph rad origin]
  (put! morph-channel :update!)
  (om/update! ->morph :morph (-> (:morph @->morph) (assoc :Rotation rad) (assoc :PivotPoint origin))))

(defn set-position! [->morph pos]
  (om/update! ->morph [:morph :Position] pos))

(defn set-fill! [->morph color]
  (om/update! ->morph [:shape :Fill] color))

(defn toggle-visibility! [->morph]
  (if (is-visible ->morph)
    (om/update! ->morph [:morph :Visible] false)
    (om/update! ->morph [:morph :Visible] true)))

(defn set-extent! [->morph extent]
  (om/update! ->morph [:shape :Extent] extent)
  (when-let [cb (-> ->morph :_compiled_methods :onResize)] (cb ->morph extent)))

(defn set-border-color! [->morph fill]
  (om/update! ->morph [:shape :BorderColor] fill))

(defn get-halo-for [morph-state]
  {:id (str "HaloOn" (:id morph-state)) 
   :morph {:Position (-> morph-state :morph :Position)}
   :shape {:Extent (-> morph-state :shape :Extent)
           :BorderColor "Red"
           :BorderWidth 1}})

(defn toggle-halo! [->morph]
  (prn "Toggel Halo!")
  (om/transact! ->morph [:morph :Halo] 
     (fn [halo] 
        (if halo
            false
            true))))

(defn start-stepping! [->morph]
  (go-loop [seconds 1]
    (<! (timeout (/ 1000 (:fps ->morph))))
    (>! step-channel :step!)
    (recur (inc seconds))))

; MORPHIC CONSTRAINTS

(defn ensure-equals [variables]
  (swap! constraints (fn [consts] (cons consts (:equals variables)))))

; Constraint Enforcement
; Once a transaction has been initiated, we verify that the constraints hold.
; Look into using relaxation algorithms for making sure that constraints are solved
; reasonably.

; MORPHIC PROTOCOL
; This protocol is required by any "object" that wants to join in the rendering chain
; of cljs-morphic. A sample "default" version of a morph object is provided, which in
; most cases should suffice. It is not really intended for this mapping to be customized
; as it might obscure the morphic runtime and make interroperability in different envs
; difficult

; -> mapping to the "dead" morphic dom
; -> managing of the om rendering (om/update! after callbacks, after method call)
; -> callback methods (mouse, drop keyboard, resize) (callback vs frp)?\
; -> custom behavior provided by functions of the form f(self , ...) -> *self* 

;
;    { morphic-dom } <--- (cljs-morphic/morph 
;                           (callbacks cb1 cb2 .... )
;                           (behavior f1 f2 .... )
;                                   :
;                                   V
;                             [OM-COMPONENTS]

; In cljs-morphic, morph references can be held by each morph locally such that he can
; send messages to other morphs via (! morph-ref :function-name args.... )
; 

(defprotocol IMorph
  (! [morph function-name args] "Perform a message send to 'morph' to execute function named 'function' with arguments 'args'")
  (perform-callback [self callback-name args] "Used internally by cljs-morphic, perform callback named 'callback-name' with given args")
  (project-morph-onto-dom [self state] "Used internally by cljs-morphic, performs the mapping of the morph's description to the dom, currently through cljs-om"))

; Default implementation

(deftype Morph [->spec callbacks behavior]
  IMorph
  (! [morph function-name args]
     (let [self (@->spec)
           *self* (apply (get behavior function-name) (conj self args))]
       (project-morph-onto-dom morph *self*)))
  (perform-callback [morph callback-name args]
      (let [self (@->spec)
           *self* (apply (get callbacks callback-name) (conj self args))]
       (project-morph-onto-dom morph *self*)))
  (project-morph-onto-dom [morph state]
       (om/transact! ->spec (fn [_] state))))

; Morphs are instanciated and then stored in a global hashmap, that maps
; the morphs ids, to the actors. From here they can be accessed globally through $morph
; and therefore referenced by other morphs through this service.
; In some sense this provides access to global state to all morphs, yet we do not
; permit direct manipulation since, we still can only interact through message sends
; and will not manipulate the state directly

; MORPH INTERACTION
; Altering another morphs state is technically possible by accessing the global state, 
; yet the cljs-morph model discourages this, by always providing the methods only with
; the (cursor derived) local definition of the morph including its submorphs.
; Access to other morphs happens through the $morph mapping or modification of own
; or one of the submorphs states by projecting self onto a new *self*

; Two different semantics for $morph :
;    (1) If we refer to one of our submorphs $morph behaves as a macro that
;        provides the rest of the control flow with the state of the submorph
;        and then just does a (assoc-in self [:path :to :submorph ... ] *new-submorph-state* (thats just a runtime cursor)
;    (2)

; FUNCTIONAL SETTERS
; In cljs-morphic, we perform changes to morph properties by following the functional
; ideom of carefully managing state, i.e. projecting the old state onto the new state
; of a morph. Consequently all property "setters" are actually sideffectless projections.
; All Methods that "belong" to a morph are provided with an argument 'self' that contains
; the current version of the morphs state. For every method belonging to a morph, we interpret
; the return value as a new version of that morphs state, which will be applied through
; om/transact!. NOTE: This removes the details of transitioning between morph states,
; which is why we need tools to inspect property changing functions in more detail

(defn add-morph [owner morph]
  (let [submorphs (or (get owner :submorphs) [])]
    (assoc owner :submorphs (conj submorphs morph))))

(defn remove-morph [owner morph-id]
  (let [submorphs (get owner :submorphs)
        new-submorphs (into [] (filter #(not= (% :id) morph-id) submorphs))]
    (assoc owner :submorphs new-submorphs)))

(defn set-rotation [self rad origin]
  (-> (get self :morph) 
      (assoc :Rotation rad) 
      (assoc :PivotPoint origin)))

(defn set-position [self pos]
  (assoc-in self [:morph :Position] pos))

(defn set-fill [self color]
  (assoc-in self [:shape :Fill] color))

(defn toggle-visibility [self]
  (assoc-in self [:morph :Visible] (not (is-visible self))))

(defn set-extent [self extent]
  (assoc-in self [:shape :Extent] extent))

(defn set-border-color [self fill]
  (assoc-in self [:shape :BorderColor] fill))

(defn toggle-halo [self]
  (assoc-in self [:morph :Halo] 
     (fn [halo] 
        (if halo
            false
            true))))

(defn start-stepping [self]
  (assoc-in self [:morph :isStepping] true))

(defn stop-stepping [self]
  (assoc-in self [:morph :isStepping] true))

; render based on ad hoc definition of morph like frame
; to visualize the halo

(defn render-submorphs [self]
  (apply dom/div nil
    (om/build-all morph (get self :submorphs) {:state {:owner (om/ref-cursor self)}
                                               :key :id})))

(defmethod morph :default [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:owner nil :step nil})
    om/IDidMount
    (did-mount [_]
               (let [state (assoc (om/get-state owner) :>> (om/ref-cursor app))]
                 (go
                  (while true
                    (<! step-channel)
                    (when-let [step-fn (om/get-state owner :step)]
                      (step-fn state))))))
    om/IRenderState
    (render-state [_ state]
                  (let [state (assoc state :>> (om/ref-cursor app))
                        style (dict->js (extract-morph-css app))]
                    (dom/div  #js {:style style
                                   :className "morphNode"
                                   :onClick #(handle-click % state)
                                   :onMouseDown #(draggable/start-dragging % state owner)
                                   :onMouseUp #(draggable/stop-dragging % state owner)
                                   :onMouseMove #(draggable/drag % state)
                                   :onMouseEnter #(handle-enter % state)
                                   :onMouseLeave #(handle-leave % state)} 
                      (om/set-state-nr! owner :step (-> app :_compiled_methods :step))
                      (shape app owner))))))

;; multi method for shape

(defmethod shape "Image" [app owner]
  (let [style (dict->js (extract-shape-css app))]
    (dom/img #js {:style style
                  :draggable false
                  :src (get-in app [:shape :url])}
      (dom/div #js {:className "originNode"} (render-submorphs app)))))

  
(defmethod shape "Ellipse" [app owner]
  (let [style (extract-shape-css app)]
    ;; we apply some customizations to the style, to make the shape elliptical
    (let [ellipse-style (assoc style "borderRadius" (str (style "width") "px /" (style "height") "px"))
          offset (get-in app [:shape :Extent])
          x-offset (str (/ (:y offset) 2) "px !important")
          y-offset (str (/ (:x offset) 2) "px !important")]
      (dom/div #js {:style (dict->js ellipse-style)}
        (dom/div #js {:className "originNode"
                      :style #js {:position "absolute",
                                  :top y-offset,
                                  :left x-offset,
                                  :marginTop "-2px",
                                  :marginLeft "-2px"}}
          (render-submorphs app))))))

(defmethod shape :default [app owner]
  (let [style (extract-shape-css app)]
    (dom/div #js {:style (dict->js style)} 
      (dom/div #js {:className "originNode"} (render-submorphs app)))))

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
        (dom/div #js {:className "originNode"} (render-submorphs app)))))

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
  (when (false? value) {"WebkitUserSelect" "none"}))

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

(defmethod morph "Text" [app owner opts]
  (reify
    om/IRender
    (render [this]
            (let [style (dict->js (extract-morph-css app))]
        (dom/div  #js {:style style
                       :className "morphNode" } (shape app owner))))))

;; ACE MORPH

; Note that from a rendering perspective this is a dead end currently. Since we leave the om managed environment,
; we can not support submorphs of an AceMorph in any meaningful way.

; state is useful to prevent the "data/model" from unnessecary polluition such as scroll
; position or other tiny bits of information that need to be stored over rendering cycles,
; but are not "essential" for the information or the "view" onto this specific part of
; the model that the component represents

; the morph model is currently local state agnostic, but it makes sense to let foreign js-libraries
; such as ace, operate mostly on local state so that they are not reinitialized by om-rerender cycles
; as soon as they store local information or other "reactive + stateful" behavior.

; which begs an interesting wuestion

(defn set-value! [ace-instance value]
  (let [cursor (.getCursorPositionScreen ace-instance)]
    (.setValue ace-instance value cursor)))

(defn change-handler [ace-instance owner]
  (om/set-state-nr! owner :edited-value (.getValue ace-instance)))

(defn save-handler [ace-instance owner]
  (let [editor-chan (om/get-state owner :editor-chan)]
    (put! editor-chan :save!)))

(defn start-checking-updates [self owner]
  (let [editor-chan (om/get-state owner :editor-chan)
        update-chan (om/get-state owner :update-chan)
        ace-instance (.edit js/ace (.getDOMNode owner))]
    (go
     (while true
       (when (and (= :update! (<! update-chan)) (not (om/get-state owner :changed)))
         (set-value! ace-instance ((-> self :_compiled_methods :getValue))))))
    (go
     (while true
       (when (= :save! (<! editor-chan))
         (when-let [edited-value (om/get-state owner :edited-value)]
           ((-> self :_compiled_methods :setValue) edited-value)))))))

(defn ace-editor [self owner]
  (reify
    om/IRender
    (render [_]
       (dom/div #js {:id "ace" 
                     :style #js {:height (-> self :shape :Extent :y) :width (-> self :shape :Extent :x)} 
                     :className "ace"
                     :onFocus (fn [e] 
                                (om/set-state-nr! owner :changed true))
                     :onBlur (fn [e] 
                                (om/set-state-nr! owner :changed false))
                     :onKeyDown (fn [e]
                                  (.log js/console (.-which e)))}))
    om/IInitState
    (init-state [_] {:editor-chan (chan) :update-chan morph-channel :changed false})
    om/IDidMount
    (did-mount [_]
               (let [ace-instance (.edit js/ace
                                         (.getDOMNode owner))]
                 (om/set-state-nr! owner :ace-instance ace-instance)
                 
                 (.. ace-instance
                       getSession
                       (on "change" #(change-handler ace-instance owner)))
                   (.. ace-instance
                       -commands
                       (addCommand #js {:name "save"
                                        :bindKey #js {:win "Ctrl-S" :mac "Ctrl-S" :sender "editor|cli"}
                                        :exec #(save-handler ace-instance owner)}))
                 
                 ; listen for cmd+s to apply changes
                 (when-let [ms (-> self :_compiled_methods)]
                     (set-value! ace-instance ((:getValue ms))))))
    om/IWillUpdate
    (will-update [_ next-self next-state]
                 (let [ace-instance (:ace-instance next-state)]
                   (.resize ace-instance)
                   (when (not (= (:_compiled_methods self) (:_compiled_methods next-self)))
                     (start-checking-updates next-self owner))
                   (when-let [ms (-> next-self :_compiled_methods)]
                     (when (not (:changed next-state)) 
                       (set-value! ace-instance ((:getValue ms)))))))))

(defmethod shape "AceMorph" [self owner]
  (om/build ace-editor self))

; Script morphs are entrypoints to include foreign js libraries such as ace or google maps
; into the cljs-morphic hierarchy. 

(defmethod morph "ScriptMorph" [self owner])