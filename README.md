# music-downloader

Downloads music from a given list file.
:wq
this first does a search query for the song on youtube and then uses youtube-dl
and ffmpeg to download and trim the silence respectively

## Installation

Download from http://example.com/FIXME.

## Usage

    $ java -jar music-downloader-0.1.0-standalone.jar [args]

## Options

-l --list-file <FILE>
    > Where to find list of songs to download
-d --download-dir <DIR>
    > Where to download music to

## Examples

### List File Format
<Artist Name> - <Song Name>
The Beatles - While My Guitar Gently Weeps

### Bugs


## License

Copyright 2020 Raymond Baker

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
