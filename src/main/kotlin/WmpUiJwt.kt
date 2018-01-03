@file:Suppress("unused")

import eu.webtoolkit.jwt.*
import javafx.beans.Observable
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import mu.KotlinLogging
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.*
import kotlin.concurrent.thread


private val logger = KotlinLogging.logger {}

enum class UIMode {
    UINORMAL, UIMINI, UISINGLECOL
}

private fun getMinutes(secs: Int): String {
    val min = Math.floor(1.0/60*secs).toInt()
    return "%01d:%02d".format(min, secs-60*min)
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
        isModal = true
        footer.addWidget(KWPushButton("OK", "Save settings", {
            Settings.mixer = sbmixer.currentText.toString()
            Settings.save()
            accept()
        }))
        footer.addWidget(KWPushButton("Cancel", "", { reject() }))

        lplayer.addWidget(kJwtVBox(contents) {
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
            ItemDataRole.DisplayRole -> WString(
                    when(index.column) {
                        1 -> lplaylist[index.row].title
                        2 -> getMinutes(lplaylist[index.row].length)
                        else -> ""
                    }
            )
            else -> null
        }
    }

    override fun getHeaderData(section: Int, orientation: Orientation, role: Int): Any? {
        return null
    }

    init {
        logger.debug("initialize ModelPlaylist...: " + WApplication.getInstance().sessionId + " threadid: " + Thread.currentThread().id)
        lplaylist.addListener(ListChangeListener {
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
        return null
    }

    init {
        lfiles.addListener { _: Observable ->
            modelReset().trigger()
        }
    }

}

class CPlayer(app: JwtApplication) : WContainerWidget() {
    private val lplayer = WVBoxLayout(this)
    private val btplay = KWPushButton("►", "Toggle play/pause", { MusicPlayer.dotoggle() })
    private val slider = kJwtGeneric( { WSlider() }, {
        isNativeControl = true // doesn't work if not!
        tickPosition = WSlider.NoTicks
        height = btplay.height
        valueChanged().addListener(this, { i ->
            println("slider: $i")
            MusicPlayer.skipTo(i.toDouble())
        })
    })
    private val volume = WText(MusicPlayer.pVolume.value.toString())
    private val timecurr = WText("1:00")
    private val timelen = WText("1:05:23")
    private val songinfo1 = WText("<b>songi1</b>")
    private val songinfo2 = WText("songi2")
    private val quickbtns = Array(Constants.NQUICKPLS, { i -> KWPushButton(Settings.getplscap(i), "load Playlist $i", {
        if (bquickedit.text.value == "✏️") {
            MusicPlayer.loadPlaylist(Settings.bQuickPls[i])
            MusicPlayer.playFirst()
        } else {
            Settings.bQuickPls[i] = MusicPlayer.pPlaylistFile.value
            setText(Settings.getplscap(i))
            Settings.save()
            println("updated quick playlist button")
            bquickedit.setText("✏️")
        }
    })})
    private val bquickedit = KWPushButton("✏️", "Click to assign quick playlist button", {
        setText(if (text.value == "✏️") "✏️..." else "✏️")
    })

    init {
        lplayer.addWidget(kJwtHBox(this){
            addit(KWPushButton("⇠", "Previous song", { MusicPlayer.playPrevious() }))
            addit(btplay)
            addit(KWPushButton("⇥", "Next song", { MusicPlayer.playNext() }))
            addit(KWPushButton("⇠", "Skip -10s", { MusicPlayer.skipRel(-10.0) }))
            addit(slider)
            addit(KWPushButton("⇢", "Skip +10s", { MusicPlayer.skipRel(+10.0) }))
            addit(KWPushButton("V-", "Volume down", { MusicPlayer.incDecVolume(false) }))
            addit(volume)
            addit(KWPushButton("V+", "Volume up", { MusicPlayer.incDecVolume(true) }))
        })

        lplayer.addWidget(kJwtHBox(this){
            addit(timecurr)
            addit(timelen)
            addit(songinfo1, 1)
        })
        lplayer.addWidget(songinfo2)

        lplayer.addWidget(kJwtHBox(this){
            quickbtns.forEach { qb -> addit(qb) }
            addit(bquickedit)
        })

        // TODO rename stuff to make coherent
        MusicPlayer.pCurrentFile.addListener { _, _, newv -> doUI(app) { songinfo2.setText(newv) } }
        MusicPlayer.pCurrentSong.addListener { _, _, newv -> doUI(app) { songinfo1.setText("<b>$newv</b>") } }
        MusicPlayer.pTimePos.addListener { _, _, newv -> doUI(app) {
            timecurr.setText(getMinutes(newv.toInt()))
            slider.value = newv.toInt()
        } }
        MusicPlayer.pTimeLen.addListener { _, _, newv -> doUI(app) {
            timelen.setText(getMinutes(newv.toInt()))
            if (slider.maximum != MusicPlayer.pTimeLen.intValue()) slider.maximum = MusicPlayer.pTimeLen.intValue()
        } }
        MusicPlayer.pVolume.addListener { _, _, newv -> doUI(app) { volume.setText(newv.toString()) } }
        MusicPlayer.pIsPlaying.addListener { _, _, newv -> doUI(app) { btplay.setText(if (newv) "❙❙" else "►") } }
    }
}

class CPlaylist(app: JwtApplication) : WContainerWidget() {
    private val lplaylist = WVBoxLayout(this)
    private var mplaylist: ModelPlaylist? = null
    private val plname = WLineEdit("<pl name>")

    private val tvplaylist = kJwtGeneric({ WTableView() }) {
        mplaylist = ModelPlaylist(app,this)
        model = mplaylist
        isSortingEnabled = false
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)
        setColumnWidth(0, WLength(0.0))
        setColumnWidth(1, WLength(300.0))
        setColumnWidth(2, WLength(50.0))
        headerHeight = WLength(0.0)
        selectionMode = SelectionMode.ExtendedSelection
        selectionBehavior = SelectionBehavior.SelectRows
        editTriggers = EnumSet.of<WAbstractItemView.EditTrigger>(WAbstractItemView.EditTrigger.NoEditTrigger)
        resize(WLength(2000.0), WLength(2000.0))
        doubleClicked().addListener(this, { mi, _ ->
            if (mi != null) {
                MusicPlayer.dosetCurrentPlaylistIdx(mi.row)
                MusicPlayer.playSong()
            }
        })
        class ItemDelegate(parent: WObject): WItemDelegate(parent) {
            override fun update(widget: WWidget?, index: WModelIndex?, flags: EnumSet<ViewItemRenderFlag>?): WWidget {
                val wid = super.update(widget, index, flags)
                if (wid is IndexText) {
                    println("huhu: ${wid.index.row} <> ${MusicPlayer.pCurrentPlaylistIdx.value}")
                    if (wid.index.row == MusicPlayer.pCurrentPlaylistIdx.value) {
                        println("huhu!!!!")
                        // TODO doesn't work
//                        wid.addStyleClass("info")
                        wid.setOffsets(10)//setStyleClass("info")
                    }
                }
                return wid
            }

        }
        setItemDelegateForColumn(1, ItemDelegate(this))
        setItemDelegateForColumn(2, ItemDelegate(this))
    }

    init {
        lplaylist.addWidget(kJwtHBox(this){
            addit(plname, 1)
            addit(KWPushButton("save", "Save current playlist in playlist folder", {
                if (Settings.playlistFolder == "") {
                    WMessageBox.show("Info", "<p>You need to assign a playlist folder first!</p>", EnumSet.of(StandardButton.Ok))
                } else {
                    MusicPlayer.savePlaylist(plname.text)
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

        MusicPlayer.pPlaylistName.addListener { _, _, newv -> doUI(app) { plname.text = newv } }
        MusicPlayer.pCurrentPlaylistIdx.addListener { _, _, _ -> doUI(app) { tvplaylist.refresh() ; println("XXX: refreshed pls!") } }
    }
}

class CFiles(private val app: JwtApplication) : WContainerWidget() {
    private val lfiles = WVBoxLayout(this)
    private var mfiles: ModelFiles? = null
    private val currentfolder = WText("currentfolder")

    private val tvfiles = kJwtGeneric({ WTableView() }) {
        mfiles = ModelFiles(this)
        model = mfiles
        isSortingEnabled = false
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)
        setColumnWidth(0, WLength(0.0))
        setColumnWidth(1, WLength(300.0))
        headerHeight = WLength(0.0)
        selectionMode = SelectionMode.ExtendedSelection
        selectionBehavior = SelectionBehavior.SelectRows
        editTriggers = EnumSet.of<WAbstractItemView.EditTrigger>(WAbstractItemView.EditTrigger.NoEditTrigger)
        resize(WLength(2000.0), WLength(2000.0))
        doubleClicked().addListener(this, { mi, _ ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (!f.isDirectory) addFileToPlaylist(f)
            }
        })
        clicked().addListener(this, { mi, _ ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (f.isDirectory) changeDir(f.path)
            }
        })
    }

    private class DirWatcher {
        private fun Path.watch() : WatchService {
            val watchService = this.fileSystem.newWatchService()
            register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE)
            return watchService
        }

        var watchThread: Thread? = null
        var watcher: WatchService? = null

        fun watchOneDir(dir: File, callback: () -> Unit) {
            watcher?.close() // stops the take() in thread
            watchThread = thread(start = true) {
                val path = dir.toPath()
                // println("watcher (${dir.name} ${Thread.currentThread().id}): started")
                watcher = path.watch()
                try {
                    // don't loop because callback will start another thread before tak() started again... while (true) {
                    val key = watcher!!.take() // blocks
                    if (key != null) {
                        key.reset()
                        callback()
                    }
                } catch (e: ClosedWatchServiceException) {
                    // println("watcher (${dir.name} ${Thread.currentThread().id}): ClosedWatchServiceException!")
                }
                // println("watcher (${dir.name} ${Thread.currentThread().id}): ended")
            }
        }
    }

    private val dirWatcher = DirWatcher()
    private fun loadDir(selectFile: File? = null) {
        mfiles!!.lfiles.clear()
        val fdir = File(Settings.pCurrentFolder)
        if (fdir.exists()) {
            currentfolder.setText(Settings.pCurrentFolder)
            var cc = fdir.listFiles( { file -> Constants.soundFilePls.matches(file.name) || (file.isDirectory && !file.name.startsWith(".")) })
            cc = cc.sortedBy { a -> a.name.toLowerCase() }.toTypedArray()
            mfiles!!.lfiles.addAll(cc)
            if (selectFile != null) {
                val sidx = cc.indexOfFirst { c -> c.path == selectFile.path }
                if (sidx > -1) {
                    val mi = mfiles!!.getIndex(sidx, 0)
                    tvfiles.select(mi)
                    tvfiles.scrollTo(mi)
                }
            }
            dirWatcher.watchOneDir(fdir, { println("watch: loaddir!"); doUI(app, { loadDir(selectFile) }) })
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
                tvfiles.selectedIndexes.reversed().forEach { mi ->
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
    private val cplayer = CPlayer(this)
    private val cplaylist = CPlaylist(this)
    private val cfiles = CFiles(this)
    private val tInfo = WText("info")

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