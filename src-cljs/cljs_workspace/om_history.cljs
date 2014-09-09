(ns cljs-workspace.history
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph 
              :as morphic 
              :refer [set-position set-fill set-extent add-morph remove-morph]]))

(enable-console-print!)

(defn pluralize [n w]
  (if (> n 1) 
      (str w "s")
      (str w)))

(def diagram-width 400)
(def branch-height 20)

;; global atom referencing the current state of the app
(def app-state (atom nil))

;; global atom referencing the total history graph
(def app-history (atom [@app-state]))


(defn init-history [init-state]
  (reset! app-state init-state)
  (reset! app-history [init-state])
  (reset! (@current-branch :data) @app-history)
  (add-morph history-view (@current-branch :id) history-indicator))

(def branch-count (atom 1))

(defn generate-uuid []
  @branch-count)

;; global atom referencing the branch that we currently operate on
(def current-branch (atom {:id (generate-uuid) :data (atom @app-history)}))

;; ffd
(def history-view)

(defn switch-to-branch [branch]
  (prn "old branch id: " (@current-branch :id))
  (prn "new branch id: "  (branch :id))
  (remove-morph history-view (@current-branch :id) "indexMorph")
  (set-fill history-view (@current-branch :id) "lightgrey")
  (set-fill history-view (branch :id) "green")
  (add-morph history-view (branch :id) history-indicator)
  (reset! current-branch branch))

(defn nth-history 
  ([i] 
    (nth-history i @current-branch))
  ([i {:keys [_ data]}]
    ;; lookup in unrolled linked list
    (if (< i (dec (count @data))) 
      (nth @data i)
      (when-let [cont ((last @data) :cont)]
        (nth-history (- i (dec (count @data))) cont)
        nil))))

(defn len [unrolled-list]
  (+ -1 (count unrolled-list) 
    (when-let [end (last unrolled-list)]
      (when-let [n (end :cont)] (len n)))))

(def history-indicator
  {:id "indexMorph"
   :morph {:Position {:x (/ diagram-width 2) :y 3}}
   :shape {:Fill "lightgreen"
           :Extent {:x 4 :y branch-height }}})

(defn branch-morph [posX posY width height branch]
         {:id (branch :id)
          :morph {:Position {:x posX :y posY}
                  :onClick (fn [e] (switch-to-branch branch))}
          :shape {:Fill "green"
                  :BorderColor "darkgreen"
                  :Extent {:x width :y height}}
           :submorphs []})

(def history-view
  (atom {:id "wrapper"
         :morph {:Position {:x 10 :y 50}}
         :shape {:BorderColor "black"
                 :Extent {:x (+ diagram-width 10) :y (+ branch-height 10) }}
         :submorphs 
         [(branch-morph 5 5 (- diagram-width 0) branch-height @current-branch)]}))

(def history-slider 
  (dom/input #js {:style #js {:position "absolute" :top 5 :left 5} 
                  :type "range" :min 0 :max 100 :defaultValue 100
                  :onChange #(revert-to (.. % -target -value))}))

(defn move-indicator-to [index]
  (set-position history-view "indexMorph" {:x (* diagram-width (/ index 100)) :y 3}))

;; this variable indicates that we are currently reverted to an earlier
;; state of the branch and also which one we have reverted to
(def reverted-to (atom -1))

(defn revert-to [i]
  (prn "request revert: " i)
  (let [index (* (dec (len @(@current-branch :data))) (/ i 100))]
    (when-let [state (when (>= index 1) (nth-history index))]
      (prn "revert to: " index)
      (reset! app-state state)
      (reset! reverted-to i)
      (move-indicator-to i))))

(defn add-new-branch [branch]
    (let [new-branch-morph (branch-morph 
                          5 (+ 5 (* branch-height (dec @branch-count))) 
                         (- diagram-width 0) branch-height branch)]
      (add-morph history-view "wrapper" new-branch-morph)
      (set-extent history-view "wrapper" {:x (+ diagram-width 10) :y (+ 10 (* branch-height @branch-count))})
      (switch-to-branch branch))
    (.log js/console (morphic/dict->js @history-view)))

;; snapshot the state when it is changed:

; The history actually stores each state as a first class object, 
; even after branching happens:
;
; -----\----
;       \------
; [ . . . .  {:cont ---------------------> [ . . . . . ] 
;             :branch | }]
;                     \                        
;                      V                      
;                      [ . . . . . { . . }]

(defn branch-at [i n]
  (prn "Branching at " i)
  (swap! branch-count inc)
  (let [index i
        new-branch {:id (generate-uuid) :data (atom [])}
        ; split the current branch
        data @(@current-branch :data)
        [pre-branch post-branch] [(vec (take index data)) (vec (drop index data))]
        ; insert the branching point into the old branch
        branched-branch (into [] (concat pre-branch [{:cont post-branch :branch new-branch}]))]
      ; update the old branch with the inserted version
      (reset! (@current-branch :data) branched-branch)
      ; let current branch pointer point to new branch
      (add-new-branch new-branch)
      (reset! reverted-to -1)))

(defn save-state [n]
    (cond (> 100 @reverted-to -1 )
      ; we have to branch because a modification based on a reverted state:
      (branch-at @reverted-to n)
      ; else just append to history
      :else
      (let [b @(@current-branch :data)]
        (when-not (= (last b) n)
          (swap! (@current-branch :data) conj n))
        (let [c (len @(@current-branch :data))]
            (prn (str c " Saved " (pluralize c "State")))))))

; (om/root
;   (fn [app owner]
;     (om/component 
;       (dom/div {}
;         (om/build morphic/morph app)
;         history-slider)))
;   history-view 
;   {:target (. js/document (getElementById "inspector"))})