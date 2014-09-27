(ns cljs-workspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [cljs.reader :refer [read-string]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [om-sync.core :refer [om-sync]]
            [om-sync.util :refer [tx-tag edn-xhr]]
            [cljs-workspace.morph :as morphic :refer [set-fill toggle-halo find-morph-path]]
            [cljs-workspace.history :as history :refer [app-state init-history]]
            [cljs-workspace.repl :as repl])
            [cljs-workspace.branch-merge :as branch-merge])
  (:import [goog.events EventType]))

(enable-console-print!)

; Initialize modules

(reset! morphic/right-click-behavior 
  (fn [e state]
    (branch-merge/toggle-preserve (find-morph-path @app-state (state :id)))
    ; now also highlight all the morphs that are being preserved
    ; without changing the state
    (toggle-halo app-state (state :id))))

(prn @morphic/right-click-behavior)

(init-history 
         {:url "/data"
          :server/id 42
          :coll {:id 1
                :morph {:id 1 :Position {:x 0 :y 100}}
                :shape {:id 1
                        :BorderWidth 5
                        :BorderColor "rgb(0,0,0)"
                        :Fill "rgb(255,255,255)"
                        :Extent {:x 300 :y 300}}
                :submorphs [{:id 2
                             :morph {:Position {:x 100 :y 100} :isDraggable true}
                             :shape {:Fill "rgb(0,0,255)"
                                     :Extent {:x 42 :y 42}}
                             :submorphs []}
                             {:id 3
                              :morph {:Position {:x 100 :y 100} :isDraggable true}
                              :shape {:Fill "rgb(250, 250, 0)"
                                      :ShapeClass "Ellipse"
                                      :Extent {:x 100 :y 100}}}
                              {:id 5
                                :morph {:Position {:x 0 :y 0} :isDraggable true}
                                :shape {:Extent {:x 110 :y 40}
                                        :BorderColor "rgb(92,77, 11)"
                                        :BorderWidth 2
                                        :Fill "rgb(255,244,194)"}
                                :submorphs [
                                      {:id 4
                                       :morph {:Preserve true
                                               :MorphClass "Text" 
                                               :Position {:x 10 :y 10}
                                               :TextString "Hallo Welt!"
                                               :isDraggable true}
                                       :shape {:ShapeClass "Text"
                                               :Extent {:x 100 :y 30}}}]}]}})

(def text (atom {:id 4
                 :morph {:MorphClass "Text" :Position {:x 100 :y 24}}
                 :shape {:ShapeClass "Text"
                         :Extent {:x 100 :y 30}
                         :BorderColor "black"
                         :BorderWidth 2}
                  :submorphs []}))

(def socket 
  (atom nil))

; (defn undo [e]
;   (when (> (count @app-history) 1)
;     (swap! app-history pop)
;     (reset! app-state (last @app-history))))

; (om/root
;   (fn [app owner]
;     (om/component (dom/input #js {:type "range" :min 0 :max 100 :defaultValue 100
;                                   :onChange #(revert-to (.. % -target -value))})))
;   {} 
;   {:target (. js/document (getElementById "inspector"))})

(defn send-update [state]
  (.send @socket (pr-str (select-keys (state :tx-data) [:new-value :path]))))

(let [sub-chan (chan)]
(defn app-view [app owner opts]
    (reify
      om/IWillUpdate
      (will-update [_ next-props next-state]
        (when (:err-msg next-state)
          (js/setTimeout #(om/set-state! owner :err-msg nil) 5000)))
      om/IRenderState
      (render-state [_ {:keys [err-msg]}]
        (async/take! sub-chan send-update)
        (dom/div #js {:id "om-sync-node"}
            (om/build om-sync app
              {:opts {:view morphic/morph
                      :filter (comp #{:create :update :delete} tx-tag)
                      :id-key :id
                      :on-success (fn [res tx-data] (prn "sent update"))
                      :sync-chan sub-chan
                      :on-error
                      (fn [err tx-data]
                        (prn "Error:")
                        (reset! app-state (:old-state tx-data))
                        (om/set-state! owner :err-msg
                          "Ooops! Sorry something went wrong try again later."))}})
           (when err-msg
             (dom/div nil err-msg)))))))

(let [tx-chan (chan)
      tx-pub-chan (async/pub tx-chan (fn [tx] :txs ))]
  (prn "connect channel to server")
  (edn-xhr
    {:method :get
     :url "/data"
     :on-error (fn [res tx-data] (prn "Error: ")(println res))
     :on-complete
     (fn [res]
       (let [host (aget js/window "location" "hostname")
             port (aget js/window "location" "port")
             conn (js/WebSocket. (str "ws://" host ":" port "/ws"))]
            (set! (.-onmessage conn) (fn [e] 
              (when-let [state (read-string (.-data e))]
                (.log js/console state)
                (reset! app-state state))))
            (reset! socket conn)) ; set the socket, so that it can be used for sending things
        (when-not (nil? res) (reset! app-state res))
        (om/root app-view app-state
           {:target (. js/document (getElementById "app"))
            :shared {:tx-chan tx-pub-chan}
            :tx-listen
            (fn [tx-data root-cursor]
                (history/save-state (:new-state tx-data)) ; to enable the timeline
                (put! tx-chan [tx-data root-cursor]))}))}))

; (om/root
;   (fn [app owner]
;     (om/component 
;       (dom/div {}
;         (om/build morphic/morph app)
;         history/history-slider)))
;   history/history-view 
;   {:target (. js/document (getElementById "inspector"))})
