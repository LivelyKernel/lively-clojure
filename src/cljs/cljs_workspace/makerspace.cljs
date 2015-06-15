(ns cljs-workspace.makerspace
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.style :as gstyle]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-workspace.morph.clock :refer [create-clock get-current-time angle-for-hour PI]]
            [cljs-workspace.morph.window :refer [create-window]]
            [cljs-workspace.morph :as morphic]
            [rksm.cloxp-com.cloxp-client :as cloxp]
            [rksm.cloxp-com.net :as net]
            [rksm.cloxp-com.messenger :as m]
            [cljs.contrib.pprint :refer [pprint]]
            [cljs.repl :as repl]
            [cljs.core.async :refer [<! >! put! close! chan pub sub]])
  (:import [goog.events EventType]))

(declare space-cursor)

(def compilation-finished (chan))

(defn $morph [id]
  (morphic/find-morph (space-cursor) id))

(def current-namespace (atom "cljs-workspace.makerspace"))

(def cljs-compile-handler
  '(do
     (require '[rksm.cloxp-cljs.ns.internals]
              '[cljs.repl :as repl]
              '[rksm.cloxp-com.cljs-repl :as cljs-repl]
              '[clojure.tools.reader :refer [read-string]]
              '[rksm.cloxp-com.messenger :as m]
              '[rksm.cloxp-com.server :as s])
     (fn [con msg]
       
       ; the clopx cljs passive repl env
       (defrecord CloxpCljsPassiveReplEnv [server client-id]
         repl/IJavaScriptEnv
         (-setup [this opts] {:status :success :value nil})
         (-evaluate [this filename line js] {:status :success :value js})
         (-tear-down [_]))
       
       (let [client-id (-> msg :data :cloxp-client)
             {:keys [server] :as connection} (s/find-connection client-id)
             compiled-js (cljs.env/with-compiler-env (:compiler-env (rksm.cloxp-cljs.ns.internals/ensure-default-cljs-env))
                           (cljs-repl/eval-cljs
                            (read-string (get-in msg [:data :exp]))
                            (->CloxpCljsPassiveReplEnv server client-id)
                            {:ns-sym (symbol (-> msg :data :required-namespace))}))]
         (m/send con (m/prep-answer-msg con msg compiled-js false))))))

(defn start-cljs-compile-service [con]
  (go
   (let [register-msg {:action "register"
                       :data {:id (:id con)
                              :cloxp-client? true
                              :services (-> con :services deref keys)
                              :document-url (. js/document -URL)}}
         add-service-msg {:action "add-service"
                          :data {:name "cljs-compile-service" :handler (str cljs-compile-handler)}}
         client-id (:id con)
         register-answer (<! (m/send con register-msg))
         add-service-answer (<! (m/send con add-service-msg))]
     (prn "Registering CLJS compilation for client: " client-id)
     (if (= "OK" (-> add-service-answer :message :data))
       (prn "Added CLJS compilation service!")
       (prn (str "CLJS compilation failed to initialize! Response: " add-service-answer))))))

(defn start-cljs-source-service [con]
  (go
   (let [add-service-msg {:action "add-service"
                          :data {:name "cljs-source-service" :handler (str cljs-compile-handler)}}
         client-id (:id con)
         add-service-answer (<! (m/send con add-service-msg))]
     (prn "Registering CLJS compilation for client: " client-id)
     (if (= "OK" (-> add-service-answer :message :data))
       (prn "Added CLJS compilation service!")
       (prn (str "CLJS compilation failed to initialize! Response: " add-service-answer))))))

(defn compile-cljs [con exp]
  (go
   (let [eval-cljs-msg {:action "cljs-compile-service"
                        :data {:exp (str exp) :cloxp-client (:id con) :required-namespace @current-namespace}}
         compile-cljs-result (<! (m/send con eval-cljs-msg))
         code (-> compile-cljs-result :message :data)
         res (js/eval code)]
     res)))

