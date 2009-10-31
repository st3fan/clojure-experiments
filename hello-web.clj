
(ns hello-web
  (:require [compojure :as c]))

(def *names* ["Stefan" "Aura" "Miki"])

(defn html-document [title & body]
  (c/html
   (c/doctype :html4)
   [:html
    [:head
     [:title title]]
    [:body body]]))

(defn names-page []
  (html-document
    "All Names"
    (c/html [:ul (for [name *names*] [:li name])])))

(c/defroutes greeter
  (c/GET "/"
    (c/html [:h1 "Hello, world?"]))
  (c/GET "/names"
    (names-page))
  (c/ANY "*"
    (c/html [:h1 "Page not found"])))

(c/run-server {:port 8080} "/*" (c/servlet greeter))

