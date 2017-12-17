import eu.webtoolkit.jwt.*
import mu.KotlinLogging
import java.util.*
import kotlin.concurrent.fixedRateTimer


private val logger = KotlinLogging.logger {}

internal class VirtualModel(private val rows_: Int, private val columns_: Int, parent: WObject) : WAbstractTableModel(parent) {

    override fun getRowCount(parent: WModelIndex?): Int {
        return if (parent == null) {
            this.rows_
        } else {
            0
        }
    }

    override fun getColumnCount(parent: WModelIndex?): Int {
        return if (parent == null) {
            this.columns_
        } else {
            0
        }
    }

    override fun getData(index: WModelIndex, role: Int): Any? {
        return when (role) {
            ItemDataRole.DisplayRole -> if (index.column == 0) {
                WString("Row {1}").arg(index.row)
            } else {
                WString("Item row {1}, col {2}").arg(
                        index.row).arg(index.column)
            }
            else -> null
        }
    }

    override fun getHeaderData(section: Int, orientation: Orientation, role: Int): Any? {
        return if (orientation === Orientation.Horizontal) {
            when (role) {
                ItemDataRole.DisplayRole -> WString("Column {1}").arg(section)
                else -> null
            }
        } else {
            null
        }
    }

}

object BackendSingleton {
    init {
        fixedRateTimer("testt", false, 0, 1000, {
            Thread.sleep(1000)
//            println("timer! app=$app ${Date().time} ${app?.cplayer?.songinfo1}")
            val uiLock = app?.updateLock
            app?.cplayer?.songinfo2?.setText(Date().time.toString())
            app?.triggerUpdate()
            uiLock?.release()
        })
    }

    var app: JwtApplication? = null
}

class CPlayer: WContainerWidget() {
    private val lplayer = WVBoxLayout(this)
    private val btplay = KWPushButton("►", "Toggle play/pause", { println("playpause") })
    private val slider = WSlider()
    private val volume = WLineEdit("0")
    private val timecurr = WText("1:00")
    private val timelen = WText("1:05:23")
    private val songinfo1 = WText("<b>songi1</b>")
    val songinfo2 = WText("songi2")
    private val quickbtns = Array(6, { i -> KWPushButton("pls $i", "load Playlist $i", { println("qloadpls $i") })})

    init {
        slider.isNativeControl = true // doesn't work if not!
        slider.tickPosition = WSlider.NoTicks
        slider.height = btplay.height
        slider.valueChanged().addListener(this, { i -> println("slider: $i") })

        volume.validator = WIntValidator(0, 500)
        volume.textSize = 3

        lplayer.addWidget(kJwtHBox(this){
            addit(KWPushButton("⇠", "Previous song", { println("prev song") }))
            addit(btplay)
            addit(KWPushButton("⇥", "Next song", { println("next song") }))
            addit(KWPushButton("⇠", "Skip -10s", { println("skip -10s") }))
            addit(slider)
            addit(KWPushButton("⇢", "Skip +10s", { println("skip +10s") }))
            addit(KWPushButton("V-", "Volume down", { println("vol down") }))
            addit(volume)
            addit(KWPushButton("V+", "Volume up", { println("vol up") }))
        })

        lplayer.addWidget(kJwtHBox(this){
            addit(timecurr)
            addit(timelen)
            addit(songinfo1, 1)
        })
        lplayer.addWidget(songinfo2)

        lplayer.addWidget(kJwtHBox(this){
            quickbtns.forEach { qb -> addit(qb) }
        })

        // TODO: playlist quick buttons

    }
}

class CPlaylist: WContainerWidget() {
    private val lplaylist = WVBoxLayout(this)
    private val plname = WLineEdit("<pl name>")
    private val tvplaylist = WTableView()
    init {
        lplaylist.addWidget(kJwtHBox(this){
            addit(plname, 1)
            addit(KWPushButton("save", "Save current playlist to current name", { println("save pls") }))
            addit(KWPushButton("✖✖", "Clear playlist", { println("clear pls") }))
            addit(KWPushButton("✖", "Remove selected songs from playlist", { println("remove from playlist") }))
        })

        tvplaylist.model = VirtualModel(100, 3, tvplaylist)
        tvplaylist.rowHeaderCount = 1
        tvplaylist.isSortingEnabled = false
        tvplaylist.setAlternatingRowColors(true)
        tvplaylist.rowHeight = WLength(28.0)
        tvplaylist.headerHeight = WLength(28.0)
        tvplaylist.selectionMode = SelectionMode.ExtendedSelection
        tvplaylist.editTriggers = EnumSet.of<WAbstractItemView.EditTrigger>(WAbstractItemView.EditTrigger.NoEditTrigger)
        tvplaylist.resize(WLength(650.0), WLength(400.0))
        lplaylist.addWidget(tvplaylist, 1)
    }
}

