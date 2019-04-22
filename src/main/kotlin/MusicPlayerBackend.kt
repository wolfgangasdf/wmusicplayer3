

import java.io.*
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.*
import mu.KotlinLogging

import org.jflac.FLACDecoder
import org.jflac.metadata.Metadata
import java.net.HttpURLConnection
import java.net.URLConnection
import java.util.concurrent.CompletableFuture

/*
You can call play() all the time, also from onCompleted!
 */
private val logger = KotlinLogging.logger {}

// this is the backend actually playing songs, doesn't know about scaladin.
// it plays gapless, i.e., you have to supply it with new songs within the thread
object MusicPlayerBackend {
//
//    var loader: ClassLoader? = null
//    fun setContextClassLoader() { Thread.currentThread().contextClassLoader = loader }
//

    // callbacks are run in Future {}
    // TODO think if to put all callbacks as arguments of play()? because an old one could remain set...
    var onCompleted: () -> Unit = {}
    var onPlayingStateChanged: (/*stopped*/Boolean, /*paused*/Boolean) -> Unit = { _, _ -> }
    var onProgress: (/*secs*/ Double, /*secstotal*/ Double) -> Unit = { _, _ -> }
    var onSongMetaChanged: (/*streamtitle*/ String, /*streamurl*/ String) -> Unit =  { _, _ -> }

    private var playThing = PlayThing()

    enum class Actions(val i: Int) {
        ANOTHING(0), ASTOP(1), ASKIPREL(2), ASKIPTO(3)
    }

    @Suppress("unused")
    fun dogetLength(url: URL): Double {
//        setContextClassLoader()
        var length: Double = -1.0
        if (url.protocol == "file") {
            val f = File(url.file)
            if (f.exists()) {
                val audioIn = AudioSystem.getAudioInputStream(f)
                val decodedFormat = dogetDecodedFormat(audioIn)
                val audioInDec = AudioSystem.getAudioInputStream(decodedFormat, audioIn)
                val bufferSize = 1000000 //(decodedFormat.getSampleRate * decodedFormat.getFrameSize).toInt
                val buffer = ByteArray(bufferSize)
                var bytesRead: Long = 0
                var total: Long = 0
                while (bytesRead >= 0) {
                    bytesRead = audioInDec.read(buffer, 0, bufferSize).toLong()
                    if (bytesRead >= 0) {
                        total += bytesRead
                    }
                }
                length = if (decodedFormat.sampleRate >0) (total / decodedFormat.sampleRate / decodedFormat.frameSize).toDouble() else 0.0
            }
        }
        return length
    }

    data class ParseSongResult(val au: String, val al: String, val ti: String, val le: Int, val tit: String)
    fun parseSong(uri: String): ParseSongResult {
//        setContextClassLoader()
        val url = URL(uri)
        var au = "" ; var al = "" ; var ti = ""
        val tit = File(uri).name
        var le = -1
        try {
            // logger.info("XXXXX1 audio file readers[${Thread.currentThread().id}]: " + com.sun.media.sound.JDK13Services.getProviders(AudioFileReader::class.java).joinToString { x -> x.toString() })
            if (url.file.endsWith(".flac")) {
                val fis = FileInputStream(url.file)
                val decoder = FLACDecoder(fis)
                val mda: Array<Metadata> = decoder.readMetadata()
                mda.forEach { mdai ->
                    when(mdai) {
                        is org.jflac.metadata.VorbisComment -> {
                            au = mdai.getCommentByName("ARTIST")?.getOrElse(0) { "" } ?: ""
                            al = mdai.getCommentByName("ALBUM")?.getOrElse(0) { "" } ?: ""
                            ti = mdai.getCommentByName("TITLE")?.getOrElse(0) { "" } ?: ""
                        }
                        is org.jflac.metadata.StreamInfo -> {
                            if (mdai.sampleRate > 0) le = (mdai.totalSamples / mdai.sampleRate).toInt()
                        }
                    }
                }
                fis.close()
            } else {
                val audioFormat = AudioSystem.getAudioInputStream(File(url.file)).format // via URL this doesn't work, BUG?
                val baseFileFormat = AudioSystem.getAudioFileFormat(File(url.file)) // via URL this doesn't work, BUG?
                // see http://www.javazoom.net/mp3spi/documents.html
                val pp = baseFileFormat.properties().mapValues { a -> a.value?.toString() }// as MutableMap<String, String>
                le = (pp.getOrDefault("duration","-1000000").toString().toLong() /1e6).toInt()
                au = pp["author"] ?: ""
                al = pp["album"] ?: ""
                ti = pp["title"] ?: ""
                if (le < 0) {
                    le = (baseFileFormat.frameLength / audioFormat.frameRate).toInt()
                    logger.debug("le=0, now le=$le fl=${baseFileFormat.frameLength} fr=${audioFormat.frameRate}")
                }
            }
        } catch (x: Throwable) {
            logger.warn("exception during file scan: " + x.message + " in: " + url, x)
        }
        logger.debug("parse: le=$le")
        return ParseSongResult(au, al, ti, le, tit) // tit is fallback-title (filename)
    }

