
import Constants.soundFile
import javafx.collections.FXCollections
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.URL


/*
the singleton instance which is similar to a standalone music player, e.g., it also manages the playlist
it is based on scaladin/vaadin types to be able to easily show playlist etc
it can be used by all clients (not only scaladin)
 */

object Constants {

    val NQUICKPLS = 6 // number of quick playlist buttons

    val tagFile = "file://"
    val tagStream = "http://"

    val soundFile = """(file.*\.(flac|mp3|wav|ogg))|(http.*)""".toRegex()
    val soundFilePls = """([^\.].*\.(flac|mp3|wav|ogg|pls))""".toRegex()
}



class PlaylistItem(var name: String, var title: String, var isCurrent: Boolean, var length: Int)

private val logger = KotlinLogging.logger {}

object MusicPlayer {


//    var loader: ClassLoader = null // set this to the contextclassloader of the main thing
    // use this everywhere where AudioSystem is used!!!
//    fun setContextClassLoader(): Unit = {
//        // this is ugly hack to fight against vaadin changing the CC (if websockets)
//        // then, audiosystem can't find SPI's... debug(" XXXXX audio file readers: " + JDK13Services.getProviders(classOf[AudioFileReader]).mkString(" ; "))
//        Thread.currentThread().setContextClassLoader(loader)
//    }

    var pCurrentFile = "currfile"
    var pCurrentSong = "currsong"
    var pTimePos = 0.0
    var pTimeLen = 0.0
    var pVolume = 50
    var pLastFolder = "/"
    var pIsStopped = true
    var pIsPaused = false

    private val cIntPlaylist = FXCollections.observableArrayList<PlaylistItem>()
    val cPlaylist = FXCollections.synchronizedObservableList(cIntPlaylist)!!

    var playlistFile: File? = null
    var pPlaylistName = ""
    var currentPlaylistItem: PlaylistItem? = null

    fun shorten(s: String, len: Int) = {
        if (s.length>len) s.substring(0, len) + ".." else s
    }

    fun playFirst() {
        dosetCurrentPlaylistItem(cPlaylist[0])
        playSong()
    }

    fun dotoggle() {
        if (dogetPlaying()) {
            if (MusicPlayerBackend.dogetIsStream()) {
                MusicPlayerBackend.stop()
            } else {
                MusicPlayerBackend.setPause(true)
            }
        } else {
            if (MusicPlayerBackend.dogetPause())
                MusicPlayerBackend.setPause(false)
            else
                playSong()
        }
    }

    fun dogetPlaying(): Boolean {
        return (MusicPlayerBackend.dogetPlaying() && !MusicPlayerBackend.dogetPause())
    }

    fun addToPlaylist(uri: String, beforeId: PlaylistItem? = null, title: String? = null, length: String? = null) {
//        setContextClassLoader()
        if (uri.endsWith(".pls")) {
            val f2 = uri.replace("file://","")
            loadPlaylist(f2)
        } else if (soundFile.matches(uri)) {
            val url = URL(uri)
            var tit = title
            var le = if (length != null) length.toInt() else -1
            if (url.protocol == "file" && (length == null || title == null)) {
                // only parse if title/length unknown
                val (au2, al2, ti2, le2, tit2) = MusicPlayerBackend.parseSong(uri)
                tit = tit2
                if (au2 != "" && ti2 != "") tit = listOf(au2, al2, ti2).filter{p -> p != ""}.joinToString(" - ")
                le = le2
            } else if (url.protocol != "file" && title == null) {
                tit = url.path
                if (tit.startsWith("/")) tit = tit.substring(1)
            }
            logger.debug("adding: tit=$tit le=$le")
            val newitem = PlaylistItem(uri, tit!!, false, le)
            if (beforeId == null)
                cPlaylist += newitem
            else {
                cPlaylist.add(cPlaylist.indexOf(beforeId), newitem)
            }
        }
    }

    // TODO really implement myself, no decent simple pls parser out there!
    fun loadPlaylist(file: String) {
        val f = File(file)
        if (f.exists()) {
            cPlaylist.clear()
            val inp = BufferedReader(FileReader(f))
            val numberTag = """numberofentries=(.*)""".toRegex(RegexOption.IGNORE_CASE)
            val fileTag = """file([0-9]+)=(.*)""".toRegex(RegexOption.IGNORE_CASE)
            val titleTag = """title([0-9]+)=(.*)""".toRegex(RegexOption.IGNORE_CASE)
            val lengthTag = """length([0-9]+)=(.*)""".toRegex(RegexOption.IGNORE_CASE)
            var nume = -1
            val files = HashMap<Int, String>()
            val titles = HashMap<Int, String>()
            val lengths = HashMap<Int, String>()
            while (inp.ready()) {
                val s = inp.readLine().trim()
                if (s.startsWith("[")) {
                    // [playlist] etc, ignored
                } else {
                    when {
                        s.matches(numberTag) -> {
                            nume = numberTag.matchEntire(s)!!.groupValues[1].toInt()
                        }
                        s.matches(fileTag) -> {
                            val ss = fileTag.matchEntire(s)!!.groupValues
                            files.put(ss[1].toInt(), ss[2])
                        }
                        s.matches(titleTag) -> {
                            val ss = titleTag.matchEntire(s)!!.groupValues
                            titles.put(ss[1].toInt(), ss[2])
                        }
                        s.matches(lengthTag) -> {
                            val ss = lengthTag.matchEntire(s)!!.groupValues
                            lengths.put(ss[1].toInt(), ss[2])
                        }
                        else -> logger.warn("not found: <" + s + ">")
                    }
                }
            }
            for (iii in 1..nume) {
                addToPlaylist(files[iii]!!, null, titles[iii], lengths[iii])
            }
            inp.close()
            setPlaylistVars(f)
        }
    }

