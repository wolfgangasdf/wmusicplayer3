import Constants.soundFileUri
import javafx.beans.property.*
import javafx.collections.FXCollections
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.URI


/*
This is the interface between the backend and the UI. It handles playing and the playlist.
It provides javafx observables for UI updates.
 */

object Constants {

    const val NQUICKPLS = 6 // number of quick playlist buttons

    // https://www.openwith.org/programs/vlc-media-player
    private const val audiofileexts = "flac|mp3|wav|ogg|m3u|a52|aac|oma|spx|m4a|mp1|mp2|xm|ac3|mod|wma|mka|m4p"
    val soundFileUri = """(file.*\.($audiofileexts))|(http.*)""".toRegex()
    val soundFilePls = """([^.].*\.($audiofileexts|pls))""".toRegex()
    val soundFile = """([^.].*\.($audiofileexts))""".toRegex()
}

class PlaylistItem(var name: String, var title: String, var length: Int)

private val logger = KotlinLogging.logger {}

object MusicPlayer {

    val pCurrentFile = SimpleStringProperty("1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 ")
    val pCurrentSong = SimpleStringProperty("1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 ")
    val pCodecInfo = SimpleStringProperty("1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 ")
    val pTimePos = SimpleDoubleProperty(0.0)
    val pTimeLen = SimpleDoubleProperty(0.0)
    val pVolume = SimpleIntegerProperty(50)
    val pIsPlaying = SimpleBooleanProperty(false)

    private val cIntPlaylist = FXCollections.observableArrayList<PlaylistItem>()
    val cPlaylist = FXCollections.synchronizedObservableList(cIntPlaylist)!!

    val pPlaylistName = SimpleStringProperty("plname")
    val pPlaylistFile = SimpleStringProperty("plfile")
    val pCurrentPlaylistIdx = SimpleIntegerProperty(0)
    private var currentPlaylistItem: PlaylistItem? = null

    private fun getCurrentPlaylistItem() = if (pCurrentPlaylistIdx.value < 0 || pCurrentPlaylistIdx.value >= cPlaylist.size)
        null else cPlaylist[pCurrentPlaylistIdx.value]

    // fun shorten(s: String, len: Int) = if (s.length>len) s.substring(0, len) + ".." else s

    fun playFirst() {
        dosetCurrentPlaylistIdx(0)
        playSong()
    }

    fun dotoggle() {
        logger.debug("dotoggle: ${dogetPlaying()}")
        if (dogetPlaying()) {
            if (MusicPlayerBackend.dogetIsStream()) {
                logger.debug("dotoggle: stopping stream...")
                MusicPlayerBackend.stop()
            } else {
                logger.debug("dotoggle: pause...")
                MusicPlayerBackend.setPause(true)
            }
        } else {
            if (MusicPlayerBackend.dogetPause()) {
                logger.debug("dotoggle: unpause...")
                MusicPlayerBackend.setPause(false)
            } else {
                logger.debug("dotoggle: playsong...")
                playSong()
            }
        }
    }

    fun dogetPlaying(): Boolean {
        return (MusicPlayerBackend.dogetPlaying() && !MusicPlayerBackend.dogetPause())
    }

    fun addToPlaylist(uri: String, beforeId: PlaylistItem? = null, clearPlayListIfPls: Boolean, title: String? = null, length: String? = null) {
        if (uri.endsWith(".pls")) {
            val f2 = uri.replace("file://","")
            loadPlaylist(f2, clearPlayListIfPls)
        } else if (soundFileUri.matches(uri)) {
            val url = URI(uri).toURL()
            var tit = title
            var le = length?.toInt() ?: -1
            if (url.protocol == "file" && (length == null || title == null)) {
                // only parse if title/length unknown
                val (au2, al2, ti2, le2, tit2) = MusicPlayerBackend.parseSong(uri)
                tit = tit2
                if (au2 != "" && ti2 != "") tit = listOf(au2, al2, ti2).asSequence().filter{ p -> p != ""}.joinToString(" - ")
                le = le2
            } else if (url.protocol != "file" && title == null) {
                tit = url.path
                if (tit.startsWith("/")) tit = tit.substring(1)
            }
            logger.debug("adding: uri=$uri tit=$tit le=$le")
            val newitem = PlaylistItem(uri, tit ?: uri, le)
            if (beforeId == null)
                cPlaylist += newitem
            else {
                cPlaylist.add(cPlaylist.indexOf(beforeId), newitem)
            }
        }
    }

