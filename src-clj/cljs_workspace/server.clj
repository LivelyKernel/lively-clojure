(ns cljs-workspace.server
  (:use ring.middleware.edn)
  (:use org.httpkit.server)
  (:require [ring.util.response :refer [file-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [compojure.route :as route]
            [compojure.core :refer [defroutes GET PUT POST]]
          ))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello!"})

(prn "Started!")

(def clients (atom {}))

(def app-state 
  (atom {:url "/data"
          :server/id 42
          :coll {:id 1
                :morph {:id 1 :Position {:x 50 :y 200}}
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
                                       :morph {:MorphClass "Text" 
                                               :Position {:x 10 :y 10}
                                               :TextString "Hallo Welt!"
                                               :isDraggable true}
                                       :shape {:ShapeClass "Text"
                                               :Extent {:x 100 :y 30}}}]}]}}))

(defn set-app-state [params origin]
  (prn origin)
  (swap! app-state assoc-in (:path params) (:new-value params))
  (doseq [client (keys @clients)]
      ;; send whole state, maybe optimize later on
      (when (not= client origin) (send! client (pr-str @app-state))))
  (generate-response {:status :ok}))

(defn updater [req]
  (with-channel req up-chan
    (swap! clients assoc up-chan true)
    (prn @clients)
    (on-receive up-chan (fn [req] 
                          (prn "websocket request: " req)
                          (set-app-state (read-string req) up-chan)))
    (on-close up-chan (fn [status]
                        (swap! clients dissoc up-chan)
                        (prn up-chan "closed, status" status)))))


(defroutes routes
  (GET "/ws" [] updater)
  (GET "/" [] app)
  (route/resources "/")
  (GET "/data" [] (generate-response @app-state))
  (PUT "/data" [] (fn [req]
    (prn req)
    (set-app-state (:params req) (:async-channel req))))
  (POST "/data" {params :edn-params} (set-app-state params nil))
  (route/not-found "<p>Page not found.</p>"))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ; server is an atom that includes the server shutdown function
    (@server :timeout 100)
    (reset! server nil)))

(def all-routes
  (-> routes
      wrap-edn-params))

(defn start-server []
  (when server (stop-server))
  (reset! server (run-server #'all-routes {:port 9081})))

(comment
  (star-server)
  (stop-server)
  )
