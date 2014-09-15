(ns cljs-workspace.branch-vis
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph 
              :as morphic 
              :refer [morph set-position set-fill set-extent add-morph remove-morph]]))

(enable-console-print!)
;; utilities to render a branching unrolled linked list
;; into a visual tree view. Similar to the visualization
;; of branches in a git repository (ala Github)


; renders a given branching tree (implemented as unrolled linked list)
; into a morph structure that is then passed to om and rendered to the
; given div element (div-id)

; we also only branch downwards for now (simplicity)
;         
;  ------\---\----->  main
;         \   \--\----->  1
;          \     \------>  2
;           \------->   3 ...
;
; Data structure:
;
; {:id ... :data [ . . . .  {:cont ---------------------> [ . . . . . ] 
;                            :fork | }]}
;                                  \                        
;                                   V                      
;                                  {:id ... :data [ . . . . . { . . }]}

(def current-branch-ref (atom nil)) ;; supplied by the user of the module
(def branch-callback-fn (atom (fn [branch] (prn "Clicked on me!"))))

(defn is-on-current [branch]
  (if @current-branch-ref
    (or (when (contains? branch :id)
            (= (@current-branch-ref :id) (branch :id))) 
        (when (contains? branch :root)
            (= @current-branch-ref (branch :root))))
    false))

(defn scale-branch-model [model max-width] model)

(defn render-stub [branch start-point]
  [{ ; id nessecary? not for now... we do not alter the morph structure but just rerender it completely
    :morph {:MorphClass "Path"
            :Position start-point
            :onClick (fn [e] 
                        (prn "switching via stub click to branch " (branch :id))
                        (@branch-callback-fn branch)
                        false)}
    :shape {:ShapeClass "Path"
            :StrokeWidth 5
            :Fill (if (is-on-current branch) "green" "black")
            :PathElements [{:x 0 :y 0}]}
            :submorphs []},
            20])

(defn render-bubble [origin r fill]
  {:morph {:Position origin}
   :shape {:ShapeClass "Ellipse"
           :Extent {:x r :y r}
           :Fill fill}})

(defn render-leaf [cont start-point]
  (let [fill (if (is-on-current cont) "green" "black")]
    [{ ; id nessecary? not for now... we do not alter the morph structure but just rerender it completely
      :morph {:MorphClass "Path"
              :Position start-point
              :onClick (fn [e] 
                          (if (contains? cont :root)
                            (let [b (cont :root)]
                              (prn "switching via leaf click to branch: " (b :id))
                              (@branch-callback-fn b))
                            (let [b cont]
                              (prn "switching via stub leaf click to branch: " (b :id))
                              (@branch-callback-fn b)))
                          false)}
      :shape {:ShapeClass "Path"
              :StrokeWidth 5
              :Fill fill
              :PathElements [{:x 0 :y 0} {:x (count @(cont :data)) :y 0}]}
              :submorphs [(render-bubble {:x (count @(cont :data)) :y -1} 10 fill)]}, ; place a cute bubble at the end :-)
              20]))

(defn smoothen [vertices]
  ; interpolate last 2 edges, 
  ; by substituting the last 2 points with an arc
  (prn "smoothening: " vertices)
  (let [[middle end] (take-last 2 vertices)
        half-point (- (end :x) (middle :x))
        rounded-edge {:type :arc
                      :anchors [(assoc middle :y (- (middle :y) half-point)) middle end]}]
        (conj (into [] (drop-last 2 vertices)) rounded-edge)))

(defn translat [op p1 p2]
  {:x (op (p1 :x) (p2 :x)) :y (op (p1 :y) (p2 :y))})

(defn create-arc [start end fill]
  { :morph {:MorphClass "Path"
            :Position start}
    :shape {:ShapeClass "Path"
            :StrokeWidth 5
            :Fill fill
            :PathElements (smoothen (map #(translat - % start) [start (assoc end :x (start :x)) end]))}})

(defn render-fork [branch cont fork start-point]
  (let [fill (if (is-on-current cont) "green" "black")
        end-point {:x (count branch) :y 0} ; based on start-point and number of entries
        [cont-path cont-space] (render-branch cont end-point) ; we have a fork, return a path that visualizes branching
        fork-start-point {:x (+ (end-point :x) 10) :y cont-space} ; based on end-point and space occupation of the cont
        [fork-path fork-space] (render-branch fork fork-start-point)
        arc-path (create-arc end-point fork-start-point (get-in fork-path [:shape :Fill]))]
            [{; id nessecary? not for now... we do not alter the morph structure but just rerender it completely
             :morph {:MorphClass "Path"
                     :Position start-point
                     :onClick (fn [e] 
                                (prn "switching via fork click to branch " (get-in cont [:root :id]))
                                (@branch-callback-fn (cont :root))
                                false)}
             :shape {:ShapeClass "Path"
                     :StrokeWidth 5
                     :Fill fill
                     :PathElements [{:x 0 :y 0} end-point]}
             :submorphs [cont-path arc-path fork-path]},
              (+ cont-space fork-space)]))

(defn render-branch
  ([branch]
     ; initial call to first branch assume origin as starting point
     (render-branch branch {:x 0 :y 0}))
  ([branch start-point]
     ; check if the current branch has any entries
     (if-let [end (last @(branch :data))]
        (if-let [cont (end :cont)] ; check for fork
          (let [fork (end :fork)]
            (render-fork @(branch :data) cont fork start-point))
          (render-leaf branch start-point))
        (render-stub branch start-point) ; this brunch has no entries yet
      )
  ))

(defn render-tree [tree branch branch-callback]
  ; idea, walk to the leaves of the tree, and then start traversing
  ; from there and end at the root. This way we can make sure, that we
  ; reserve enough space such that all branches are neatly spaced apart)
  (when (not= @current-branch-ref branch) (prn "have to recolor!"))
  (reset! current-branch-ref branch)
  (reset! branch-callback-fn branch-callback)
  (let [[model space] (render-branch tree)]
      ; render stuff with om...
      (prn container)
      (swap! branch-view assoc :submorphs [(scale-branch-model model 400)])))

(def branch-view (atom
  {:id "branchView"
   :shape {:Extent {:x 400 :y 100}}
   :morph {:Position {:x 50 :y 50} :isDraggable true}
   :submorphs []}))

(om/root
  (fn [app owner]
    (om/component 
      (dom/div {}
        (om/build morph app))))
branch-view 
{:target (. js/document (getElementById "branch"))})