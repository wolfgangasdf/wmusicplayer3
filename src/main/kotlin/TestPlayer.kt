import mu.KotlinLogging
import java.io.File
import java.net.URI
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine


@Suppress("unused")
object TestFlac {
    fun main() {
        var audioInputStream = AudioSystem.getAudioInputStream(File("/Unencrypted_Data/Music/10-thievery_corporation-safar_(the_journey)_(feat._lou_lou).flac"))
        var audioFormat = audioInputStream.format
        println("Play input audio format=$audioFormat")
        if (audioFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
            // if ((audioFormat.getEncoding() != AudioFormat.Encoding.PCM) ||
            //     (audioFormat.getEncoding() == AudioFormat.Encoding.ALAW) ||
            //     (audioFormat.getEncoding() == AudioFormat.Encoding.MP3)) {
            val newFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
            audioFormat.sampleRate,
            16,
            audioFormat.channels,
            audioFormat.channels * 2,
            audioFormat.sampleRate,
            false)
            println("Converting audio format to $newFormat")
            val newStream = AudioSystem.getAudioInputStream(newFormat, audioInputStream)
            audioFormat = newFormat
            audioInputStream = newStream
        }
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        if (!AudioSystem.isLineSupported(info)) {
            println("Play.playAudioStream does not handle this type of audio on this system.")
            return
        }
        val dataLine = AudioSystem.getLine(info) as SourceDataLine
        dataLine.open(audioFormat)
        dataLine.start()
        val bufferSize = (audioFormat.sampleRate * audioFormat.frameSize).toInt()
        val buffer = ByteArray(bufferSize)

        // Move the data until done or there is an error.
        try {
            var bytesRead = 0
            while (bytesRead >= 0) {
                bytesRead = audioInputStream.read(buffer, 0, buffer.size)
                if (bytesRead >= 0) {
                    // System.out.println("Play.playAudioStream bytes read=" + bytesRead +
                    //    ", frame size=" + audioFormat.getFrameSize() + ", frames read=" + bytesRead / audioFormat.getFrameSize());
                    // Odd sized sounds throw an exception if we don't write the same amount.
                    dataLine.write(buffer, 0, bytesRead)
                }
            } // while
        } catch(e: Exception) {
            e.printStackTrace()
        }

        println("Play.playAudioStream draining line.")
        // Continues data line I/O until its buffer is drained.
        dataLine.drain()

        println("Play.playAudioStream closing line.")
        // Closes the data line, freeing any resources such as the audio device.
        dataLine.close()


    }
}

/*
@Suppress("unused")
object TestBackend {

    private fun waitForPlayer() {
        while (MusicPlayerBackend.dogetPlaying()) {
            println("playing...")
            Thread.sleep(200)
        }
    }

    fun main() {
        MusicPlayerBackend.onCompleted = { println("onFinished1") }
        print("play res = " + MusicPlayerBackend.play("file:///Unencrypted_Data/Music/111.wav"))

        Thread.sleep(500)
        MusicPlayerBackend.stop() // should stop, and NOT call onCompleted
        waitForPlayer()

        println("test play other song")
        MusicPlayerBackend.onCompleted = { println("onFinished2") }
        print("play res = " + MusicPlayerBackend.play("file:///Unencrypted_Data/Music/111.wav"))
        Thread.sleep(500)
        print("play res = " + MusicPlayerBackend.play("file:///Unencrypted_Data/Music/222.wav"))
        waitForPlayer()

        println("test gapless")
        val songs = listOf("audiocheck.net_sin_1000Hz_-3dBFS_1.5s.wav", "audiocheck.net_sin_2000Hz_-3dBFS_1.5s.wav", "audiocheck.net_sin_3000Hz_-3dBFS_1.5s.wav")
        var songi = 0
        MusicPlayerBackend.onCompleted = {
            println("onFinished3")
            if (songi <= songs.size - 2) {
                songi += 1
                print("play res = " + MusicPlayerBackend.play("file:///Unencrypted_Data/Music/" + songs[songi]))
            }
        }
        print("play res = " + MusicPlayerBackend.play("file:///Unencrypted_Data/Music/" + songs[songi]))
        waitForPlayer()

        println("test skipping")
        MusicPlayerBackend.onCompleted = {} // have to clear it...
        MusicPlayerBackend.play("file:///Unencrypted_Data/Music/10-thievery_corporation-safar_(the_journey)_(feat._lou_lou).flac")
        Thread.sleep(1500)
        MusicPlayerBackend.skipRel(10.0)
        Thread.sleep(2000)
        MusicPlayerBackend.skipRel(-2.0)
        Thread.sleep(2000)
        MusicPlayerBackend.skipRel(-2.0)
        Thread.sleep(2000)
        MusicPlayerBackend.skipRel(-2.0)
        Thread.sleep(2000)

        println("test volume")
//    MusicPlayerBackend.play("file:///Unencrypted_Data/Music/10-thievery_corporation-safar_(the_journey)_(feat._lou_lou).flac", -1)
        MusicPlayerBackend.play("file:///Unencrypted_Data/Music/04Daft Punk - Within.mp3")
        MusicPlayerBackend.skipRel(10.0)
        Thread.sleep(1500)
        MusicPlayerBackend.setVolume(50)
        Thread.sleep(2500)
        MusicPlayerBackend.setVolume(100)
        Thread.sleep(1500)
        MusicPlayerBackend.setVolume(50)
        Thread.sleep(1500)

    }
}
*/


@Suppress("unused")
object TestStream {
    fun main() {
//        val url = "http://ice.somafm.com/groovesalad"
//        val url = "http://ice1.somafm.com/deepspaceone-128-mp3"
    val url = URI("http://listen.radionomy.com/ABC-Lounge") // from shoutcast.com
        print("play res = " + MusicPlayerBackend.play(url))
        while (MusicPlayerBackend.dogetPlaying()) {
            println("playing...")
            Thread.sleep(1000)
        }
    }
}

@Suppress("unused")
object TestStreamSkipping {
    fun main() {
//        val url = "http://ice.somafm.com/groovesalad"
//        val url = "http://ice1.somafm.com/deepspaceone-128-mp3"
        val url = URI("http://listen.radionomy.com/ABC-Lounge") // from shoutcast.com
        println("play res = " + MusicPlayerBackend.play(url))
        Thread.sleep(2000)
        println("playing: ${MusicPlayerBackend.dogetPlaying()}")
        println("stopping...")
        MusicPlayerBackend.stop()
        Thread.sleep(2000)
        println("stopped, playing again...")
        println("play res = " + MusicPlayerBackend.play(url))
        while (MusicPlayerBackend.dogetPlaying()) {
            println("playing...")
            Thread.sleep(1000)
        }
    }
}

fun main() {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")

    val logger = KotlinLogging.logger {} // WTF, can't define Logger outside because then no debug.

    fun testot() {
        println("debug: " + logger.isDebugEnabled)
        logger.info("iiiinfo")
        logger.debug("ddddebug")
    }

    testot()
//    TestStream.main()
    TestStreamSkipping.main()
//    TestBackend.main(args)
//    TestFlac.main(args)
}


