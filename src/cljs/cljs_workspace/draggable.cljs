(ns cljs-workspace.draggable
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            ; [clojure.browser.repl :as repl]
            )
  (:import [goog.events EventType]))

(def clicked-morph
  (atom nil))

(def prev-cursor-pos
  (atom [0 0]))

;; draggin functions 

(declare shape)
(declare render-submorphs)

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type #(put! out %))
    out))

(defn element-offset [el]
  (let [offset (gstyle/getPageOffset el)]
    [(.-x offset) (.-y offset)]))

(defn location [e]
  [(.-clientX e) (.-clientY e)])

(defn is-draggable [self]
  (and (get-in self [:morph :isDraggable])))

(defn is-clicked [self]
  (= (self :id) @clicked-morph))

(defn drag [e ->self offset]
  (let [self @->self
        old-pos (get-in self [:morph :Position])
        [prev-cursor-x, prev-cursor-y] @prev-cursor-pos
        [delta-x, delta-y] [(- (.-clientX e) prev-cursor-x) (- (.-clientY e) prev-cursor-y)]
        [new-pos-x, new-pos-y] [(+ (old-pos :x) delta-x) (+ (old-pos :y) delta-y)]]
    (when (and (is-clicked self) (is-draggable self))
      (swap! prev-cursor-pos #(identity [(.-clientX e) (.-clientY e)]))
      (if-not 
        (if-let [callback (get-in self [:morph :onDrag])]
            ; custom behavior
            (callback ->self {:x new-pos-x :y new-pos-y})
            false)
        ; default behavior
        (om/update! ->self [:morph :Position] {:x new-pos-x :y new-pos-y})))))

(defn stop-dragging [e ->self owner]
  (swap! clicked-morph #(identity nil))
    (doto js/window
      (events/unlisten EventType.MOUSEUP stop-dragging)
      (events/unlisten EventType.MOUSEMOVE #(drag % ->self nil)))) ;; let the event propagate further down ??

(defn start-dragging [e ->self owner]
  (swap! prev-cursor-pos #(identity [(.-clientX e) (.-clientY e)]))
  (when (and (is-draggable @->self) (not @clicked-morph)) 
    (swap! clicked-morph #(@->self :id)))
    ;; start listening for mouse movements in the window in case the mouse cursor
    ;; escapes temporarily from the dragged morph
  (let [[offset-x, offset-y] (element-offset (om/get-node owner))]
    (doto js/window
      (events/listen EventType.MOUSEUP stop-dragging)
      (events/listen EventType.MOUSEMOVE #(drag % ->self [offset-x offset-y]))))) ;; let the event propagate further down
