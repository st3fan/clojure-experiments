;; hackernews-archive.clj

(ns hackernews-archive
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.contrib.sql :as sql]
            [compojure :as c])
  (:use clojure.contrib.zip-filter.xml))

;; RSS Parsing

(defn feed-to-zip [url]
  (zip/xml-zip (xml/parse url)))

(defn fetch-items []
  (xml-> (feed-to-zip "http://news.ycombinator.net/rss") :channel :item))

(defn parse-item-id-from-link [link]
  (Long/parseLong (second (re-find #"http://news.ycombinator.com/item\?id=(\d+)" link))))

(defn item-to-record [item]
  (let [item-id (parse-item-id-from-link (first (xml-> item :comments text)))]
    {:title (first (xml-> item :title text)) :id item-id :link (first (xml-> item :link text))}))

(defn parse-items []
  (map item-to-record (fetch-items)))

;; Data Access

(def *items-database* {:classname "org.hsqldb.jdbcDriver" :subprotocol "hsqldb" :subname "file:/tmp/hackernews-archive.db"})

(defn create-items-database []
  (sql/create-table :items
    [:id :int "IDENTITY" "PRIMARY KEY"]
    [:title :varchar "NOT NULL"]
    [:link :varchar "NOT NULL"]))

(defn initialize-items-database []
  (sql/with-connection *items-database*
    ;; TODO This should only happen if the table does not exist yet
    (create-items-database)))

(defn insert-or-update-items [items]
  "Store the items in the database. First check if they exist already or not."
  (sql/with-connection *items-database*
    (sql/transaction
     (dorun (map #(sql/update-or-insert-values :items ["id=?" (:id %)] %) items)))))

(defn foo [items]
  "Store the items in the database. First check if they exist already or not."
  (sql/with-connection *items-database*
    (sql/transaction
     (dorun (map #(println %) items)))))

(defn stored-items []
  (sql/with-connection *items-database*
    (sql/with-query-results results ["select * from items"]
      (doall results))))

(defn page-offset [page page-size]
  (* page-size (- page 1)))

(defn paged-reverse-stored-items [page page-size]
  (sql/with-connection *items-database*
    (sql/with-query-results
      results
      ["select * from items order by id desc limit :limit offset :offset" *page-size* (page-offset page page-size)]
      (doall results))))

(defn print-stored-items []
  (sql/with-connection *items-database*
    (sql/with-query-results results ["select * from items"]
      (map #(println (format "%d %s" (:id %) (:title %))) results))))

;; Periodic task

(defn update-items []
  (insert-or-update-items (parse-items)))

;; Debugging

(defn print-items [items]
  (for [item items]
    (println (format "Inserting %d %s" (:id item) (:title item)))))

;; Web

(def *page-size* 25)

(defn html-document [title & body]
  (c/html
   (c/doctype :html4)
   [:html
    [:head
     [:title title]]
    [:body body]]))

(defn items-page [page]
  (html-document
    "Items"
    (c/html
     [:ul
      (for [item (paged-reverse-stored-items page *page-size*)]
        [:li
         [:a {:href (:link item)} (:title item)]])])))

(c/defroutes hackernews-archive
  (c/GET "/"
    (items-page 1))
  (c/GET "/page/:page"
    (items-page (Integer/parseInt (:page params))))
  (c/ANY "*"
    (c/html [:h1 "Page not found"])))

;; Run the server

(c/run-server {:port 8080} "/*" (c/servlet hackernews-archive))