(defn compile-methods-for! [->morph]
  (let [method-descriptions (select-keys (:morph ->morph) (for [[k v] (:morph ->morph) :when (and (list? v) (= 'fn (first v)))] k))]
    (cloxp/with-con
      (fn [con]
        (go 
         (om/update! ->morph :_compiled_methods (<! (compile-cljs con method-descriptions))))))))

(defn compile-morph! [morph-id]
  (prn (str "Compiling " morph-id))
  (go
   (let [morph ($morph morph-id)]
     (<! (compile-methods-for! morph))
    (when-let [submorphs (:submorphs morph)]
      (cljs.core.async/merge (map #(compile-morph! (:id %)) submorphs))))))

(def space-state
  (atom {:id "World"
         :morph {:Position {:x 0 :y 0}}
         :shape {:Extent {:x 1280 :y 800}
                 :BorderColor "darkgrey"
                 :BorderWidth 2
                 :Fill "lightgrey"}}))

(def mario {:id "Mario"
            :shape {:ShapeClass "Image"
                    :url "http://www.veryicon.com/icon/png/Game/Super%20Mario/Super%20Paper%20Mario.png", 
                    :Extent {:x 100 :y 100}} 
            :morph {:isDraggable true 
                    :Position {:x 50 :y 50}}})

(def clock (create-clock {:x 251 :y 251}))

(defn space-cursor [] (om/root-cursor space-state))

(defn filter-morph-state [state exclusions]
  (select-keys state (for [[k v] state :when (not (contains? exclusions k))] k)))

(om/root
 morphic/morph
 space-state
 {:target (. js/document (getElementById "app"))
  :tx-listen (fn [tx-data root-cursor]
               ; notify morphs that something happened
               (when (= :new-morph (:tag tx-data))
                 (prn "New Morph added. Triggering Compilation...")
                 (let [new-morph (:id (last (:new-value tx-data)))]
                   (go 
                    (<! (compile-morph! new-morph))
                    (>! compilation-finished new-morph))))
               (when (= :new-method (:tag tx-data))
                 (prn "New Method added. Triggering Compilation...")
                 (let [new-morph (:id (:new-value tx-data))]
                   (go 
                    (<! (compile-morph! new-morph))
                    (>! compilation-finished new-morph)))))
  :instrument (fn [f x m]
                (let [morph-state (filter-morph-state x [:submorphs :_compiled_methods])
                      halo (morphic/get-halo-for x)]
                  (dom/div nil
                    (om/build* f (if (-> x :morph :Halo) 
                                   (morphic/add-morph x halo)
                                   x) m))))})

(def world-workspace (assoc-in (create-window {:position {:x 100 :y 100} 
                                            :name "Workspace" 
                                            :extent {:x 500 :y 600}}) 
                            [:morph :onResize]
                            '(fn [->self new-size]
                               (prn "resetting extent") 
                               (morphic/set-extent! ($morph "ClockWatcher") (morphic/add-points {:x -10 :y -30} new-size)))))

(def ace-editor {:id "ClockWatcher"
                 :morph {:Position {:x 5 :y 30}
                         :value (with-out-str (pprint clock))
                         :getValue '(fn []
                                      (let [clock @($morph "Clock")]
                                        (with-out-str (pprint (select-keys clock (for [[k v] clock :when (not (= :_compiled_methods k))] k))))))
                         :setValue '(fn [value] 
                                      (let [path (morphic/find-morph-path (space-cursor) "Clock")
                                            new-description (cljs.reader/read-string value)]
                                        ; in case reading the string caused an error, we do not apply the change!
                                        ; read how we can adapt ace to this
                                        (om/update! (space-cursor) path new-description :new-method)))}
                 :shape {:ShapeClass "AceMorph"
                         :Extent {:x 490 :y 570}}})

; (comment
 
;  A morph is a function taking a dictionary of morph properties
;  resulting in a visual representation of the morph.
;  A morph (ellipse / image / text ...) is an indivisble unit.
 
;  A morph shell projects data onto a morph composition.
;  Should wrap a conceptual entity, that can be resued later on.
;  Also maintains a local state, to prevent obscuring the data.
;  (analogous to components in react/om)
 
;  (morphshell shell1 [data state submorphs]  
;              (render
;               (morph1 {props} ...
;                       (morph2 {props} ...)
;                       (morph3 {props} submorphs))))
 
;  How can we recompose new morphs out of components?
;  Just use them as if they were morphs:
;   ...
;  (morph1 {props} ... ) -> (dom/div ...)
;  (shell1 data ...) -> (om/build shell1 data ...)
 
;  but as they present stateful boundaries, we need to pass them 
;  a data reference, instead of immutable props.
 
;  How do we map mouse and keyboard events to this structure?
;  If we rely on callback behavior, we have to incorporate
;  a highly sideffectful paradigm into a functional system.
 
;  A more frp like inclusion of events would look something like this:
 
;  (morph1 (map-signal (fn [value] {props}) frp-signal) ... )
 
;  morph1 should now be rendered as soon as the props can be received from
;  the signal.
 
;  Problem: Om/React sees the change of state as the essential trigger for a
;  rerendering. But FRP employs signals as the essential starting point for 
;  a reaction/rerender of the application. We need to find a middle ground
;  between signal and state based rerender, since the morphic development
;  style will cause a state explosion within the developed application if
;  we stick to the om/react paradigm.
 
;  This is important: Om from itself sees state as something that has to be
;  managed with care yet is still open to all imperative control flow constructs.
;  This works, as long as you program only with om itself and try to simplify the
;  conceptualization of a less state driven application. When we want to
;  program om in a direct manipulation / self hosted environment the prevalance of
;  stateful user interaction can only be mapped meaningful onto the om application
;  by exhausting its stateful capabilities to an extreme.
;  In some sense a Morphic IDE would amplify the statefulness of OM to an extent,
;  that completely eradicates its initial intention and raison d'être.
;  However our thesis is, that direct and live manipulation of graphical objects can
;  aid the programmer in developing a LESS stateful and MORE functional GUI application.
;  We therefore need to make morphic-cljs less state central and allow FRP signals
;  to also trigger rerenderings as well.
 
;  Alternatively, we can view the user manipulations only as higher level modifications
;  of the code, and remap them to edit operations of the morph/shell definitions.
 
;  But this devides run and dev time: Interactions done through dragging/scaling/composing
;  should not be a "higher level" kind of operation. They should just be the same
;  as the usual interactions that we define as part of the scope of the application.
 
;  In LISP modifiaction of code is possible and part of the runtime concept. "Code is Data"
;  CLJS stores the code as a read expression that is not evaluated. This allows us to understand
;  graphical user interactions as a means of modifying function composition.
 
;  Therefore a cljs-morphic IDE does not version and modify STATE of Objects such as Lively does
;  but instead operates on the function graphs that correspond to the manipulated morphs.
;  Just as lively holds a complete state and function description of each object,
;  cljs-morphic holds the state and function graph for each rendered morph.
 
;  Manipulating the morph directly only changes the function graph + state of the local morph.
 
;  (component
;   (morph1 ...
;           (component 
;           (morph2 ...))
;           (component
;           (morph3 ...)))))


(cloxp/with-con
  (fn [con]
    (go
     (<! (start-cljs-compile-service con))
    ;  (morphic/add-morph! ($morph "World") world-workspace)
    ;  (<! compilation-finished)
    ;  (morphic/add-morph! ($morph "Workspace") ace-editor)
    ;  (<! compilation-finished)
     
     (morphic/add-morph! ($morph "World") clock)
     (<! compilation-finished)
     (morphic/set-position! ($morph "Clock") {:x 800 :y 100})
     
     
     ; compile cljs
     ;(om/update! (space-cursor) (<! (compile-cljs con @(space-cursor))))
     
     (morphic/start-stepping! (om/ref-cursor ($morph "Clock"))))))