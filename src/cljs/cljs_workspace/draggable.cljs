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
  (= (self :id) (:id (@clicked-morph :>>))))

(defn drag [e self]
  (.preventDefault e)
  (when (and (not (nil? @clicked-morph)) (is-draggable (@clicked-morph :>>)))
    (let [old-pos (get-in (@clicked-morph :>>) [:morph :Position])
          [prev-cursor-x, prev-cursor-y] @prev-cursor-pos
          [delta-x, delta-y] [(- (.-clientX e) prev-cursor-x) (- (.-clientY e) prev-cursor-y)]
          [new-pos-x, new-pos-y] [(+ (old-pos :x) delta-x) (+ (old-pos :y) delta-y)]
          callback (get-in (@clicked-morph :>>) [:_compiled_methods :onDrag])
          do-default (or (nil? callback) (callback @clicked-morph {:x new-pos-x :y new-pos-y}))]
        ; default behavior
        (when do-default (om/update! (@clicked-morph :>>) [:morph :Position] {:x new-pos-x :y new-pos-y})))))

(defn stop-dragging [e self owner]
  (let [->self (self :>>)]
    (swap! clicked-morph #(identity nil))))

(defn start-dragging [e self owner]
  (let [->self (self :>>)]
    (swap! prev-cursor-pos #(identity [(.-clientX e) (.-clientY e)]))
    (when (and (is-draggable ->self) (not @clicked-morph)) 
      (swap! clicked-morph (fn [_] self)))))
