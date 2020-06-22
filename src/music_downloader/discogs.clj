(ns music-downloader.discogs
  (:gen-class)
  (:require [clj-http.client :as client])
  (:require [clj-http.util :as clj-util])
  (:require [clojure.string :as str])
  (:require [clojure.pprint :as pp])
  (:require [clojure.java.io :as io]))


(def headers {"User-Agent" "Unique user agent"})
(def discogs_token (slurp "dicogsToken.tok"))
(def discogs_url "https://api.discogs.com/database/search?")

;; TODO: add sleep if request fails due to more than 25 requests per minute
(defn discogs-query [artist song_name]
  (def query_str (clj-util/url-encode
                   (str discogs_url "release_title=" nevermind "&artist=" artist
                     "&per_page=1&page=1&token=" discogs_token)))
  (def query_res (nth (-> (client/get query_str {:headers headers}) :pagination :results)
                      1))
  (reduce [
           (str "artist="artist)]))



