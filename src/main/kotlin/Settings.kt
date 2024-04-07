import mu.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

private val logger = KotlinLogging.logger {}


object Settings {

    var port = 8083
    var playlistDefault = ""
    var pCurrentFolder = ""
    var playlistFolder = ""
    var recentDirs = Stack<String>()
    val bQuickPls = mutableListOf<String>()
    var audioDevice = ""

    private var firstRun = false
    private val props = Properties()

    private fun getSettingsFile(): File {
        val fp = File(".").absoluteFile.parentFile // gets CWD!
        return File(fp.path + File.separator + "wmpsettings.txt")
    }

    private fun load() {
        val ff = getSettingsFile()
        logger.debug("load config: settings file = " + ff.path)
        if (!ff.exists()) {
            ff.createNewFile()
            firstRun = true
        }
        props.load(FileInputStream(ff))
        MusicPlayer.pVolume.value = props.getProperty("volume","50").toInt()
        port = props.getProperty("port","8083").toInt()
        playlistDefault = props.getProperty("playlistdefault","")
        playlistFolder = props.getProperty("playlistfolder","")
        pCurrentFolder = props.getProperty("currentfolder","/")
        audioDevice = props.getProperty("mixer","")
        for (ii in 0 until Constants.NQUICKPLS) {
            bQuickPls += props.getProperty("quickpls$ii", "")
        }
        MusicPlayer.updateVolume()
    }

    fun save() {
        try {
            val ff = getSettingsFile()
            logger.debug("save config: settings file = " + ff.path)
            props["port"] = port.toString()
            props["volume"] = MusicPlayer.pVolume.value.toString()
            props["playlistdefault"] = playlistDefault
            props["playlistfolder"] = playlistFolder
            props["currentfolder"] = pCurrentFolder
            props["mixer"] = audioDevice
            for (ii in 0 until Constants.NQUICKPLS) {
                props["quickpls$ii"] = bQuickPls[ii]
            }
            val fos = FileOutputStream(ff)
            props.store(fos,null)
            fos.close()
        } catch(e: Throwable) {
            logger.debug("got ex",e)
        }
    }

    fun getplscap(ii: Int): String {
        return if (bQuickPls[ii] == "")
            "none"
        else {
            val f = File(bQuickPls[ii]).name.replace(".pls","")
            val res = f.substring(0, listOf(7,f.length).minOrNull()!!)
            res
        }
    }


    init {
        load()
    }
}