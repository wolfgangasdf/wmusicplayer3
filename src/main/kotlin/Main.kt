import eu.webtoolkit.jwt.*
import kotlin.concurrent.fixedRateTimer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import eu.webtoolkit.jwt.WLength
import eu.webtoolkit.jwt.WAbstractItemView
import eu.webtoolkit.jwt.WTableView
import eu.webtoolkit.jwt.WString
import eu.webtoolkit.jwt.ItemDataRole
import eu.webtoolkit.jwt.WModelIndex
import eu.webtoolkit.jwt.WObject
import eu.webtoolkit.jwt.WAbstractTableModel
import eu.webtoolkit.jwt.WApplication
import java.util.*


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
            println("timer! app=$app ${Date().time} ${app?.cplayer?.songinfo1}")
            val uiLock = app?.updateLock
            app?.cplayer?.songinfo2?.setText(Date().time.toString())
            app?.triggerUpdate()
            uiLock?.release()
        })
    }

    var app: HelloApplication? = null
}

class CPlayer: WContainerWidget() {
    val lplayer = WVBoxLayout(this)
    val btplay = KWPushButton("►", "Toggle play/pause", { println("playpause") })
    val slider = WSlider()
    val volume = WLineEdit("0")
    val timecurr = WText("1:00")
    val timelen = WText("1:05:23")
    val songinfo1 = WText("<b>songi1</b>")
    val songinfo2 = WText("songi2")
    val quickbtns = Array(6, { i -> KWPushButton("pls $i", "load Playlist $i", { println("qloadpls $i") })})

    init {
        slider.isNativeControl = true // doesn't work if not!
        slider.tickPosition = WSlider.NoTicks
        slider.height = btplay.height

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
    val lplaylist = WVBoxLayout(this)
    val plname = WLineEdit("<pl name>")
    val tvplaylist = WTableView()
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
    val lfiles = WVBoxLayout(this)
    val tvfiles = WTableView()
    val currentfolder = WText("currentfolder")
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
            addit(KWPushButton("?", "Help", { println("help") }))
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

class HelloApplication(env: WEnvironment) : WApplication(env) {
    val cplayer = CPlayer()
    val cplaylist = CPlaylist()
    val cfiles = CFiles()

    init {
        setTitle("WMusicPlayer")

        setCssTheme("polished")
//        useStyleSheet(WLink("style/everywidgetx.css"))

//        val theme = WBootstrapTheme()
//        theme.version = WBootstrapTheme.Version.Version3
//        // load the default bootstrap3 (sub-)theme
//        useStyleSheet(WLink(WApplication.getRelativeResourcesUrl() + "themes/bootstrap/3/bootstrap-theme.min.css"))

//        setTheme(WBootstrapTheme())

        val lmain = WGridLayout(root)
        lmain.addWidget(cplayer, 0, 0, 1, 2)
        lmain.addWidget(cplaylist, 1, 0, 1, 1)
        lmain.addWidget(cfiles, 1, 2, 1, 1)
        lmain.setRowStretch(1, 1)

        BackendSingleton.app = this
        enableUpdates()
    }
}

class HelloMain : WtServlet() {

    override fun createApplication(env: WEnvironment): WApplication {
        return HelloApplication(env)
    }

    companion object {
        private val serialVersionUID = 1L
    }

    init {
        super.init()
        // Enable websockets only if the servlet container has support for JSR-356 (Jetty 9, Tomcat 7, ...)
        // configuration.setWebSocketsEnabled(true);
    }
}

fun main(args: Array<String>) {

    val server = Server(8080)

    val context = ServletContextHandler(ServletContextHandler.SESSIONS)

    context.addServlet(HelloMain::class.java, "/*") // star is essential for css etc!
    context.addEventListener(ServletInit())
//    context.baseResource = Resource.newResource("WebRoot")

    server.handler = context

    server.start()
    server.join()
}