    fun dogetDecodedFormat(audioIn: AudioInputStream): AudioFormat {
        val baseFormat = audioIn.format
        logger.debug("baseformat = $baseFormat class=${baseFormat::class.java} ch=${baseFormat.channels} fs=${baseFormat.sampleSizeInBits}")
        val decodedFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
        baseFormat.sampleRate,
        if (baseFormat.sampleSizeInBits > 0) baseFormat.sampleSizeInBits else 16,
        baseFormat.channels,
        if (baseFormat.sampleSizeInBits > 0)
            baseFormat.channels * baseFormat.sampleSizeInBits / 8
        else baseFormat.channels * 2,
        baseFormat.sampleRate,
        false)
        logger.debug("decoformat = $decodedFormat")
        return decodedFormat
    }

    fun dogetIsStream(): Boolean = playThing.isStream

    fun play(songurl: String, timelen: Double): (/*currentfile*/String) {
        if (playThing.isPlaying.get()) {
            playThing.action.set(Actions.ASTOP.i)
            playThing.isPaused.set(false)
            Thread.sleep(500)
            playThing.cleanup()
        }
        //playThing = PlayThing()
        return playThing.play2(songurl, timelen)
    }

    class PlayThing {
        val actionVal = AtomicReference<Double>(0.0)
        val action = AtomicInteger(Actions.ANOTHING.i)
        val isPaused = AtomicBoolean(false)
        val isPlaying = AtomicBoolean(false)
        private var sdl: SourceDataLine? = null
        private var audioIn: AudioInputStream? = null
        private var audioInDec: AudioInputStream? = null
        private var decodedFormat: AudioFormat? = null
        private var fut: CompletableFuture<Unit>? = null
        private var volume: FloatControl? = null
        private var conn: URLConnection? = null
        private var bInputStream: IcyBufferedInputStream? = null

        var isStream = false

        fun setVolume(vol: Double) {
            if (volume != null) {
                val maxvol = Math.min(volume!!.maximum, 0f)
                // set max gain
                fun dbToLin(db: Float) = Math.pow(10.0, db / 20.0) // http://docs.oracle.com/javase/1.5.0/docs/api/javax/sound/sampled/FloatControl.Type.html
                logger.debug("vol: maxvol=" + maxvol + " maxpossible=" + volume!!.maximum + " min=" + volume!!.minimum + " vol=" + vol)
                volume!!.value = volume!!.minimum + (maxvol - volume!!.minimum) * vol.toFloat()
                val minlin = dbToLin(volume!!.minimum)
                val maxlin = dbToLin(maxvol)
                val newlin = (maxlin - minlin) * vol
                val newdb = Math.log10(newlin) * 20
                volume!!.value = newdb.toFloat()
                logger.debug("vol: maxvol=" + maxvol + " maxpossible=" + volume!!.maximum + " min=" + volume!!.minimum + " vol=" + vol + " newdb=" + newdb)
            }
        }

        fun safeString(s: String) = s.map { if (it.toInt() in 32..175) it else '?' }.joinToString("")

        inner class IcyBufferedInputStream(inps: InputStream, private var metainterval: Int) : BufferedInputStream(inps) {
            var readBytesUntilMeta = 0

            override fun read(): Int = 0 // not needed
            override fun read(b: ByteArray?) = 0 // not needed

            private fun readOrThrow(b: ByteArray?, off: Int, len: Int) {
                var tmpread = 0
                while (tmpread < len) {
                    val t2 = super.read(b, off, len-tmpread)
                    if (t2 != len-tmpread) logger.debug("readOrThrow: failed to read, got $t2!")
                    if (t2 == -1) throw Exception("readOrThrow: got -1!")
                    tmpread += t2
                }
            }
            // read icy-metaint chunks according to http://www.smackfu.com/stuff/programming/shoutcast.html etc
            override fun read(b: ByteArray?, off: Int, len: Int): Int {
                assert(off != 0) // not implemented, don't need
                return if (metainterval > 0 && readBytesUntilMeta +len > metainterval) {
                    val len2 = metainterval- readBytesUntilMeta // read until next metadata or len
                    readOrThrow(b, off, len2)
                    val metaN = super.read() // metadata length = metaN*16 bytes
                    if (metaN < 0) throw Exception ("ERROR reading metadata length byte!!!")
                    if (metaN > 0) {
                        val metabb = ByteArray(metaN*16)
                        readOrThrow(metabb, 0, metaN*16)
                        val md = metabb.toString(Charsets.UTF_8).trim(0.toChar())
                        logger.debug("received metadata [$readBytesUntilMeta $metainterval $off $len $len2 $metaN]")
                        logger.debug("received metadata: ${safeString(md)}")
                        val saneFraction = md.map { if (it.toInt() in 32..175) 1 else 0 }.sum() / md.length
                        if (saneFraction > 0.5) {
                            var st = ""
                            var surl = ""
                            md.split(";").forEach { s ->
                                val keyval = s.split("=")
                                if (keyval.size == 2) {
                                    if (keyval[0] == "StreamTitle") st = keyval[1]
                                    if (keyval[0] == "StreamUrl") surl = keyval[1]
                                }
                            }
                            if (st + surl != "") onSongMetaChanged(st, surl)
                        } else {
                            throw Exception("icystream: metadata binary, something got messed up!")
                        }
                    }
                    readBytesUntilMeta = 0
                    len2
                } else {
                    val tmpread = super.read(b, off, len)
                    readBytesUntilMeta += tmpread
                    tmpread
                }
            }
        }

        fun play2(songurl: String, timelen: Double): (/*currentfile*/String) {
//            setContextClassLoader()
            if (fut?.isDone == false) {
                logger.error("future is not completed!")
                //throw IllegalStateException("future is not completed!")
            }

            action.set(Actions.ANOTHING.i)
            isPlaying.set(true)
            emitPlayingStateChanged()

            val url = URL(songurl)
            var ptimepos = 0.0
            logger.debug("play2: playing url = $url")

            var currentFile = ""

            var audioFile: File? = null
            try {
                if (url.protocol == "http") { // shoutcast streams seem to need this

                    // own icy metadata reader, WORKS for icecast & shoutcast!
                    conn = url.openConnection()
                    conn!!.setRequestProperty("Accept", "*/*")
                    conn!!.setRequestProperty("Icy-Metadata", "1") // request it
                    conn!!.setRequestProperty("Connection", "close")
                    logger.debug("contentlength: ${conn!!.contentLength}")
                    val metaint = conn!!.getHeaderFieldInt("icy-metaint", -1)
                    logger.debug("ice: is icecast 2 stream metaint=$metaint")
                    // get stream name
                    val headers = conn!!.headerFields.mapValues { e -> e.value.first() }
                    headers.forEach { (t, u) -> logger.debug("header: $t = $u") }
                    val streamname = headers.getOrDefault("icy-name", url.toString())
                    currentFile = streamname
                    bInputStream = IcyBufferedInputStream(conn!!.getInputStream(), metaint)
                    audioIn = AudioSystem.getAudioInputStream(bInputStream)
                    bInputStream!!.readBytesUntilMeta = 0 // here i must reset the read counter so that after icy-metaint bytes the metadata comes!!!
                    isStream = true
                } else if (url.protocol == "file") {
                    isStream = false
                    audioFile = File(url.file)
                    if (audioFile.exists()) {
                        currentFile = audioFile.path
                        audioIn = AudioSystem.getAudioInputStream(audioFile)
                    } else {
                        logger.error("file not found: $url")
                        isPlaying.set(false)
                        emitPlayingStateChanged()
                        return ""
                    }
                }

                decodedFormat = dogetDecodedFormat(audioIn!!)
                audioInDec = AudioSystem.getAudioInputStream(decodedFormat, audioIn)
                sdl = null

                for (thisMixerInfo in AudioSystem.getMixerInfo()) {
                    if (thisMixerInfo.name == Settings.mixer) {
                        logger.debug("found mixer: " + thisMixerInfo.name)
                        try {
                            sdl = AudioSystem.getSourceDataLine(decodedFormat, thisMixerInfo)
                        } catch (e: Exception) {
                            logger.error("Selected mixer doesn't work: " + e.message)
                            e.printStackTrace()
                        }
                    }
                }
                if (sdl == null) {
                    logger.debug("sdl null, using default")
                    val info = DataLine.Info(SourceDataLine::class.java, decodedFormat, AudioSystem.NOT_SPECIFIED)
                    sdl = AudioSystem.getLine(info) as SourceDataLine
                }
                sdl!!.open(decodedFormat, AudioSystem.NOT_SPECIFIED)
            } catch(e: Throwable) {
                logger.error("got exception", e)
                isPlaying.set(false)
                emitPlayingStateChanged()
                return ""
            }
            volume = if (sdl!!.isControlSupported(FloatControl.Type.MASTER_GAIN))
                sdl!!.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl // TODO
            else {
                logger.warn("No volume control!")
                null
            }

            sdl!!.start()
            fut = CompletableFuture.supplyAsync {
                // http://docs.oracle.com/javase/tutorial/sound/playing.html
                var total = 0
                val bufferSize = (decodedFormat!!.sampleRate * decodedFormat!!.frameSize).toInt()
                logger.debug("samplerate=" + decodedFormat!!.sampleRate + " framesize=" + decodedFormat!!.frameSize + " buffsize=$bufferSize")
                val buffer = ByteArray(bufferSize)
                var bytesRead = 0
                fun skipBuffer(toSkip: Int): Int {
                    var skipReadTotal = 0
                    var skipRead = 0
                    while (toSkip > skipReadTotal && skipRead >= 0) {
                        skipRead = audioInDec!!.read(buffer, 0, bufferSize)
                        skipReadTotal += skipRead
                    }
                    return skipReadTotal
                }
                var oldTime = 0.0
                fun restartSong() {
                    sdl!!.flush()
                    sdl!!.stop()
                    audioIn = AudioSystem.getAudioInputStream(audioFile)
                    audioInDec = AudioSystem.getAudioInputStream(decodedFormat, audioIn)
                    sdl!!.start()
                }
                while (bytesRead >= 0 && (action.get() == Actions.ANOTHING.i || action.get() == Actions.ASKIPREL.i || action.get() == Actions.ASKIPTO.i) && isPlaying.get()) {
                    if (isPaused.get()) {
                        Thread.sleep(250)
                    } else {
                        //debug(s"total=$total action=${action.get}")
                        if (action.get() == Actions.ASKIPREL.i && audioFile != null) {
                            // TODO add skip back buffer: record in hashmap all / some positions,...
                            val dt = actionVal.get()
                            if (dt > 0) {
                                sdl!!.flush()
                                total += skipBuffer((bufferSize * dt).toInt())
                            } else {
                                restartSong()
                                val newpos = total - bufferSize * 10
                                total = skipBuffer(newpos)
                                oldTime = 0.0
                            }
                        } else if (action.get() == Actions.ASKIPTO.i) {
                            if (audioFile != null) {
                                if (actionVal.get() in 0.0..timelen) {
                                    if (actionVal.get() != ptimepos && decodedFormat!!.sampleRate > 0) {
                                        restartSong()
                                        val newpos = (actionVal.get() * (decodedFormat!!.sampleRate * decodedFormat!!.frameSize)).toInt()
                                        total = skipBuffer(newpos)
                                        oldTime = 0.0
                                    }
                                }
                                actionVal.set(-1.0)
                            }
                        }
                        action.set(Actions.ANOTHING.i)
                        // read and play
                        bytesRead = audioInDec!!.read(buffer, 0, bufferSize)
                        if (bytesRead >= 0) {
                            total += bytesRead
                            var bytesleft = bytesRead
                            while (bytesleft > 0) {
                                bytesleft -= sdl!!.write(buffer, 0, bytesleft)
                                if (bytesleft > 0) { // can this happen?
                                    logger.debug("XXXX left=$bytesleft bytesRead=$bytesRead")
                                    Thread.sleep(10)
                                }
                            }
                        }
                        val newTime = if (decodedFormat!!.sampleRate >0) (total / decodedFormat!!.sampleRate / decodedFormat!!.frameSize).toDouble() else 0.0
                        if (newTime - oldTime > 2) { // update every 2 secs
                            oldTime = newTime ; ptimepos = newTime ; CompletableFuture.runAsync{ onProgress(ptimepos, timelen) }
                        }
                        // debug(s" loop: bytesread=$bytesRead total=$total length=$length")
                    }
                    //debug(s"fut loop: read=$bytesRead bufsize=$bufferSize length=$length x=" + audioIn.available() + " %:" + (100.0*(length-audioIn.available())/length))
                }
                //debug("fut: end action=" + action)
            }.thenApplyAsync {
                logger.debug("fut: thenApplyAsync [$songurl]... action=$action")
                if (action.get() != Actions.ASTOP.i) sdl!!.drain() // wait until all played
                logger.debug("  drain finished, action=$action")
                isPlaying.set(false)
                emitPlayingStateChanged()
                if (action.get() != Actions.ASTOP.i) CompletableFuture.runAsync { onCompleted() }
            }.handle { _, u ->
                logger.debug("fut: handle [$songurl]: error = $u", u)
                cleanup()
                logger.debug("fut handle [$songurl]: done")
            }

            logger.debug("playatpos/")

            return currentFile
        }
        fun cleanup() {
            bInputStream?.close()
            (conn as? HttpURLConnection)?.disconnect()
            audioInDec?.close()
            audioIn?.close()
            if (sdl != null) { if (sdl!!.isOpen) { sdl!!.stop() ; sdl!!.close() } }
            sdl?.close()
        }
        init {
            logger.debug("plaything initialized!")
        }
    }

    fun dogetMixers(): List<String> {
        return AudioSystem.getMixerInfo().map{ mi -> mi.name }.filter{ s -> !s.startsWith("Port ")} // TODO check if also ok on win/lin
    }

    fun emitPlayingStateChanged() {
        onPlayingStateChanged(!playThing.isPlaying.get(), playThing.isPaused.get())
    }

    fun setPause(pause: Boolean) {
        playThing.isPaused.set(pause)
        emitPlayingStateChanged()
    }
    fun dogetPause(): Boolean = playThing.isPaused.get()
    fun dogetPlaying(): Boolean = playThing.isPlaying.get()
    fun stop() {
        playThing.isPaused.set(false)
        playThing.action.set(Actions.ASTOP.i)
        emitPlayingStateChanged()
    }
    fun skipTo(s: Double) {
        logger.debug("skip to $s...")
        playThing.actionVal.set(s)
        playThing.action.set(Actions.ASKIPTO.i)
    }
    fun skipRel(s: Double) {
        playThing.actionVal.set(s)
        playThing.action.set(Actions.ASKIPREL.i)
    }
    fun setVolume(vol: Double) {
        playThing.setVolume(vol)
    }
//    fun dogetVolume() = playThing.volume!!.value

}
