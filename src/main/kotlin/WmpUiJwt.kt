@file:Suppress("unused")

import eu.webtoolkit.jwt.*
import mu.KotlinLogging
import java.util.*
import kotlin.concurrent.fixedRateTimer
import eu.webtoolkit.jwt.WString
import eu.webtoolkit.jwt.WText
import java.util.EnumSet
import eu.webtoolkit.jwt.WLength
import eu.webtoolkit.jwt.WSelectionBox
import eu.webtoolkit.jwt.WContainerWidget
import java.io.File
import eu.webtoolkit.jwt.StandardButton
import eu.webtoolkit.jwt.WMessageBox
import javafx.beans.Observable
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener


private val logger = KotlinLogging.logger {}

enum class UIMode {
    UINORMAL, UIMINI, UISINGLECOL
}

class SettingsWindow : WDialog("Settings") {
    private val lplayer = WVBoxLayout(this.contents)
    private val mixers = MusicPlayer.getMixers()
    private val sbmixer = kJwtGeneric({ WSelectionBox() }) {
        for (mix in mixers) addItem(mix)
        if (mixers.contains(Settings.mixer)) currentIndex = mixers.indexOf(Settings.mixer)
        setMargin(WLength(10.0), EnumSet.of(Side.Right))
    }
    init {
        this.isModal = true
        this.footer.addWidget(KWPushButton("OK", "Save settings", {
            Settings.mixer = sbmixer.currentText.toString()
            Settings.save()
            this.accept()
        }))
        this.footer.addWidget(KWPushButton("Cancel", "", { this.reject() }))

        lplayer.addWidget(kJwtVBox(this.contents) {
            addit(WLabel("Audio mixers:"))
            addit(sbmixer)
        })
        lplayer.addWidget(KWPushButton("Reset playlist folder", "set again afterwards!", { Settings.playlistFolder = "" }))
    }
}

// TODO need iscurr?
class ModelPlaylist(app: WApplication, parent: WObject) : WAbstractTableModel(parent) {

    val lplaylist = MusicPlayer.cPlaylist

    override fun getRowCount(parent: WModelIndex?): Int {
        return if (parent == null) { lplaylist.size } else 0
    }

    //bug: can't scroll first column bug: add thin column before!
    override fun getColumnCount(parent: WModelIndex?): Int {
        return if (parent == null) 2 else 0
    }

    override fun getData(index: WModelIndex, role: Int): Any? {
        return when (role) {
            ItemDataRole.DisplayRole -> WString(if (index.column == 1) lplaylist[index.row].title else "")
            else -> null
        }
    }

    override fun getHeaderData(section: Int, orientation: Orientation, role: Int): Any? {
        return if (orientation === Orientation.Horizontal) {
            when (role) {
                ItemDataRole.DisplayRole -> if (section == 1) "Title" else ""
                else -> null
            }
        } else {
            null
        }
    }

    init {
        logger.debug("initialize ModelPlaylist...: " + WApplication.getInstance().sessionId + " threadid: " + Thread.currentThread().id)
        lplaylist.addListener(ListChangeListener { obs ->
            val uiLock = app.updateLock
            modelReset().trigger()
            uiLock?.release()
        })
    }

}


class ModelFiles(parent: WObject) : WAbstractTableModel(parent) {

    val lfiles = FXCollections.observableArrayList<File>()!!

    override fun getRowCount(parent: WModelIndex?): Int {
        return if (parent == null) { lfiles.size } else 0
    }

    //bug: can't scroll first column bug: add thin column before!
    override fun getColumnCount(parent: WModelIndex?): Int {
        return if (parent == null) 2 else 0
    }

    override fun getData(index: WModelIndex, role: Int): Any? {
        return when (role) {
            ItemDataRole.DisplayRole -> WString(if (index.column == 1) lfiles[index.row].name else "")
            else -> null
        }
    }

    override fun getHeaderData(section: Int, orientation: Orientation, role: Int): Any? {
        return if (orientation === Orientation.Horizontal) {
            when (role) {
                ItemDataRole.DisplayRole -> if (section == 1) "Filename" else "xxx"
                else -> null
            }
        } else {
            null
        }
    }

