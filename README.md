# Introduction

WMusicPlayer is a web-based (JWT/Jave Webtoolkit + Kotlin) music player that

* is directory (folder) based, you don't need to have proper ID3 tags or so.
* uses simple pls-playlists which can be created and modified using the web interface.
* plays mp3, flac, ogg

The closest program that I could find is [mopidy](https://www.mopidy.com), but it does not

* remember last browsed folder location
* in folderA, go to parent folder: scroll to folderA
* optional simple plain-html control frontend without javascript
* pls playlists


# Getting started #

To run it, you need java >= 1.8

To build it, you need a java jdk >= 1.8 and sbt 0.13.

## To run it: 

* [Download the jar](https://bitbucket.org/wolfgang/wmusicplayer3/downloads)
* TODO Double-click to run the jar or run with `java -jar wmusicplayer.jar`

I run this on a computer in a [GNU screen](https://en.wikipedia.org/wiki/GNU_Screen) that is started automatically.

## Web frontend versions
TODO You can access WMP using 3 different methods:

1. Full WMP using vaadin: http://host:8083/
2. Short WMP using vaadin: http://host:8083/mini
3. Simple WMP control without vaadin/javascript: http://host:8083/mobile

## Build & package:
TODO `sbt dist`

# Used frameworks #
TODO
* [JWT](https://www.webtoolkit.eu/jwt)
* [Kotlin](https://kotlinlang.org)
* via [com.googlecode.soundlibs](https://code.google.com/p/soundlibs/):
    * [Tritonus](http://tritonus.org/)
    * [mp3spi](http://www.javazoom.net/mp3spi/mp3spi.html)
    * [jorbis](http://www.jcraft.com/jorbis/)
    * [vorbisspi](http://www.javazoom.net/vorbisspi/vorbisspi.html)
* [jflac](https://github.com/nguillaumin/jflac)

# Contributors #

Contributions are of course very welcome, please contact me or use the standard methods.

# Maintainer #

wolfgang.loeffler@gmail.com