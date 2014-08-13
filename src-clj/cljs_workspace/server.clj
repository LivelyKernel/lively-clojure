(ns cljs-workspace.server
  (:require [org.httpkit.server :as http]
            [compojure.route :as route]
            [compojure.core :as comp]))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello!"})

(comp/defroutes all-routes
  (comp/GET "/" [] app)
  (route/resources "/")
  (route/not-found "<p>Page not found.</p>"))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ; server is an atom that includes the server shutdown function
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server []
  (when server (stop-server))
  (reset! server (http/run-server #'all-routes {:port 8080})))

(comment
  (star-server)
  (stop-server)
  )
