(ns music-downloader.core
  (:gen-class)
  (:require [clj-http.client :as client])
  (:require [clj-http.util :as clj-util])
  (:require [clojure.string :as str])
  (:require [clojure.pprint :as pp])
  (:require [clojure.java.io :as io])
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:require [clojure.data.json :as json])
  (:use [clojure.java.shell :only [sh]])
  (:use [clojure.string :only [trim]])
  (:use [music-downloader.lastfm :only [get-song-info get-album-songs]]))

(def youtube_url "https://www.youtube.com/")
(def youtube_watch_url (str youtube_url "watch?v="))
(def youtube_search_string (str youtube_url "results?search_query="))
(def default_download_loc "/home/raymond/Downloads/Music/")
(def default_music_lib_loc "/home/raymond/Share/Music/")
;;TODO: lower start threshold
(def trim_silence_code "silenceremove=start_periods=1:start_duration=1:start_threshold=-90dB:detection=peak,aformat=dblp,areverse,silenceremove=start_periods=1:start_duration=1:start_threshold=-90dB:detection=peak,aformat=dblp,areverse")

;; Global Options
(def Verbose false)

(def loc_regex
  #"Destination:\s(.*?\.m4a)")


; Get song location from youtube-dl output
(defn get-song-loc [output]
  (def matcher (re-matcher loc_regex output))
  (when-let [match (re-find matcher)]
    (nth match 1)))


(defn println-err [msg]
  (binding [*out* *err*]
    (println msg)))

; Get song information from .list file line returns hash
; if [Album] at start of line use lastfm to get all songs in album
(defn parse-line [line]
  (cond
    (re-matches #"\[Album\]\s*.+\s+-\s+.*" line)
      (let [split (str/split (str/replace line #"\[Album\]\s*" "") #"\s+-\s+")
            artist (first split)
            album (second split)]
        (get-album-songs (trim album) (trim artist)))
    (re-matches #"\s*.+\s+-\s+.*" line)
      (let [split (str/split line #"\s+-\s+")
            artist (first split)
            ; TODO: Change this to do str/join instead of first so songs can have - in their name
            title (first (rest split))]
        (list (hash-map :artist (trim artist)
                    :title (trim title))))))

;Read .list file into list of hashs
; TODO: Clean this up with apply concat
(defn read-music [file_path]
  (with-open [rdr (clojure.java.io/reader file_path)]
    (loop [lines (line-seq rdr)
           out []]
     (let [parse (parse-line (first lines))]
       (if (some? (second lines))
         (recur (rest lines)
                (concat out parse))
         (concat out parse))))))

; TODO: If lastfm tags are passed into this this becomes pure
(defn ffmpeg-tag-options [title artist use_lastfm]
  (if use_lastfm
    ;;With lastfm
    (do
      (def query_res (get-song-info title artist))
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

;; Youtube Api
;; TODO get this from file
(def youtube_api_key (slurp "googleApi.key"))
(def headers {"User-Agent" "Mozilla/5.0 (platform; rv:geckoversion) Gecko/geckotrail Firefox/firefoxversion"})
(defn youtube-api-url [search_str api_key]
  (str "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=20&" "q=" search_str
       "&type=video&key=" api_key))
(defn search-query [search_str]
  (def search_url (youtube-api-url search_str youtube_api_key))
  (println search_url)
  (def query (:body (client/get search_url {:headers headers})))
  (def results (json/read-str query :key-fn keyword))
  (map #(-> % :id :videoId) (-> results :items)))

;; TODO: add option to delete untrimmed file
;; option to disable silence trimming
(def cli_options
  ;; An option with a required argument
  [["-l" "--list-file FILE" "List file location"
    :default "music.list"]
   ["-d" "--download-dir DIR" "Where to download music to"
    :default default_download_loc]
   ["-s" "--single-line Line" "Single line from in music.list format for download"]
   ["-c" "--clean-up" "Delete untrimmed song"]
   ["-o" "--offline" "Don't use lastfm for song info"]])

(defn -main [& args]
  (def cli_opts (parse-opts args cli_options))

  (def list_file (-> cli_opts :options :list-file))
  (def download_dir (-> cli_opts :options :download-dir))
  (def single_line (-> cli_opts :options :single-line))

  (defn downloaded? [music_dir]
    (let [cur_files (str/split-lines (:out (sh "find" music_dir)))
          cur_songs (map #(-> %
                          (str/split #"/")
                          last
                          (str/split #"\.")
                          butlast
                          (as-> string_list (str/join "." string_list))
                          str/upper-case)
                      cur_files)]
      (fn [song]
        (let [file_name (str/upper-case (str (:artist song) " - " (:title song)))]
          (not (some #(= file_name %) cur_songs))))))

  ;; TODO: paramaterize this music_lib_loc
  ; TODO: BUG even though all dark side of the moon songs were filtered they were still downloaded
  (def songs (filter (downloaded? default_music_lib_loc)
                     (if (some? single_line)
                       (parse-line single_line)
                       (read-music list_file))))

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
    (def search_string (clj-util/url-encode (str search_name " (Official Audio)")))
    (def results (map #(str youtube_watch_url %) (search-query search_string)))
    (println results)


    (println "")
    (println "Downloading Song")
    ;; TODO: go through all of results until a download completes


    (defn youtube-dl-cmd []
      (println "Calling youtube-dl")
      (sh "youtube-dl" "-x" "--embed-thumbnail" "--audio-format" "m4a" "--no-playlist"
                        "--output" (str search_name "_untrimmed" ".%(ext)s") (first results)
                        :dir download_dir))

    (def file_path (loop [ytd_res (youtube-dl-cmd)]
       (println "youtube-dl Std-Out")
       (println (:out ytd_res))
       (println-err "youtube-dl Std-Err:")
       (println-err (:err ytd_res))
       (println "")

       ;; Trim silence
       (if-let [song_loc (get-song-loc (:out ytd_res))]
         song_loc
         (recur (youtube-dl-cmd)))))

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

    (def chmod_command ["chmod" "755" (str download_dir new_file_path)])
    (println chmod_command)
    (def chmod_res (apply sh chmod_command))
    (println "chmod Std-Out")
    (println (:out chmod_res))
    (println-err "chmod Std-Err:")
    (println-err (:err chmod_res))

    ;;This needs to be more thorough
    (when clean_up
      (println (str "Deleting file " file_path "\n\tBecause clean_up is set"))
      (io/delete-file (str download_dir file_path)))
    (println ""))
  (shutdown-agents))
