# Introduction

WMusicPlayer is a web-based (JWT/Java Webtoolkit + Kotlin) music player that

* is directory (folder) based, you don't need to have proper ID3 tags or so.
* uses simple pls-playlists which can be created and modified using the web interface.
* plays mp3, flac, ogg, wav

It

* remembers the last browsed folder location.
* scrolls to the last folder after "go to parent".
* has an optional simple plain-html control frontend without javascript.
* has quick-playlist buttons.

# How to use

* Get the [Java JRE](http://www.oracle.com/technetwork/java/javase/downloads/index.html) >= 8u101. Don't forget to untick the [crapware](https://www.google.com/search?q=java+crapware) installer, and/or [disable it permanently](https://www.java.com/en/download/faq/disable_offers.xml)!
The "JRE server" is also fine. OpenJDK is not tested.
* download the [jar](https://bitbucket.org/wolfgang/wmusicplayer3/downloads) or `wget https://bitbucket.org/wolfgang/wmusicplayer3/downloads/wmusicplayer.jar`
* Double click the jar or run `java -Dorg.eclipse.jetty.server.Request.maxFormKeys=2000 -Djava.io.tmpdir=./tmp -jar wmusicplayer3.jar`.
* I run this in a [GNU screen](https://en.wikipedia.org/wiki/GNU_Screen) that is started automatically.

You can access WMP using different methods:

1. Full WMP using JWT: http://host:8083/
2. Simple WMP control without JWT/javascript: http://host:8083/mobile


# How to develop, compile & package

Contributions are of course very welcome!

* Get Java JDK >= 8u101 and [gradle](https://gradle.org/install/)
* check out the code (`git clone ...` or download a zip)
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), just open the project to get started.

Package it:

* run `gradle shadowJar`. The resulting jar is `build/libs/wmusicplayer3.jar`


# Used frameworks #

* [JWT](https://www.webtoolkit.eu/jwt)
* [Kotlin](https://kotlinlang.org)
* via [com.googlecode.soundlibs](https://code.google.com/p/soundlibs/):
    * [Tritonus](http://tritonus.org/)
    * [mp3spi](http://www.javazoom.net/mp3spi/mp3spi.html)
    * [jorbis](http://www.jcraft.com/jorbis/)
    * [vorbisspi](http://www.javazoom.net/vorbisspi/vorbisspi.html)
* [jflac](https://github.com/nguillaumin/jflac)


