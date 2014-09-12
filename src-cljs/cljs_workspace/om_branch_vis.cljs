(ns cljs-workspace.branch-vis
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph 
              :as morphic 
              :refer [morph set-position set-fill set-extent add-morph remove-morph]]))

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

(defn scale-branch-model [model max-width] model)

(defn render-stub [start-point]
  [{ ; id nessecary? not for now... we do not alter the morph structure but just rerender it completely
    :morph {:MorphClass "Path"
            :Position start-point}
    :shape {:ShapeClass "Path"
            :StrokeWidth 10
            :Fill "black"
            :PathElements [{:x 0 :y 0}]}
            :submorphs []},
            10])

(defn render-leaf [cont start-point]
  [{ ; id nessecary? not for now... we do not alter the morph structure but just rerender it completely
    :morph {:MorphClass "Path"
            :Position start-point}
    :shape {:ShapeClass "Path"
            :StrokeWidth 10
            :Fill "black"
            :PathElements [{:x 0 :y 0} {:x (count cont) :y 0}]}
            :submorphs []},
            10])

(defn render-fork [branch cont fork start-point]
  (let [end-point {:x (count branch) :y 0} ; based on start-point and number of entries
        [cont-path cont-space] (render-branch @cont end-point) ; we have a fork, return a path that visualizes branching
        fork-start-point {:x (end-point :x) :y cont-space} ; based on end-point and space occupation of the cont
        [fork-path fork-space] (render-branch @(fork :data) fork-start-point)]
            [{; id nessecary? not for now... we do not alter the morph structure but just rerender it completely
             :morph {:MorphClass "Path"
                     :Position start-point}
             :shape {:ShapeClass "Path"
                     :StrokeWidth 10
                     :Fill "black"
                     :PathElements [{:x 0 :y 0} end-point fork-start-point]}
             :submorphs [cont-path fork-path]},
              (+ cont-space fork-space)]))

(defn render-branch
  ([branch]
     ; initial call to first branch assume origin as starting point
     (render-branch branch {:x 0 :y 0}))
  ([branch start-point]
     ; check if the current branch has any entries
     (if-let [end (last branch)]
        (if-let [cont (end :cont)] ; check for fork
          (let [fork (end :fork)]
            (render-fork branch cont fork start-point))
          (render-leaf branch start-point))
        (render-stub start-point) ; this brunch has no entries yet
      )
  ))

(defn render-tree [tree div-id]
  ; idea, walk to the leaves of the tree, and then start traversing
  ; from there and end at the root. This way we can make sure, that we
  ; reserve enough space such that all branches are neatly spaced apart)
  (let [[model space] (render-branch tree)
      ; make the wrapper large enough to fit the whole tree
         container {:id "branchView"
                    :shape {:Extent {:x 400 :y space}}
                    :morph {:Position {:x 50 :y 600} :isDraggable true}
                    :submorphs [(scale-branch-model model 400)]}]
      ; render stuff with om...
      (prn container)
      (reset! branch-view container)))

(def branch-view (atom
  {:id "branchView", :shape {:Extent {:x 400, :y 10}}, :morph {:Position {:x 50 :y 600} :isDraggable true}, :submorphs [{:morph {:MorphClass "Path", :Position {:x 0, :y 0}}, :shape {:ShapeClass "Path", :PathElements [{:x 0, :y 0} {:x 57, :y 0}] :Fill "black" :StrokeWidth 10}, :submorphs []}]} 
  ))

(om/root
  (fn [app owner]
    (om/component 
      (dom/div {}
        (om/build morph app))))
branch-view 
{:target (. js/document (getElementById "branch"))})