    init {
        lfiles.addListener { obs: Observable ->
            modelReset().trigger()
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

class CPlayer(app: JwtApplication) : WContainerWidget() {
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

class CPlaylist(app: JwtApplication) : WContainerWidget() {
    private val lplaylist = WVBoxLayout(this)
    var mplaylist: ModelPlaylist? = null
    private val plname = WLineEdit("<pl name>")

    private val tvplaylist = kJwtGeneric({ WTableView() }) {
        mplaylist = ModelPlaylist(app,this)
        model = mplaylist
        rowHeaderCount = 1
        isSortingEnabled = false
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)

        setColumnWidth(0, WLength(0.0))
        setColumnWidth(1, WLength(300.0))
        headerHeight = WLength(28.0)
        selectionMode = SelectionMode.ExtendedSelection
        selectionBehavior = SelectionBehavior.SelectRows
        editTriggers = EnumSet.of<WAbstractItemView.EditTrigger>(WAbstractItemView.EditTrigger.NoEditTrigger)
        resize(WLength(2000.0), WLength(2000.0))
        doubleClicked().addListener(this, { mi, me -> println(" dclick: " + if (mi != null) mplaylist!!.lplaylist[mi.row] else "none") })
    }

    init {
        lplaylist.addWidget(kJwtHBox(this){
            addit(plname, 1)
            addit(KWPushButton("save", "Save current playlist to current name", {
                if (Settings.playlistFolder == "") {
                    WMessageBox.show("Info", "<p>You need to assign a playlist folder first!</p>", EnumSet.of(StandardButton.Ok))
                } else {
                    MusicPlayer.savePlaylist()
                    println("saved playlist") // make floating thing.
                }
            }))
            addit(KWPushButton("✖✖", "Clear playlist", {
                MusicPlayer.cPlaylist.clear()
            }))
            addit(KWPushButton("✖", "Remove selected songs from playlist", {
                tvplaylist.selectedIndexes.map { mi -> mi.row }.reversed().forEach { i ->  mplaylist!!.lplaylist.removeAt(i) }
            }))
        })

        lplaylist.addWidget(tvplaylist, 1)
    }
}

class CFiles(val app: JwtApplication) : WContainerWidget() {
    private val lfiles = WVBoxLayout(this)
    private var mfiles: ModelFiles? = null
    private val currentfolder = WText("currentfolder")

    private val tvfiles = kJwtGeneric({ WTableView() }) {
        mfiles = ModelFiles(this)
        model = mfiles
        rowHeaderCount = 1
        isSortingEnabled = false
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)

        setColumnWidth(0, WLength(0.0))
        setColumnWidth(1, WLength(300.0))
        headerHeight = WLength(28.0)
        selectionMode = SelectionMode.ExtendedSelection
        selectionBehavior = SelectionBehavior.SelectRows
        editTriggers = EnumSet.of<WAbstractItemView.EditTrigger>(WAbstractItemView.EditTrigger.NoEditTrigger)
        resize(WLength(2000.0), WLength(2000.0))
        doubleClicked().addListener(this, { mi, me ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (f.isDirectory) addFileToPlaylist(f)
            }
        })
        clicked().addListener(this, { mi, me ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (f.isDirectory) changeDir(f.path)
            }
        })
    }
    private fun loadDir(selectFile: File? = null) {
        mfiles!!.lfiles.clear()
        val fdir = File(Settings.pCurrentFolder)
        if (fdir.exists()) {
            currentfolder.setText(Settings.pCurrentFolder)
            var cc = fdir.listFiles( { file -> Constants.soundFilePls.matches(file.name) || (file.isDirectory && !file.name.startsWith(".")) })
            cc = cc.sortedByDescending { a -> a.name.toLowerCase() }.toTypedArray()
            mfiles!!.lfiles.addAll(cc)
            if (selectFile != null) {
                val sidx = cc.indexOfFirst { c -> c.path == selectFile.path }
                if (sidx > -1) {
                    val mi = mfiles!!.getIndex(sidx, 0)
                    tvfiles.select(mi)
                    tvfiles.scrollTo(mi)
                }
            }
        }
    }

    private fun changeDir(newPath: String, selectFile: File? = null, dontStore: Boolean = false) {
        logger.debug("chdir to $newPath ds=$dontStore")
        if (!dontStore) Settings.recentDirs.push(Settings.pCurrentFolder)
        Settings.pCurrentFolder = newPath
        currentfolder.setText(Settings.pCurrentFolder)
        Settings.save()
        loadDir(selectFile)
    }