class CFiles: WContainerWidget() {
    private val lfiles = WVBoxLayout(this)
    private val tvfiles = WTableView()
    private val currentfolder = WText("currentfolder")
    init {
        lfiles.addWidget(currentfolder)
        lfiles.addWidget(kJwtHBox(this){
            addit(KWPushButton("+", "Add current file to playlist", { println("add+") }))
            addit(KWPushButton("++", "Add all files in current folder to playlist", { println("add++") }))
            addit(KWPushButton("+++", "Add all files in current folder recursively to playlist", { println("add+++") }))
            addit(KWPushButton("⇧", "Go to parent folder", { println("parentfolder") }))
            addit(KWPushButton("⇦", "Go to previous folder", { println("prevfolder") }))
            addit(KWPushButton("pls", "Go to playlist folder", { println("plsfolder") }))
            addit(KWPushButton("✖", "Delete selected playlist file", { println("delpls") }))
            addit(KWPushButton("S", "Settings", { println("settings") }))
            addit(KWPushButton("?", "Help", {
                println("help")
            }))
        })

        tvfiles.model = VirtualModel(100, 3, tvfiles)
        tvfiles.rowHeaderCount = 1
        tvfiles.isSortingEnabled = false
        tvfiles.setAlternatingRowColors(true)
        tvfiles.rowHeight = WLength(28.0)
        tvfiles.headerHeight = WLength(28.0)
        tvfiles.selectionMode = SelectionMode.ExtendedSelection
        tvfiles.editTriggers = EnumSet.of<WAbstractItemView.EditTrigger>(WAbstractItemView.EditTrigger.NoEditTrigger)
        tvfiles.resize(WLength(650.0), WLength(400.0))
        lfiles.addWidget(tvfiles, 1)
    }

}

class JwtApplication(env: WEnvironment) : WApplication(env) {
    val cplayer = CPlayer()
    private val cplaylist = CPlaylist()
    private val cfiles = CFiles()

    init {
        logger.info("initialize Application thread=${Thread.currentThread().id} agent=${env.agent}")

        setTitle("WMusicPlayer")

        setCssTheme("polished")

        useStyleSheet(WLink("style/everywidgetx.css")) // TODO this does not work (returns web app due to /*)

//        theme = WBootstrapTheme()

        val lmain = WGridLayout(root)
        lmain.addWidget(cplayer, 0, 0, 1, 2)
        lmain.addWidget(cplaylist, 1, 0, 1, 1)
        lmain.addWidget(cfiles, 1, 2, 1, 1)
        lmain.setRowStretch(1, 1)
        lmain.addWidget(KWPushButton("debug", "...", {
            logger.debug("debug: ${env.hasAjax()}")
        }), 2, 0)

        BackendSingleton.app = this
        enableUpdates()

        logger.info("Application initialized!")
        logger.debug("env: ${env.hasAjax()} ${env.hasJavaScript()} ${env.hasWebGL()}")
    }
}

class JwtServlet : WtServlet() {

    override fun createApplication(env: WEnvironment): WApplication {
        return JwtApplication(env)
    }

    companion object {
        private val serialVersionUID = 1L
    }

    init {
        logger.info("servlet init...")
        configuration.setProgressiveBootstrap(true) // TODO testing
        configuration.favicon = "/favicon.ico" // TODO nothing works, hardcoded paths in jwt...

        super.init()
        logger.info("servlet config: pbs:${configuration.progressiveBootstrap("/")} ua:${configuration.uaCompatible}")

        // Enable websockets only if the servlet container has support for JSR-356 (Jetty 9, Tomcat 7, ...)
        // configuration.setWebSocketsEnabled(true);
        logger.info("servlet initialized!")
    }
}