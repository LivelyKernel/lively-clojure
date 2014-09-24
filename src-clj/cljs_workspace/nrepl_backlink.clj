(ns cljs-workspace.nrepl-backlink
  (:require [clojure.tools.nrepl.server :as server]
            [clojure.tools.nrepl :as nrepl]
            [cemerick.piggieback]
            [cljs.repl.browser]))

; (defonce server (server/start-server :port 7888))

(def default-env {:port 7889
                  :client-port 9050
                  :server nil
                  :conn nil
                  :session nil
                  :browser-repl-enabled false
                  :browser-repl-env nil})

(defn- reset-env
  [env]
  (reset! env default-env))

(defn running?
  [env]
  (let [{server :server} @env]
    (not (or
          (nil? server)
          (.isClosed (:server-socket server))))))

(defn start-server
  [env]
  (when (running? env) (throw (Exception. "Server already running")))
  (let [{port :port} @env
        server (server/start-server
                :port port
                :handler (apply server/default-handler
                                #'cemerick.piggieback/wrap-cljs-repl
                                ; (map resolve cider.nrepl/cider-middleware)
                                server/default-middlewares))
        conn (nrepl/connect :port port)
        session (nrepl/client-session (nrepl/client conn Long/MAX_VALUE))]
    (.setReuseAddress (:server-socket server) true)
    (swap! env #(assoc % :server server :conn conn :session session))))

(defn stop-server
  [env]
  (when-not (running? env) (throw (Exception. "Server not running")))
  (let [{server :server conn :conn} @env]
   (.close conn)
   (server/stop-server server)))

(defn cljs-eval
  [env form]
  (when-not (running? env) (throw (Exception. "Server not running")))
  (let [{session :session} @env
        {browser-repl-env :browser-repl-env} @env
        ; result (doall (nrepl/message session {:op "eval" :code (str form)}))
        result (cemerick.piggieback/cljs-eval browser-repl-env form :verbose true)]
    result))

(defn start-browser-repl-default
  [env]
  (let [port (:client-port @env)
        browser-repl-env (cljs.repl.browser/repl-env :port port)]
    (cemerick.piggieback/cljs-repl :repl-env browser-repl-env)
    (swap! env #(assoc % :browser-repl-enabled true))
    (swap! env #(assoc % :browser-repl-env browser-repl-env))))

(defn start-browser-repl
  [env]
  (start-browser-repl-default env))

(defn stop-browser-repl
  [env]
  (when (:browser-repl-enabled env)
    (cljs-eval env :cljs/quit)
    (swap! env #(assoc % :browser-repl-enabled false))))


(defn stop
  [env]
  (try (stop-browser-repl env)
       (catch Exception e
         (println "Could not stop the browser repl: " e)))
  (stop-server env)
  @(future (loop []
             (Thread/sleep 100)
             (when-not (.isClosed (:server-socket (:server @env)))
               (recur))))
  env)

(defn start-with-env
  [env & {:keys [cljs-connect] :or {cljs-connect true} :as options}]
  (when (running? env) (stop env))
  (try
   (start-server env)
   (when cljs-connect (start-browser-repl env))
   (catch Exception err
     (do
       (try (stop-server env) (catch Exception _))
       (throw err))))
  env)

(defn start
  [& options]
  (apply start-with-env (atom default-env) options))

(comment 

  (require '[clojure.tools.nrepl :as repl])
  (require '[clojure.tools.nrepl.server :as server])
  (require '[cljs-workspace.nrepl-backlink :as nrepl-local])
  (server/stop-server nrepl-local/server)

  ; (doc repl/client)

  (with-open [conn (repl/connect :port 7888)]
     (-> (repl/client conn 1000)
       (repl/message {:op :eval :code "(+ 1 1)"})
        repl/response-values))
)