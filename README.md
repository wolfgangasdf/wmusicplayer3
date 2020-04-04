# Introduction

WMusicPlayer is a web-based (JWT/Java Webtoolkit + Kotlin) music player that

* is directory (folder) based, you don't need to have proper ID3 tags or so.
* uses simple pls-playlists which can be created and modified using the web interface.
* plays everything that VLC plays (mp3, flac, ogg, wav, aac, ...)

It

* remembers the last browsed folder location.
* scrolls to the last folder after "go to parent".
* has an optional simple plain-html control frontend without javascript.
* has quick-playlist buttons.

Note

* Audio device selection works only after some music has been played. 


# How to use

* Install VLC into the default location.
* [Download a zip](https://github.com/wolfgangasdf/gmail-attachment-remover/releases), extract it somewhere and run
`bin/wmusicplayer.bat` (Windows) or `bin/wmusicplayer` (Linux/Mac). It is not signed, google for "open unsigned mac/win".
* I run this in a [GNU screen](https://en.wikipedia.org/wiki/GNU_Screen) that is started automatically.

You can access WMP using different methods:

1. Full WMP using JWT: http://host:8083/
2. Simple WMP control without JWT/javascript: http://host:8083/mobile


# How to develop, compile & package

Contributions are of course very welcome!

* Get Java 13 from https://jdk.java.net
* Clone the repository
* install JWT into local maven repository: see https://github.com/emweb/jwt#maven
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

