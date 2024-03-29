

import mu.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import uk.co.caprica.vlcj.media.MetaData
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import java.io.File
import java.net.URI

private val logger = KotlinLogging.logger {}


/*
 This is the backend actually playing songs.
 It plays gapless, i.e., you have to supply it with new songs within the thread
 You can call play() all the time, also from onCompleted!
 */
object MusicPlayerBackend {

    private val mpc = AudioPlayerComponent() // http://capricasoftware.co.uk/projects/vlcj-4/tutorials/basic-audio-player
    private val mp: MediaPlayer = mpc.mediaPlayer()
    private val vmetadata = mutableMapOf<String, String>()
    private var vpaused = false
    private var vstopped = true

    private fun getMetadata(md: MetaData): Map<String, String> = md.values().mapKeys { it.key.name }

    private fun iniCallbacks() {
        mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                logger.debug("mpc playing")
                vpaused = false
                vstopped = false
                emitPlayingStateChanged()
            }

            override fun stopped(mediaPlayer: MediaPlayer?) {
                logger.debug("mpc stopped")
                vstopped = true
                emitPlayingStateChanged()
            }

            override fun finished(mediaPlayer: MediaPlayer?) {
                logger.debug("mpc finished")
                onCompleted()
            }

            override fun positionChanged(mediaPlayer: MediaPlayer?, newPosition: Float) {
                //logger.debug("mpc position $newPosition ${getMetadata(mp.media().meta().asMetaData())}")
            }

            override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) {
                //logger.debug("mpc time $newTime")
                if (isStream) {
                    val newmd = getMetadata(mp.media().meta().asMetaData())
                    if (vmetadata != newmd) {
                        logger.debug("   metadata changed!! $newmd")
                        vmetadata.clear()
                        vmetadata.putAll(newmd)
                        onSongMetaChanged(vmetadata.getOrDefault("TITLE", "<title>"), vmetadata.getOrDefault("NOW_PLAYING", "<now_playing>"))
                    }
                }
                onProgress((newTime/1000).toDouble(), mp.media().info().duration()/1000.0)
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                logger.debug("paused!")
                vpaused = true
                emitPlayingStateChanged()
            }

            override fun chapterChanged(mediaPlayer: MediaPlayer?, newChapter: Int) { logger.debug("mpc chapter: $newChapter") }

            override fun opening(mediaPlayer: MediaPlayer?) { logger.debug("mpc opening!") }

            override fun titleChanged(mediaPlayer: MediaPlayer?, newTitle: Int) { logger.debug("mpc title $newTitle") }

            override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) { logger.debug("mpc ready") }

            override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) { logger.debug("mpc length $newLength") }

            override fun error(mediaPlayer: MediaPlayer?) {
                logger.error("mpc error!!!!")
            }
        })

    }


    var onCompleted: () -> Unit = {}
    var onPlayingStateChanged: (/*stopped*/Boolean, /*paused*/Boolean) -> Unit = { _, _ -> }
    var onProgress: (/*secs*/ Double, /*secstotal*/ Double) -> Unit = { _, _ -> }
    var onSongMetaChanged: (/*streamtitle*/ String, /*now_playing*/ String) -> Unit =  { _, _ -> }

    data class ParseSongResult(val au: String, val al: String, val ti: String, val le: Int, val tit: String)

    fun parseSong(uri: String): ParseSongResult {
        val tit = File(uri).name
        val f = AudioFileIO.read(File(uri.removePrefix("file://")))
        val tag = f.tag
        return try {
            ParseSongResult(tag.getFirst(FieldKey.ARTIST), tag.getFirst(FieldKey.ALBUM), tag.getFirst(FieldKey.TITLE),
                    f.audioHeader.trackLength, tit) // tit is fallback-title (filename)
        } catch(e: Exception) {
            ParseSongResult("", "", "", f.audioHeader.trackLength, tit)
        }
    }

// vlcj doesn't parse in background, stops playing
//    fun parseSong(uri: String): ParseSongResult {
//        val tit = File(uri).name
//        mp.media().prepare(uri)
//        val res = mp.media().parsing().parse(1000)
//        logger.debug("parse res=$res")
//        while (mp.media().parsing().status() == null) {
//            Thread.sleep(1)
//        }
//        val md = getMetadata(mp.media().meta().asMetaData())
//
//        return ParseSongResult(md.getOrDefault("ARTIST", ""),
//                md.getOrDefault("ALBUM", ""),
//                md.getOrDefault("TITLE", ""),
//                (mp.media().info().duration()/1000.0).toInt(), tit) // tit is fallback-title (filename)
//    }

    private var isStream = false
    fun dogetIsStream(): Boolean = isStream

    fun play(songurl: String) {
        val url = URI(songurl).toURL()
        isStream = url.protocol == "http"
        mp.submit { if (!mp.media().start(songurl)) {
            logger.error("Error starting song $songurl")
            onCompleted()
        } }
    }

    fun dogetMixers(): List<String> {
        logger.debug("mix: ${mp.audio().outputDevices().joinToString(";") { it.toString() }}")
        logger.debug("mix: ${mpc.mediaPlayerFactory().audio().audioOutputs().joinToString(";") { it.toString() }}")
        return mp.audio().outputDevices().map { it.longName }
    }
    fun setMixer(mixer: String) {
        val mid = mp.audio().outputDevices().find { it.longName == mixer }?.deviceId
        mp.audio().setOutputDevice(null, mid) // output is NOT the device name, but "auhal" or so
        mp.submit {
            mp.controls().stop()
        }
    }

    fun emitPlayingStateChanged() {
        onPlayingStateChanged(vstopped, vpaused)
    }

    fun setPause(pause: Boolean) {
        mp.submit { if (pause) mp.controls().pause() else mp.controls().play() }
    }
    fun dogetPause(): Boolean = vpaused
    fun dogetPlaying(): Boolean = mp.status().isPlaying
    fun stop() {
        mp.submit { mp.controls().stop() }
    }
    fun skipTo(s: Double) {
        logger.debug("skip to $s...")
        mp.submit { mp.controls().setTime((s * 1000.0).toLong()) }
    }
    fun skipRel(s: Double) {
        mp.submit { mp.controls().skipTime((s * 1000.0).toLong()) }
    }
    fun setVolume(vol: Int) {
        logger.debug("set vol $vol")
        mp.submit { mp.audio().setVolume(vol) }
    }
    fun dogetMediaInfo(): String {
        logger.debug("media info: " + mp.media()?.info()?.audioTracks()?.firstOrNull())
        return mp.media()?.info()?.audioTracks()?.firstOrNull()?.let {
            "${it.codecName()}|${it.codecDescription()}|${it.channels()}ch|${"%.1f".format(it.rate()/1e3)}kHz" +
            (if (it.bitRate() > 0) "|${(it.bitRate()/1e3).toInt()}kbps" else "")
        }?: ""
    }

    init {
        iniCallbacks()
    }
}
