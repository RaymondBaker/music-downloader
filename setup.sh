#!/usr/bin/env sh


sudo apt install python3-pip
sudo pip3 install youtube-dl
sudo apt install leiningen
sudo apt install ffmpeg

mkdir -p ~/Downloads/Music

lein run
