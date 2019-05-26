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

# How to use

* Install VLC into the default location.
* Get the [Java JRE](http://www.oracle.com/technetwork/java/javase/downloads/index.html) >= 8u101. Don't forget to untick the [crapware](https://www.google.com/search?q=java+crapware) installer, and/or [disable it permanently](https://www.java.com/en/download/faq/disable_offers.xml)!
The "JRE server" is also fine. OpenJDK is not tested.
* download the [jar](https://github.com/wolfgangasdf/wmusicplayer/releases) or `wget https://github.com/wolfgangasdf/wmusicplayer/releases/download/SNAPSHOT/wmusicplayer.jar`
* Double click the jar or better run `java -Dorg.eclipse.jetty.server.Request.maxFormKeys=2000 -Djava.io.tmpdir=./tmp -jar wmusicplayer.jar`.
* I run this in a [GNU screen](https://en.wikipedia.org/wiki/GNU_Screen) that is started automatically.

You can access WMP using different methods:

1. Full WMP using JWT: http://host:8083/
2. Simple WMP control without JWT/javascript: http://host:8083/mobile


# How to develop, compile & package

Contributions are of course very welcome!

* Get Java JDK >= 8u101 and [gradle](https://gradle.org/install/)
* check out the code (`git clone ...` or download a zip)
* install JWT into local maven repository: see https://github.com/emweb/jwt#maven
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), just open the project to get started.

Package it:

* run `gradle dist`. The resulting jar is `build/libs/wmusicplayer.jar`


# Used frameworks #

* [Kotlin](https://kotlinlang.org)
* [JWT](https://www.webtoolkit.eu/jwt)
* [vlcj](https://github.com/caprica/vlcj/)
* [AzaKotlinCSS](https://github.com/olegcherr/Aza-Kotlin-CSS)

