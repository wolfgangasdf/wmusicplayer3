# Introduction

WMusicPlayer is a web-based (JWT/Java Webtoolkit + Kotlin) music player intended to run on a server that

* is directory (folder) based, you don't need to have proper ID3 tags or so.
* uses simple pls-playlists which can be created and modified using the web interface.
* uses VLC, so it plays everything that VLC plays (mp3, flac, ogg, wav, aac, ...)

It

* remembers the last browsed folder location.
* scrolls to the last folder after "go to parent".
* has an optional simple plain-html control frontend without javascript.
* has quick-playlist buttons.
* plays streams

It does not

* play spotify etc.

Notes

* Audio device selection works only after some music has been played.
* There is no authentication or other protection, use it only on private LANs.
* Only mac is tested continuously, but it should run on win & linux, too.
* Before commit [3083d69](https://github.com/wolfgangasdf/wmusicplayer3/commit/3083d69f380379731b4d52e400d82464bc925a60) it did contain a pure-java skip-free music player (flac, mp3, ogg,...), but AAC didn't work, and because of lack of time, I switched to VLC.

# How to use

* Install VLC into the default location.
* [Download a zip](https://github.com/wolfgangasdf/gmail-attachment-remover/releases), extract it somewhere and run
`bin/wmusicplayer.bat` (Windows) or `bin/wmusicplayer` (Linux/Mac). It is not signed, google for "open unsigned mac/win".
* I run this in a [GNU screen](https://en.wikipedia.org/wiki/GNU_Screen) that is started automatically on a mac mini.

You can access WMP using different methods:

1. Full WMP using JWT: http://host:8083/
2. Simple WMP control without JWT/javascript: http://host:8083/mobile


# How to develop, compile & package

Contributions are of course very welcome!

* Get Java from https://jdk.java.net
* Clone the repository
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), just open the project to get started.

Package it:

* run `./gradlew clean dist`. The resulting files are in `build/crosspackage`


# Used frameworks #

* [Kotlin](https://kotlinlang.org)
* [JWT](https://www.webtoolkit.eu/jwt)
* [vlcj](https://github.com/caprica/vlcj/)
* [jaudiotagger](http://www.jthink.net/jaudiotagger/)
* [AzaKotlinCSS](https://github.com/olegcherr/Aza-Kotlin-CSS)
* [Runtime plugin](https://github.com/beryx/badass-runtime-plugin) to make runtimes with JRE