    fun setPlaylistVars(f: File) {
        playlistFile = f
        pPlaylistName = f.getName().replace(".pls","")
        Settings.playlistDefault = f.getPath()
        Settings.save()
    }

    fun savePlaylist(fn: String) {
        setPlaylistVars(File(fn))
        savePlaylist()
    }

    fun savePlaylist() {
        if (pPlaylistName != "") {
            playlistFile = File(Settings.playlistFolder + "/" + pPlaylistName + ".pls")
            if (playlistFile!!.exists()) playlistFile!!.delete()
            val pw = java.io.PrintWriter(playlistFile)
            pw.write("[playlist]\nNumberOfEntries=" + cPlaylist.size + "\n")
            var iii = 1
            for (it in cPlaylist) {
                pw.write("File" + iii + "=" + it.name + "\n")
                if (it.length > 0) pw.write("Length" + iii + "=" + it.length + "\n")
                pw.write("Title" + iii + "=" + it.title + "\n")
                iii += 1
            }
            pw.close()
        }
    }

    fun updateVolume() { // 0..100
        MusicPlayerBackend.setVolume(pVolume/100.0)
        Settings.save()
    }

    fun incDecVolume(up: Boolean) {
        var newvol = pVolume + (if (up) +1 else -1) * 5
        if (newvol > 100) newvol = 100
        if (newvol < 0) newvol = 0
        pVolume = newvol
        updateVolume()
    }

    fun dosetCurrentPlaylistItem(it: PlaylistItem) {
        logger.debug("setcurrit: it=" + it + " currit=" + currentPlaylistItem)
        if (cPlaylist.contains(currentPlaylistItem)) currentPlaylistItem!!.isCurrent = false
        currentPlaylistItem = it
        currentPlaylistItem!!.isCurrent = true
        pCurrentSong = currentPlaylistItem!!.title
    }

    fun getMixers() = MusicPlayerBackend.dogetMixers()

    fun playNext() {
        val ci = cPlaylist.indexOf(currentPlaylistItem)
        if (ci > -1 && ci < cPlaylist.size - 1) {
            dosetCurrentPlaylistItem(cPlaylist[ci + 1])
            playSong()
        } else {
            dosetCurrentPlaylistItem(cPlaylist[0])
            MusicPlayerBackend.stop()
        }
    }

    fun playPrevious() {
        val ci = cPlaylist.indexOf(currentPlaylistItem)
        if (ci > 0) {
            dosetCurrentPlaylistItem(cPlaylist[ci - 1])
            playSong()
        } else {
            dosetCurrentPlaylistItem(cPlaylist[0])
            MusicPlayerBackend.stop()
        }
    }

    fun skipTo(t: Double) {
        MusicPlayerBackend.skipTo(t)
    }

    fun skipRel(dt: Double) {
        MusicPlayerBackend.skipRel(dt)
    }

    // plays currendId
    fun playSong() {

        MusicPlayerBackend.onPlayingStateChanged = { stopped, paused ->
            pIsStopped = stopped
            pIsPaused = paused
        }

        MusicPlayerBackend.onProgress = { time, len ->
            pTimePos = time
            pTimeLen = len
        }

        MusicPlayerBackend.onSongMetaChanged = {
            // TODO
        }

        MusicPlayerBackend.onFinished = {
            logger.debug("future.onfinished...")
            if (!cPlaylist.contains(currentPlaylistItem)) {
                logger.debug("item gone, stop")
                dosetCurrentPlaylistItem(cPlaylist[0])
                MusicPlayerBackend.stop()
            } else {
                playNext()
            }
        }

        if (currentPlaylistItem == null) dosetCurrentPlaylistItem(cPlaylist[0])
        logger.debug("playsong " + currentPlaylistItem)
        if (currentPlaylistItem == null) {
            return
        }
        val currfile = MusicPlayerBackend.play(currentPlaylistItem!!.name, currentPlaylistItem!!.length.toDouble())
        updateVolume()
        pCurrentFile = currfile
    }

    init {
        // init musicPlayer, called once the server is started!
        logger.info("Init musicPlayer...")
        loadPlaylist(Settings.playlistDefault)
    }
}