    private fun addFileToPlaylist(f: File) {
        if (!f.isDirectory) {
            MusicPlayer.addToPlaylist("file://" + f.path)
        }
    }

    init {
        lfiles.addWidget(currentfolder)
        lfiles.addWidget(kJwtHBox(this){
            addit(KWPushButton("+", "Add current file to playlist", {
                tvfiles.selectedIndexes.forEach { mi -> addFileToPlaylist(mfiles!!.lfiles[mi.row])}
            }))
            addit(KWPushButton("++", "Add all files in current folder to playlist", {
                mfiles!!.lfiles.forEach { f -> addFileToPlaylist(f) }
            }))
            addit(KWPushButton("+++", "Add all files in current folder recursively to playlist", {
                File(currentfolder.text.toString()).walkTopDown().forEach { f -> addFileToPlaylist(f) }
            }))
            addit(KWPushButton("⇧", "Go to parent folder", {
                val newcf = File(Settings.pCurrentFolder).parent
                if (newcf != null) changeDir(newcf, File(Settings.pCurrentFolder))
            }))
            addit(KWPushButton("⇦", "Go to previous folder", {
                logger.debug("recent dirs ne=${Settings.recentDirs.isNotEmpty()} :" + Settings.recentDirs.joinToString { s -> s })
                if (Settings.recentDirs.isNotEmpty()) changeDir(Settings.recentDirs.pop(), dontStore = true)
            }))
            addit(KWPushButton("pls", "Go to playlist folder (or set if empty)", {
                if (Settings.playlistFolder == "") {
                    if (WMessageBox.show("Warning", "<p>Set playlist folder to current folder?</p>", EnumSet.of(StandardButton.Ok, StandardButton.Cancel))
                            == StandardButton.Ok) {
                        Settings.playlistFolder = currentfolder.text.toString()
                        Settings.save()
                    }
                } else changeDir(Settings.playlistFolder)
            }))
            addit(KWPushButton("✖", "Delete selected playlist file", {
                tvfiles.selectedIndexes.forEach { mi ->
                    val f = mfiles!!.lfiles[mi.row]
                    if (f.name.endsWith(".pls")) {
                        f.delete()
                        logger.info("deleted ${f.path}")
                    }
                    loadDir()
                }
            }))
            addit(KWPushButton("S", "Settings", {
                SettingsWindow().show()
            }))
            addit(KWPushButton("?", "Help", {
                println("help")
            }))
        })
        lfiles.addWidget(tvfiles, 1)
        loadDir()
    }

}

class JwtApplication(env: WEnvironment) : WApplication(env) {
    val cplayer = CPlayer(this)
    val cplaylist = CPlaylist(this)
    val cfiles = CFiles(this)
    val tInfo = WText("info")

    override fun quit() {
        logger.debug("quit application thread=${Thread.currentThread().id} agent=${environment.agent}")
        super.quit()
    }

    init {
        logger.info("initialize Application sid=${WApplication.getInstance().sessionId} thread=${Thread.currentThread().id} agent=${env.agent}")

        setTitle("WMusicPlayer")

        setCssTheme("polished")

        useStyleSheet(WLink("style/everywidgetx.css")) // TODO this does not work (returns web app due to /*)

//        theme = WBootstrapTheme()

        val lmain = WGridLayout(root)
        lmain.addWidget(cplayer, 0, 0, 1, 2)
        lmain.addWidget(cplaylist, 1, 0, 1, 1)
        lmain.addWidget(cfiles, 1, 2, 1, 1)
        lmain.setRowStretch(1, 1)
        lmain.addWidget(kJwtHBox(root) {
            addit(KWPushButton("debug", "...", {
                logger.debug("debug: ${env.hasAjax()}")
                //logger.debug("debug playlist=" + MusicPlayer.cPlaylist.joinToString { pli -> pli.name })
            }), 0)
            addit(tInfo, 1)
        }, 2, 0, 1, 2)

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
//        configuration.setProgressiveBootstrap(true) // TODO testing
        configuration.favicon = "/favicon.ico" // TODO nothing works, hardcoded paths in jwt...

        super.init()
        logger.info("servlet config: pbs:${configuration.progressiveBootstrap("/")} ua:${configuration.uaCompatible}")

        // Enable websockets only if the servlet container has support for JSR-356 (Jetty 9, Tomcat 7, ...)
        // configuration.setWebSocketsEnabled(true);
        logger.info("servlet initialized!")
    }
}