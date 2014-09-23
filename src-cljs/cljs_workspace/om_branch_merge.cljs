(ns cljs-workspace.branch-merge
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(def preserve-list (atom {}))
(def merge-candidate (atom nil))
(def staged-for-merge (atom {:from nil :into nil}))

(defn merge-state [remote-state local-state]
    (let [merged (merge local-state remote-state)]
      ; recover the old values, that ar on the exclude list and return
      (reduce 
        (fn [state path] (assoc-in state path (get-in local-state path))) 
        merged 
        (keys @preserve-list))))

(defn reachable-from [fp branch]
  (if (not (contains? fp :fork))
    nil ; this is not a fork point at all
    (if (= (get-in fp [:cont :root]) branch)
      [:cont]
      (if (= (fp :fork) branch) 
        [:fork]
        ; if not a single step suffices we recursively try to check if there
        ; is any path to the branch through :fork or :cont
        (if-let [path (reachable-from (last @(get-in fp [:fork :data])) branch)]
          (apply conj [:fork] path)
          (if-let [path (reachable-from (last @(get-in fp [:cont :data])) branch)]
            (apply conj [:cont] path)
            nil)))))) ; not reachable at all!

(defn depth-search-fork-point [a b root-branch]
  (let [fork-point (last @(root-branch :data))]
    (if (contains? fork-point :cont) ; check if we reached leaf or not
        (if-let [cont-reachable (depth-search-fork-point a b (fork-point :cont))]
          cont-reachable
          (if-let [fork-reachable (depth-search-fork-point a b (fork-point :fork))]
            fork-reachable
            (let [path-to-a (reachable-from fork-point a)
                  path-to-b (reachable-from fork-point b)]
              (if (and path-to-a path-to-b)
                [fork-point path-to-a path-to-b]
                nil))))
        nil)))  ; this branch does not contain a potential fork point at all
        
(defn fork-point [a b root-branch]
  (if-let [res (depth-search-fork-point a b root-branch)]
    res
    [root-branch [] []])) ; find the path to branch from root

(defn extract-branch [start-fp path] 
  (into [] (reduce (fn [[flattened fork] next-attr]
                        (let [data @(get-in fork :next-attr :data)]
                            [(concat flattened data) (last data)]))
                    [[] start-fp]
                    path)))

(defn merge-from-flattened [branch flat-data]
  (let [local-data @(branch :data)]
    (if (< (count local-data) (count flat-data))
        ; we need to either continue merging after a branching point
        ; or append new merged entries to our local-branch
        (if-let [last-entry (last local-data)]
            (if (contains? last-entry :cont) 
                ; we have to now continue merging into BOTH forking branches, as we are NOT stroing diffs
                (let [merged-part (map merge-state (butlast local-data) flat-data) 
                      cont (merge-from-flattened (last-entry :cont) (take (count local-data) flat-data))
                      fork (merge-from-flattened (last-entry :fork) (take (count local-data) flat-data))]
                    (conj merged-part {:cont {root: (last-entry :root) :data (atom cont)} 
                                       :fork {root: (last-entry :root) :data (atom fork)}}))
                ; we reached the end of the branch and just append additional states to the branch
                ; that are all merges with the last state in the branch
                (map merge-state (concat local-data (repeat last-entry)) (flat-data)))
            ; this is a weird case and the branch part we have to merge into
            ; is actually a stub and contains no entries. In that case we just
            ; append merged versions with the previous last entry to until the flattened
            ; data is used up. But this is so weird, we can just aswell return the flat-data
            (map merge-state (repeat {}) flat-data))
        ; if not we have fewer entries to be merged into, we continue
        ; merging the last state into all other proceeding local states
        (conj (map merge-state (butlast local-data) (concat flat-data (repeat (last flat-data)))) (last local-data)))))

(defn merge-branches [remote-branch local-branch root-branch]
  (prn "Merging Branch " (remote-branch :id) " into " (local-branch :id))
  (let [[fp remote-path local-path] (fork-point remote-branch local-branch root-branch)
         ; we first flatten the remote branch such that it appears to be one continuous
         ; vector of states, which makes it easier to perform the merge into our local branch
         remote (extract-branch fp remote-path)
         merged-branch (merge-from-flattened local-branch remote)]
      (prn "merged branch of len " (count local) " and " (count remote) " into size " (len merged-branch)) 
      (reset! (into-branch :data) merged-branch) ; maybe clean up the remote branch that got included?
      ))

(defn select-for-merge [branch cbk]
  ; if we select the same twice, we deselect
  (prn "select for marge: " cbk)
  (if (= @merge-candidate branch)
      (reset! merge-candidate nil)
      (if @merge-candidate 
          (if cbk 
            (do 
              (reset! staged-for-merge {:from @merge-candidate :into branch})
              (cbk @merge-candidate branch)
              (reset! merge-candidate nil))
            (merge-branch @merge-candidate branch)) ; perform merge immediately if not callback defined
          (reset! merge-candidate branch))))

(defn merge-staged-branches [root-branch]
  (let [[a b] @staged-for-merge]
    (merge-branches a b root-branch)))

(defn toggle-preserve [morph-path]
  (if (contains? @preserve-list morph-path) 
      (swap! preserve-list dissoc morph-path)
      (swap! preserve-list assoc morph-path true))
  (prn "PRESERVING: " @preserve-list))

