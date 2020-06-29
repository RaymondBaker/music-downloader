(ns music-downloader.lastfm
  (:gen-class)
  (:require [clj-http.client :as client])
  (:require [clj-http.util :as clj-util])
  (:require [clojure.string :as str])
  (:require [clojure.pprint :as pp])
  (:require [clojure.java.io :as io])
  (:require [clojure.data.json :as json]))



(defn capitalize-words
  "Capitalize every word in a string"
  [s]
  (->> (str/split (str s) #"\b")
       (map str/capitalize)
       str/join))

(def headers {"User-Agent" "Mozilla/5.0 (platform; rv:geckoversion) Gecko/geckotrail Firefox/firefoxversion"})
(def lastfm_key (slurp "lastfm.key"))
(def lastfm_url "http://ws.audioscrobbler.com/2.0/?method=track.getInfo")

;; TODO: add sleep if request fails due to more than 25 requests per minute
(defn lastfm-query [title artist ]
  (def query_str (str lastfm_url
                    "&api_key=" (clj-util/url-encode lastfm_key) "&artist="
                    (clj-util/url-encode artist) "&track="
                    (clj-util/url-encode title) "&format=json"))
  (println query_str)

  ;; Because @ is a keyword in clojure
  (def query_res
    (let [query (:body (client/get query_str {:headers headers}))
          fixed_query (str/replace query #"@attr" "attr")]
      (:track (json/read-str fixed_query :key-fn keyword))))

  (println query_res)
  ;; TODO: return nil when query fails
  {:title (-> query_res :name)
   :artist (-> query_res :artist :name)
   :album-artist (-> query_res :album :artist)
   :album (-> query_res :album :title)
   :track-num (-> query_res :album  :attr :position)
   :genre (capitalize-words (:name (first (-> query_res :toptags :tag))))})