    fun loadPlaylist(file: String, clearPlayList: Boolean) {
        val f = File(file)
        if (f.exists()) {
            if (clearPlayList) cPlaylist.clear()
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
                if (!s.startsWith("[")) { // [playlist] etc, ignored
                    when {
                        s.matches(numberTag) -> {
                            nume = numberTag.matchEntire(s)!!.groupValues[1].toInt()
                        }
                        s.matches(fileTag) -> {
                            val ss = fileTag.matchEntire(s)!!.groupValues
                            files[ss[1].toInt()] = ss[2]
                        }
                        s.matches(titleTag) -> {
                            val ss = titleTag.matchEntire(s)!!.groupValues
                            titles[ss[1].toInt()] = ss[2]
                        }
                        s.matches(lengthTag) -> {
                            val ss = lengthTag.matchEntire(s)!!.groupValues
                            lengths[ss[1].toInt()] = ss[2]
                        }
                        else -> logger.warn("not found: <$s>")
                    }
                }
            }
            for (iii in 1..nume) {
                addToPlaylist(files[iii]!!, null, false, titles[iii], lengths[iii])
            }
            inp.close()
            setPlaylistVars(f)
        }
    }

    private fun setPlaylistVars(plfile: File) {
        pPlaylistName.value = plfile.name.replace(".pls","")
        pPlaylistFile.value = plfile.path
        Settings.playlistDefault = plfile.path
        Settings.save()
    }

    fun savePlaylist(plname: String) {
        setPlaylistVars(playlistFileFromName(plname))
        savePlaylist()
    }

    private fun playlistFileFromName(plname: String) = File(Settings.playlistFolder + "/" + plname + ".pls")

    private fun savePlaylist() {
        if (pPlaylistName.value != "") {
            val playlistFile = playlistFileFromName(pPlaylistName.value)
            logger.info("saving to playlist ${playlistFile.path}")
            if (playlistFile.exists()) playlistFile.delete()
            val pw = java.io.PrintWriter(playlistFile)
            pw.write("[playlist]\nNumberOfEntries=" + cPlaylist.size + "\n")
            var iii = 1
            synchronized(cPlaylist) {
                for (it in cPlaylist) {
                    pw.write("File" + iii + "=" + it.name + "\n")
                    if (it.length > 0) pw.write("Length" + iii + "=" + it.length + "\n")
                    pw.write("Title" + iii + "=" + it.title + "\n")
                    iii += 1
                }
            }
            pw.close()
        }
    }

    fun updateVolume() { // 0..100
        MusicPlayerBackend.setVolume(pVolume.value)
        Settings.save()
    }

    fun incDecVolume(up: Boolean) {
        var newvol = pVolume.value + (if (up) +1 else -1) * 5
        if (newvol > 100) newvol = 100
        if (newvol < 0) newvol = 0
        pVolume.value = newvol
        updateVolume()
    }

    fun dosetCurrentPlaylistIdx(i: Int) {
        logger.debug("setcurrit: it=$i currit=$pCurrentPlaylistIdx")
        pCurrentPlaylistIdx.value = if (i >= cPlaylist.size) -1 else i
        pCurrentSong.value = getCurrentPlaylistItem()?.title ?: ""
    }

    fun getMixers() = MusicPlayerBackend.dogetMixers()
    fun updateMixer() {
        logger.info("updateMixer: using <${Settings.mixer}>")
        MusicPlayerBackend.setMixer(Settings.mixer)
    }

    fun playNext() {
        val ci = pCurrentPlaylistIdx.value
        if (ci > -1 && ci < cPlaylist.size - 1) {
            dosetCurrentPlaylistIdx(ci + 1)
            playSong()
        } else {
            dosetCurrentPlaylistIdx(0)
            MusicPlayerBackend.stop()
        }
    }

    fun playPrevious() {
        val ci = pCurrentPlaylistIdx.value
        if (ci > 0) {
            dosetCurrentPlaylistIdx(ci - 1)
            playSong()
        } else {
            dosetCurrentPlaylistIdx(0)
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
        if ((pCurrentPlaylistIdx.value == -1 || pCurrentPlaylistIdx.value > cPlaylist.size - 1) && cPlaylist.size > 0)
            dosetCurrentPlaylistIdx(0)
        else {
            dosetCurrentPlaylistIdx(pCurrentPlaylistIdx.value)
        }
        logger.debug("playsong $pCurrentPlaylistIdx")
        currentPlaylistItem = getCurrentPlaylistItem()
        val currfile = if (currentPlaylistItem == null) "" else currentPlaylistItem!!.name
        MusicPlayerBackend.play(currentPlaylistItem!!.name)
        updateVolume()
        pCurrentFile.value = currfile
    }

    fun updateCurrentPlaylistItem() {
        pCurrentPlaylistIdx.value = cPlaylist.indexOf(currentPlaylistItem)
    }

    init {
        // init musicPlayer, called once the server is started!
        logger.info("Init MusicPlayer...")

        // permanent callbacks
        MusicPlayerBackend.onPlayingStateChanged = { _, _ ->
            pIsPlaying.set(dogetPlaying())
            if (dogetPlaying()) pCodecInfo.set(MusicPlayerBackend.dogetMediaInfo())
        }

        MusicPlayerBackend.onProgress = { time, len ->
            pTimePos.value = time
            pTimeLen.value = len
        }

        MusicPlayerBackend.onSongMetaChanged = { streamtitle, nowplaying ->
            pCurrentFile.value = streamtitle
            pCurrentSong.value = nowplaying
        }

        MusicPlayerBackend.onCompleted = {
            logger.debug("future.onfinished...")
            playNext()
        }

        // load playlist
        MusicPlayerBackend.dogetMixers()
        loadPlaylist(Settings.playlistDefault, true)
        updateMixer()
    }
}
