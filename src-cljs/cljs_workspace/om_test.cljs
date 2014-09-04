(ns cljs-workspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [cljs.core.async :as async :refer [>! <! put! chan]]
            [cljs.reader :refer [read-string]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.style :as gstyle]
            [cljs-workspace.morph :as morphic]
            [om-sync.core :refer [om-sync]]
            [om-sync.util :refer [tx-tag edn-xhr]]
            ; [clojure.browser.repl :as repl]
            )
  (:import [goog.events EventType]))

(enable-console-print!)

(println "Hello :)")

(def app-state 
         (atom {:id 1
                :morph {:id 1 :Position {:x 0 :y 42}}
                :shape {:id 1
                        :BorderWidth 5
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

(def socket 
  (atom nil))

; (om/root
;   morphic/morph
;   app-state
;   {:target (. js/document (getElementById "app"))})

;; snapshot the state when it is changed:

; The history actually stores each state as a first class object, 
; even after branching happens:
;
; -----\----
;       \------
;
; [ . . . . . {new-branch: \ old-val: _ } . . . ] <
;                           \                      \  
;                            V                      \
;                           {:offset-index _ :parent | :coll [ . . . .  ] }

(def app-history (atom {:offset-index 0 :coll (atom [@app-state])}))

(def current-branch (atom @app-history))

(def reverted-to (atom -1))

(defn pluralize [n w]
  (if (> n 1) 
      (str w "s")
      (str w)))

(defn nth-history 
  ([i] 
    (nth-history i current-branch))
  ([i branch]
    (nth @(@branch :coll) i)))

(defn branch-at [i n]
  (prn "Branching at " i)
  (let [index i
        new-branch {:offset-index i :coll (atom []) :parent (atom @current-branch)}
        ; split the current branch
        coll @(@current-branch :coll)
        [pre-branch post-branch] [(vec (take index coll)) (vec (drop index coll))]
        ; insert the branching point into the old branch
        branched-branch (into [] (concat pre-branch [{:new-branch new-branch :old-value (first post-branch)}] (rest post-branch)))]
      ; update the old branch with the inserted version
      (reset! (@current-branch :coll) branched-branch)
      ; let current branch pointer point to new branch
      (reset! current-branch new-branch)
      (reset! reverted-to -1)))

(defn save-state [n]
    (cond (> @reverted-to -1) 
      ; we have to branch because a modification based on a reverted state:
      (branch-at @reverted-to n)
      ; else just append to history
      :else
      (let [b @(@current-branch :coll)]
        (when-not (= (last b) n)
          (swap! (@current-branch :coll) conj n))
        (let [c (+ (@current-branch :offset-index) (count b))]
            (prn (str c " Saved " (pluralize c "State")))))))

(defn revert-to [i]
  (prn "request revert: " i)
  (let [index (* (dec (count @(@current-branch :coll))) (/ i 100))]
    (when-let [state (when (>= index 1) (nth-history index))]
      (prn "revert to: " index)
      (reset! app-state state)
      (reset! reverted-to index))))

; (defn undo [e]
;   (when (> (count @app-history) 1)
;     (swap! app-history pop)
;     (reset! app-state (last @app-history))))

(om/root
  (fn [app owner]
    (om/component (dom/input #js {:type "range" :min 0 :max 100 :defaultValue 100
                                  :onChange #(revert-to (.. % -target -value))})))
  {} 
  {:target (. js/document (getElementById "inspector"))})

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
              (.log js/console (read-string (.-data e)))
              (reset! app-state (read-string (.-data e)))))
            (reset! socket conn)) ; set the socket, so that it can be used for sending things
        (reset! app-state res)
        (om/root app-view app-state
           {:target (. js/document (getElementById "app"))
            :shared {:tx-chan tx-pub-chan}
            :tx-listen
            (fn [tx-data root-cursor]
                (save-state (:new-state tx-data)) ; to enable the timeline
                (put! tx-chan [tx-data root-cursor]))}))}))