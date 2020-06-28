(ns music-downloader.lastfm
  (:gen-class)
  (:require [clj-http.client :as client])
  (:require [clj-http.util :as clj-util])
  (:require [clojure.string :as str])
  (:require [clojure.pprint :as pp])
  (:require [clojure.java.io :as io]))


(def headers {"User-Agent" "Mozilla/5.0 (platform; rv:geckoversion) Gecko/geckotrail Firefox/firefoxversion"})
(def lastfm_key (slurp "lastfm.key"))
(def lastfm_url "http://ws.audioscrobbler.com/2.0/?method=track.getInfo")

;; TODO: add sleep if request fails due to more than 25 requests per minute
(defn lastfm-query [artist song_name]
  (def query_str (clj-util/url-encode
                   (str lastfm_url "&api_key=" lastfm_key "&artist=" artist "&track=" song_name
                     "&format=json")))
  (def query_res (-> (client/get query_str {:headers headers}) :track))

  {:artist (-> query_res :artist)
   :album-artist (-> query_res :album :artist)
   :album (-> query_res :album :title)
   :track-num (-> query_res :album :@attr :position)
   :genre (:name (nth (-> query_res :toptags :tag) 0))})


