import mu.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

private val logger = KotlinLogging.logger {}


// TODO move to musicplayer.kt? some things should update the UI to avoid race conditions (e.g. pls folder / names).
// also: must be atomic???
object Settings {

    var playlistDefault = ""
    var pCurrentFolder = ""
    var playlistFolder = ""
    var recentDirs = Stack<String>()
    val bQuickPls = mutableListOf<String>() // TODO this should be an observable property
    var mixer = ""

    private var firstRun = false
    private val props = java.util.Properties()

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
            logger.debug("save config: settings file = " + ff.path)
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

    fun getplscap(ii: Int): String {
        return if (Settings.bQuickPls[ii] == "")
            "none"
        else {
            val f = File(Settings.bQuickPls[ii]).name.replace(".pls","")
            val res = f.substring(0, listOf(7,f.length).min()!!)
            res
        }
    }


    init {
        load()
    }
}