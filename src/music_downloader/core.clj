(ns music-downloader.core
  (:gen-class)
  (:require [clj-http.client :as client])
  (:require [clj-http.util :as clj-util])
  (:require [clojure.string :as str])
  (:require [clojure.pprint :as pp])
  (:require [clojure.java.io :as io])
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:use [clojure.java.shell :only [sh]])
  (:use [clojure.string :only [trim]])
  (:use [music-downloader.lastfm :only [lastfm-query]]))

(def youtube_url "https://www.youtube.com/")
(def youtube_search_string (str youtube_url "results?search_query="))
(def default_download_loc "/home/raymond/Downloads/Music/")
(def trim_silence_code "silenceremove=start_periods=1:start_duration=1:start_threshold=-60dB:detection=peak,aformat=dblp,areverse,silenceremove=start_periods=1:start_duration=1:start_threshold=-60dB:detection=peak,aformat=dblp,areverse")

;; Global Options
(def Verbose false)

(def loc_regex
  #"Destination:\s(.*?\.m4a)")


; Get song location from youtube-dl output
(defn get-song-loc [output]
  (def matcher (re-matcher loc_regex output))
  (when-let [match (re-find matcher)]
    (nth match 1)))

(def vid_regex
  #"<h3 class=\"yt-lockup-title \"><a href=\"(.*?)\"")

; Get result links from youtube search
(defn get-vid-links [html]
  ;; TODO: check what vidoe title best matches the song title
  (def matcher (re-matcher vid_regex html))
  (loop [match (re-find matcher)
                  result []]
    (if-not match
      result
      (recur (re-find matcher)
             (conj result (str youtube_url (nth match 1)))))))

(defn println-err [msg]
  (binding [*out* *err*]
    (println msg)))

; Get song information from .list file line returns hash
(defn parse-song [line]
  (when (re-matches #"\s*.+\s+-\s+.*" line)
      (let [split (str/split line #"\s+-\s+")
            artist (first split)
            title (first (rest split))]
        [(trim artist) (trim title)])))

; convert parse song output into hash
(defn parse-song->hash-map [parse]
  (let [artist (get parse 0)
        title (get parse 1)]
    (hash-map :artist artist :title title)))

;Read .list file into list of hashs
(defn read-music [file_path]
  (with-open [rdr (clojure.java.io/reader file_path)]
    (for [line (doall (line-seq rdr))
          :let [parse (parse-song line)]
          :when (some? parse)]
      (parse-song->hash-map parse))))

(defn ffmpeg-tag-options [title artist use_lastfm]
  (if use_lastfm
    ;;With lastfm
    (do
      (def query_res (lastfm-query title artist))
      ;;If query fails fall back on offline way
      (when (nil? query_res) (ffmpeg-tag-options title artist false))
      ["-metadata" (str "artist=" (:artist query_res))
       "-metadata" (str "title=" (:title query_res))
       "-metadata" (str "album_artist=" (:album-artist query_res))
       "-metadata" (str "album=" (:album query_res))
       "-metadata" (str "genre=" (:genre query_res))
       "-metadata" (str "track=" (:track-num query_res))
       ])
    ;; Without lastfm
    ["-metadata" (str "artist=" artist)
     "-metadata" (str "title=" title)
     "-metadata" (str "album_artist=" artist)
     ]))

;; Run search-query until a valid result or 10 tries
(defn search-query [search_string]
  (loop [x 0]
    (let  [query_res (get-vid-links
                        (:body (client/get search_string)))]
      (cond
        (>= x 10)
          (do
            (println-err "Youtube query failed 10 times")
            [])
        (empty? query_res)
          (recur (inc x))
        :else
          query_res))))

;; TODO: add option to delete untrimmed file
;; option to disable silence trimming
(def cli_options
  ;; An option with a required argument
  [["-l" "--list-file FILE" "List file location"
    :default "music.list"]
   ["-d" "--download-dir DIR" "Where to download music to"
    :default default_download_loc]
   ["-s" "--single-song SONG" "Name of a single song you want to download"]
   ["-c" "--clean-up" "Delete untrimmed song"]
   ["-o" "--offline" "Don't use lastfm for song info"]])

(defn -main [& args]
  (def cli_opts (parse-opts args cli_options))

  (def list_file (-> cli_opts :options :list-file))
  (def download_dir (-> cli_opts :options :download-dir))
  (def single_song (-> cli_opts :options :single-song))

  (def songs (if (some? single_song)
               [(parse-song->hash-map (parse-song single_song))]
               (read-music list_file)))
  (def clean_up (-> cli_opts :options :clean-up))

  (def offline (-> cli_opts :options :offline))

  (println "Songs to download")
  (pp/pprint songs)
  (println "")

  ;; get songs
  (doseq [song songs]
    (def artist (:artist song))
    (def title (:title song))
    (def search_name (str artist " - " title))

    ;; get search query for youtube
    (def search_string (str youtube_search_string (clj-util/url-encode (str search_name " (Official Audio)"))))
    (println search_string)
    (def results (search-query search_string))
    (println results)

    (println "")
    (println "Downloading Song")
    (def ytd_res (sh "youtube-dl" "-x" "--embed-thumbnail" "--audio-format" "m4a" "--no-playlist"
                           "--output" (str search_name "_untrimmed" ".%(ext)s") (first results)
        :dir download_dir))
    (println "youtube-dl Std-Out")
    (println (:out ytd_res))
    (println-err "youtube-dl Std-Err:")
    (println-err (:err ytd_res))
    (println "")

    ;; Trim silence
    (def file_path (get-song-loc (:out ytd_res)))
    (def new_file_path (str/replace file_path #"_untrimmed" ""))
    (println "Trimming Silence And Adding Tags")

    ;; TODO add option to stop using lastfm
    (def ffmpeg_command (concat ["ffmpeg" "-i" file_path] (ffmpeg-tag-options title artist (not offline)) ["-c:a" "aac" "-c:v" "copy" "-af" trim_silence_code new_file_path :dir download_dir]))
    (println ffmpeg_command)

    (def fmpeg_res (apply sh ffmpeg_command))
    (println "ffmpeg Std-Out")
    (println (:out fmpeg_res))
    (println-err "ffmpeg Std-Err:")
    (println-err (:err fmpeg_res))

    ;;This needs to be more thorough
    (when clean_up
      (println (str "Deleting file " file_path "\n\tBecause clean_up is set"))
      (io/delete-file (str download_dir file_path)))
    (println ""))
  (shutdown-agents))
