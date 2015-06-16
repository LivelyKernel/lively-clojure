(ns cljs-morphic.morph
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [goog.dom :as gdom])
  (:import [goog.events EventType]))

; MORPH FUNCTIONS



(defn world [function id & args]
    "Invokes the function on the morph with the given id and args."
    (apply (partial function @world-state id) args))
    
(defn without [world-state id]
   (let [path (find-morph-path world-state id)]
       (dissoc world-state path)))

(defn update [world-state id change]

    



; RENDERING
; props to css conversion

(def html5TransformProperty "WebkitTransform")
(def html5TransformOriginProperty "WebkitTransformOrigin")

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

(defn get-border-style-css [value]
  {"borderStyle" value})

(defn get-drop-shadow-css [value]
  (when value {"boxShadow" "0 18px 40px 10px rgba(0, 0, 0, 0.36)"}))

(defn get-transform-css [{rotation :Rotation, scale :Scale, pivot :PivotPoint, 
                          :or {rotation 0, scale 1, pivot {:x 0, :y 0}}}]
  {html5TransformProperty (str "rotate(" (mod (/ (* rotation 180) js/Math.PI) 360) "deg) scale(" scale "," scale ")")
   html5TransformOriginProperty (str (:x pivot) "px " (:y pivot) "px")})

(defn get-visibility-css [value]
  {"visibility" (if value "visible" "hidden")})

; Morph Elements, stateless mbuildingblocks, that just project a morph shape onto the dom

(defn render-rectangle [props & submorphs]
  )

(defn rectangle [props & submorphs]
  {:type "rectangle"
   :props props
   :submorphs submorphs})

(defn ellipse [props & submorphs]
  {:type "ellipse"
   :props props
   :submorphs submorphs})

(defn image [props & submorphs]
  {:type "image"
   :props props
   :submorphs submorphs})

(defn text [props & submorphs]
  {:type "text"
   :props props
   :submorphs submorphs})

(defn polygon [props & submorphs]
  {:type "polygon"
   :props props
   :submorphs submorphs})

; we should check the shema to weat out bugs

(declare render-rectangle 
         render-ellipse 
         render-polygon 
         render-text 
         render-image)

(defn render [morph]
  (case (:type morph)
    "rectangle" (render-rectangle morph)
    "ellipse" (render-ellipse morph)
    "text" (render-text morph)
    "image" (render-image morph)
    "polygon" (render-polygon morph)
    ; there are sure more to come ...
    ))

(defn render-rectangle [morph]
  )

(defn render-om [morph _]
  (reify
    om/IRender
    (render [self]
       (render morph))))

(def default-world-state
  (rectangle {:id "world" :extent {:x 1000 :y 1000} :fill "dargrey"}))

(def world-state (atom default-world-state))

(defn rerender [new-world-state]
  (om/update! world-state new-world-state))

(om/root
 render-om
 world-state
 {:target (. js/document (getElementById "app"))})

; can also be rewritten as:

; (comment
 
;  This is the data structure that is generated form the 
;  functional composition. Conceptually this is a isomorphic
;  representation of the original callgraph, so the corresponding
;  callgraph can always be regenerated.
;  With this we fix the only pitfall in this approach: That r
 
;  {:rect {:props {:id "world" :extent ...}
;          :submorphs [{:ellipse {:props {:extent ...}}}
;                      {:image {:props {:url }}}]}}
;  )

; (comment 
; (def world
;   (rect {:id "world" :extent {:x 1000 :y 1000}}
;         test-morph
;         hand-morph
;         (-> (rect {:id "rahmen" :extent {:x 500 :y 500} :position {:x 0 :y 0}}
;                   (-> (ellipse {:extent {:x 250 :y 250}})
;                     (clickable 
;                      (fn [world props]
;                       (world without "rahmen"))))
;                   (image {:url "mario"})
;                   (polygon {:id "hi"}))
;           (steppable (fn [world]
;                       (world update "rahmen" (fn [self props children]
;                                                 (self props
;                                                       children
;                                                       dragged)))))
;           (droppable (fn [world dragged]
;                       (update world "rahmen" (fn [self props children]
;                                                 (self props
;                                                       children
;                                                       dragged)))))
;           (clickable (fn [world] 
;                       (update world "rahmen" (fn [self props children]
;                                                 (self (update-in props :fill 
;                                                                  (fn [color] (if (color = "red")
;                                                                               "green"
;                                                                               "red")))
;                                                       children))))))
;         (-> 
;           (rect {:id "hand-morph" :extent {:x 1 :y 1} :fill "red"})
;           (mouse-moveable (fn [world mouse-position]
;                             (update world "hand-morph" 
;                               (fn [self props children]
;                                                                   (self (assoc props :position mouse-position))))))))))


; (defn create-entry [extent name]
;   (rect {:extent extent :fill "white"}
;         (text {:string name})))

; (-> list
;   (remove-element "x")
;   (remove-element "y")
;   (add-element (create-entry (get-extent list) "test")))

; (def list
;   (rect
;     (text {:id "x"})
;     (text {:id "y"})))

; (defn remove-element [list]
;   (let []))

; (pprint world)

; (render world)