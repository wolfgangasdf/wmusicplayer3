import mu.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

private val logger = KotlinLogging.logger {}

object Settings {

    var playlistDefault = ""
    var pCurrentFolder = ""
    var playlistFolder = ""
    var recentDirs = Stack<String>()
    val bQuickPls = mutableListOf<String>()
    var mixer = ""

    var firstRun = false

    val props = java.util.Properties()
    fun getSettingsFile(): File {
        val fp = File(".").getAbsoluteFile().getParentFile() // gets CWD!
        return File(fp.getPath() + File.separator + "wmpsettings.txt")
    }

    fun load() {
        val ff = getSettingsFile()
        logger.debug("load config: settings file = " + ff.getPath())
        if (!ff.exists()) {
            ff.createNewFile()
            firstRun = true
        }
        props.load(FileInputStream(ff))
        MusicPlayer.pVolume.value = props.getProperty("volume","50").toInt()
        playlistDefault = props.getProperty("playlistdefault","")
        playlistFolder = props.getProperty("playlistfolder","")
        pCurrentFolder = props.getProperty("currentfolder","/")
        mixer = props.getProperty("mixer","")
        for (ii in 0 until Constants.NQUICKPLS) {
        bQuickPls += props.getProperty("quickpls" + ii, "")
    }
        MusicPlayer.updateVolume()
    }

    fun save() {
        try {
            val ff = getSettingsFile()
            logger.debug("save config: settings file = " + ff.getPath())
            props.put("volume",MusicPlayer.pVolume.value.toString())
            props.put("playlistdefault", playlistDefault)
            props.put("playlistfolder", playlistFolder)
            props.put("currentfolder", pCurrentFolder)
            props.put("mixer", mixer)
            for (ii in 0 until Constants.NQUICKPLS) {
                props.put("quickpls" + ii, bQuickPls[ii])
            }
            val fos = FileOutputStream(ff)
            props.store(fos,null)
            fos.close()
        } catch(e: Throwable) {
            logger.debug("got ex",e)
        }
    }

    init {
        load()
    }
}