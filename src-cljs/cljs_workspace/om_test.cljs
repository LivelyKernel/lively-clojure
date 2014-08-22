(ns cljs-workspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [cljs-workspace.morph :as morphic]
            ; [clojure.browser.repl :as repl]
            )
  (:import [goog.events EventType]))

(enable-console-print!)

(println "Hello :)")

(def app-state 
         (atom {:id 1
                :text "Hi!"
                :morph {:Position {:x 0 :y 42}}
                :shape {:BorderWidth 5
                        :BorderColor "rgb(0,0,0)"
                        :Fill "rgb(255,255,255)"
                        :Extent {:x 300 :y 300}}
                :submorphs [{:id 2
                             :morph {:Position {:x 100 :y 100}}
                             :shape {:Fill "rgb(0,0,255)"
                                     :Extent {:x 42 :y 42}}
                             :submorphs []}
                             {:id 3
                              :morph {:Position {:x 100 :y 100}}
                              :shape {:Fill "rgb(250, 250, 0)"
                                      :ShapeClass "Ellipse"
                                      :Extent {:x 100 :y 100}}}
                              {:id 5
                                :morph {:Position {:x 0 :y 0}}
                                :shape {:Extent {:x 110 :y 40}
                                        :BorderColor "rgb(92,77, 11)"
                                        :BorderWidth 2
                                        :Fill "rgb(255,244,194)"}
                                :submorphs [
                                      {:id 4
                                       :morph {:MorphClass "Text" 
                                               :Position {:x 10 :y 10}
                                               :TextString "Hallo Welt!"}
                                       :shape {:ShapeClass "Text"
                                               :Extent {:x 100 :y 30}}}]}]}))

(def text (atom {:id 4
                 :morph {:MorphClass "Text" :Position {:x 100 :y 24}}
                 :shape {:ShapeClass "Text"
                         :Extent {:x 100 :y 30}
                         :BorderColor "black"
                         :BorderWidth 2}}))

(om/root
  morphic/morph
  app-state
  {:target (. js/document (getElementById "app"))})

;; snapshot the state when it is changed:

(def app-history (atom [@app-state]))

(defn pluralize [n w]
  (if (> n 1) 
      (str w "s")
      (str w)))

(add-watch app-state :history
  (fn [_ _ _ n]
    (when-not (= (last @app-history) n)
      (swap! app-history conj n))
    (let [c (count @app-history)]
        (prn (str c " Saved " (pluralize c "State"))))))

(om/root
  (fn [app owner]
    (om/component (dom/button #js {:onClick undo} "Undo!")))
  app-state 
  {:target (. js/document (getElementById "inspector"))})

  (defn undo [e]
    (when (> (count @app-history) 1)
      (swap! app-history pop)
      (reset! app-state (last @app-history))))

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