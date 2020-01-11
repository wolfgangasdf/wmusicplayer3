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
import kotlin.concurrent.timerTask
import kotlin.math.floor


private val logger = KotlinLogging.logger {}

enum class UIMode {
    UINORMAL, UIMINI, UISINGLECOL
}

private fun getMinutes(secs: Int): String {
    val min = floor(1.0/60*secs).toInt()
    return if (secs >= 0) "%01d:%02d".format(min, secs-60*min) else "--"
}

class SettingsWindow : WDialog("Settings") {
    private val layout = WVBoxLayout()
    private val mixers = MusicPlayer.getMixers()
    private val sbmixer = kJwtGeneric({ WSelectionBox() }) {
        for (mix in mixers) addItem(mix)
        if (mixers.contains(Settings.mixer)) currentIndex = mixers.indexOf(Settings.mixer)
        setMargin(WLength(10.0), EnumSet.of(Side.Right))
    }
    private val lePort = kJwtGeneric({ WLineEdit()}) {
        text = Settings.port.toString()
    }
    init {
        isModal = true
        contents.layout = layout
        footer.addWidget(KWPushButton("OK", "Save settings") {
            Settings.mixer = sbmixer.currentText.toString()
            Settings.port = lePort.text.toString().toInt()
            Settings.save()
            MusicPlayer.updateMixer()
            accept()
        })
        footer.addWidget(KWPushButton("Cancel", "") { reject() })

        layout.addWidget(kJwtVBox(contents) {
            addit(WLabel("Audio mixers:"))
            addit(sbmixer)
        })
        layout.addWidget(KWPushButton("Reset playlist folder", "set again afterwards!") { Settings.playlistFolder = "" })
        layout.addWidget(kJwtHBox(contents) {
            addit(WLabel("Port (requires restart):"))
            addit(lePort)
        })
    }
}

class AddURLWindow : WDialog("Add stream URL...") {
    private val layout = WVBoxLayout()
    private val leTitle = kJwtGeneric({ WLineEdit() })
    private val leUrl = kJwtGeneric({ WLineEdit() }) {
        text = "http://"
    }
    init {
        isModal = true
        contents.layout = layout
        footer.addWidget(KWPushButton("OK", "Add URL to playlist") {
            if (leUrl.text.startsWith("http")) {
                MusicPlayer.addToPlaylist(leUrl.text, null, false, title = leTitle.text)
            }
            accept()
        })
        footer.addWidget(KWPushButton("Cancel", "") { reject() })
        layout.addWidget(WLabel("URL of stream:"))
        layout.addWidget(leUrl)
        layout.addWidget(WLabel("Title:"))
        layout.addWidget(leTitle)
    }
}

class EditPLSentry(index: Int?) : WDialog("Edit playlist entry...") {
    private val layout = WVBoxLayout()
    private val leURI = kJwtGeneric({ WLineEdit() })
    private val leTitle = kJwtGeneric({ WLineEdit() })
    init {
        isModal = true
        contents.layout = layout
        if (index != null) {
            val pli = MusicPlayer.cPlaylist[index]
            leURI.text = pli.name
            leTitle.text = pli.title
            footer.addWidget(KWPushButton("Update", "") {
                pli.name = leURI.text
                pli.title = leTitle.text
                MusicPlayer.cPlaylist[index] = pli
                accept()
            })
        }
        width = WLength(350.0)
        footer.addWidget(KWPushButton("Add entry", "") {
            MusicPlayer.addToPlaylist(leURI.text, title = leTitle.text, clearPlayListIfPls = false)
            accept()
        })
        footer.addWidget(KWPushButton("Cancel", "") { reject() })
        layout.addWidget(WLabel("URI (file:///... or http://....):"))
        layout.addWidget(leURI, 1)
        layout.addWidget(WLabel("Title:"))
        layout.addWidget(leTitle, 1)
    }
}


class ModelPlaylist(private val app: WApplication) : WAbstractTableModel() {

    override fun getRowCount(parent: WModelIndex?): Int = if (parent == null) { MusicPlayer.cPlaylist.size } else 0
    override fun getData(index: WModelIndex?, role: ItemDataRole?): Any? {
        if (index?.row == -1) return null
        return when (role) {
            ItemDataRole.Display -> WString(
                    when(index?.column) {
                        1 -> MusicPlayer.cPlaylist[index.row].title
                        2 -> getMinutes(MusicPlayer.cPlaylist[index.row].length)
                        else -> ""
                    }
            )
            else -> null
        }
    }

    //bug: can't scroll first column bug: add thin column before!
    override fun getColumnCount(parent: WModelIndex?): Int = if (parent == null) 3 else 0

    // resetting the model to reload the playlist must not happen with high rate.
    private var tt: TimerTask? = null
    private fun resetModelDelayed() {
        tt?.cancel()
        tt = timerTask {
            MusicPlayer.updateCurrentPlaylistItem() // not nice, should be inside MP
            doUI(app) { modelReset().trigger() }
            logger.debug("resetting modelplaylist done! ${Thread.currentThread().id}")
        }
        Timer().schedule(tt, 500)
    }

    init {
        logger.debug("initialize ModelPlaylist...: " + WApplication.getInstance().sessionId + " threadid: " + Thread.currentThread().id)
        MusicPlayer.cPlaylist.addListener(ListChangeListener { resetModelDelayed() })
    }

    // TODO unused ugly hack to update style of cell...
    fun updateRow(row: Int) {
        for (c in 0..getColumnCount(null))
            setData(row, c, getData(row, c))
    }

    override fun dropEvent(e: WDropEvent?, action: DropAction?, row: Int, column: Int, parent: WModelIndex?) {
        logger.debug("drop: $action $row $column")
        super.dropEvent(e, action, row, column, parent)
    }
}


class ModelFiles(parent: WObject) : WAbstractTableModel() { // TODO link to parent?

    val lfiles = FXCollections.observableArrayList<File>()!!

    override fun getRowCount(parent: WModelIndex?): Int = if (parent == null) { lfiles.size } else 0
    override fun getData(index: WModelIndex?, role: ItemDataRole?): Any? {
        return when (role) {
            ItemDataRole.Display -> WString(if (index?.column == 1) lfiles[index.row].name else "")
            else -> null
        }
    }

    //bug: can't scroll first column bug: add thin column before!
    override fun getColumnCount(parent: WModelIndex?): Int = if (parent == null) 2 else 0

    init {
        @Suppress("RedundantLambdaArrow")
        lfiles.addListener { _: Observable ->
            modelReset().trigger()
        }
    }

}

class CPlayer(app: JwtApplication, isMobile: Boolean) : WContainerWidget() {
    private val lplayer = WVBoxLayout()
    private val btplay = KWPushButton("►", "Toggle play/pause") { MusicPlayer.dotoggle() }
    private val slider = kJwtGeneric( { WSlider() }, {
        isNativeControl = true // doesn't work if not!
        tickPosition = WSlider.NoTicks
        height = btplay.height
        valueChanged().addListener(this) { i ->
            MusicPlayer.skipTo(i.toDouble())
        }
    })
    private val volume = WText("100")
    private val timecurr = WText("1:00")
    private val timelen = WText("1:05:23")
    private val songinfo1 = WText("songi1")
    private val songinfo2 = WText("songi2")
    private val codecInfo = WText("codecinfo")
    private val quickbtns = Array(Constants.NQUICKPLS) { i -> KWPushButton(Settings.getplscap(i), "Load Playlist $i") {
        if (bquickedit.text.value == "✏️") {
            MusicPlayer.loadPlaylist(Settings.bQuickPls[i], true)
            MusicPlayer.playFirst()
        } else {
            Settings.bQuickPls[i] = MusicPlayer.pPlaylistFile.value
            setText(Settings.getplscap(i))
            Settings.save()
            bquickedit.setText("✏️")
        }
    }
    }
    private val bquickedit = KWPushButton("✏️", "Click to assign quick playlist button") {
        setText(if (text.value == "✏️") "✏️..." else "✏️")
    }

    init {
        this.layout = lplayer
        // TODO if (!isMobile) width = WLength(500.0)
        btplay.width = WLength("14%")
        songinfo1.height = WLength(32.0)
        songinfo1.decorationStyle.font.weight = FontWeight.Bold
        songinfo2.height = WLength(30.0)
        codecInfo.height = WLength(10.0)
//        codecInfo.styleClass = "codecinfo"
        codecInfo.decorationStyle.font.size = FontSize.Smaller
        lplayer.addWidget(kJwtHBox(this){
            addit(KWPushButton("⇠", "Previous song") { MusicPlayer.playPrevious() })
            addit(btplay)
            addit(KWPushButton("⇥", "Next song") { MusicPlayer.playNext() })
            addit(KWPushButton("⇠", "Skip -10s") { MusicPlayer.skipRel(-10.0) })
            addit(slider)
            addit(KWPushButton("⇢", "Skip +10s") { MusicPlayer.skipRel(+10.0) })
            addit(KWPushButton("V-", "Volume down") { MusicPlayer.incDecVolume(false) })
            addit(volume)
            addit(KWPushButton("V+", "Volume up") { MusicPlayer.incDecVolume(true) })
        })

        lplayer.addWidget(kJwtHBox(this){
            addit(timecurr, 0, EnumSet.of(AlignmentFlag.Bottom))
            addit(WText("/"), 0, EnumSet.of(AlignmentFlag.Bottom))
            addit(timelen, 0, EnumSet.of(AlignmentFlag.Bottom))
            addit(songinfo1, 1, EnumSet.of(AlignmentFlag.Bottom))
        })
        lplayer.addWidget(songinfo2)

        lplayer.addWidget(kJwtHBox(this){
            quickbtns.forEach { qb -> addit(qb) }
            addit(bquickedit)
        })
        lplayer.addWidget(codecInfo)

        bindprop2widget(app, MusicPlayer.pCurrentSong) { _, newv -> songinfo1.setText(newv) }
        bindprop2widget(app, MusicPlayer.pCurrentFile) { _, newv -> songinfo2.setText(newv) }
        bindprop2widget(app, MusicPlayer.pCodecInfo) { _, newv -> codecInfo.setText(newv) }
        bindprop2widget(app, MusicPlayer.pTimePos) { _, newv ->
            timecurr.setText(getMinutes(newv.toInt()))
            slider.value = newv.toInt()
        }
        bindprop2widget(app, MusicPlayer.pTimeLen) { _, newv ->
            timelen.setText(getMinutes(newv.toInt()))
            if (slider.maximum != MusicPlayer.pTimeLen.intValue()) slider.maximum = MusicPlayer.pTimeLen.intValue()
        }
        bindprop2widget(app, MusicPlayer.pVolume) { _, newv -> volume.setText(newv.toString()) }
        bindprop2widget(app, MusicPlayer.pIsPlaying) { _, newv -> btplay.setText(if (newv) "❙❙" else "►") }
    }
}

class CPlaylist(app: JwtApplication, isMobile: Boolean) : WContainerWidget() {
    private val lplaylist = WVBoxLayout()
    private var mplaylist: ModelPlaylist? = null
    private val plname = WLineEdit("plname")

    private val tvplaylist = kJwtGeneric({ KWTableView() }) {
        mplaylist = ModelPlaylist(app)
        model = mplaylist
        isSortingEnabled = false
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)
        positionScheme = PositionScheme.Relative
        onLayoutSizeChanged = { w, _ ->
            setColumnWidth(0, WLength(0.0))
            setColumnWidth(1, WLength(w - model.getColumnCount(null)*7.0 - 50))
            setColumnWidth(2, WLength(50.0))
        }
        headerHeight = WLength(0.0)
        selectionMode = SelectionMode.Extended
        selectionBehavior = SelectionBehavior.Rows
        editTriggers = EnumSet.of(EditTrigger.None)
        resize(WLength(2000.0), WLength(2000.0)) // hack to make both lists equal width
        doubleClicked().addListener(this) { mi, _ ->
            if (mi != null) {
                MusicPlayer.dosetCurrentPlaylistIdx(mi.row)
                MusicPlayer.playSong()
            }
        }
        class PlsItemDelegate(parent: WObject): WItemDelegate() { // TODO link to parent?
            override fun update(widget: WWidget?, index: WModelIndex?, flags: EnumSet<ViewItemRenderFlag>?): WWidget? {
                val wid = super.update(widget, index, flags)
                if (wid is IndexText) {
                    println("UUUUUUU: row=${wid.index.row} (<> ${MusicPlayer.pCurrentPlaylistIdx.value}")
                    wid.toggleStyleClass("Wt-valid", (wid.index.row == MusicPlayer.pCurrentPlaylistIdx.value))
                    // TODO work but requires reload page...
                }
                return wid
            }
        }
        itemDelegate = PlsItemDelegate(this)
    }

    private fun updateRow(row: Int) {
        println("updaterow $row: ")
        if (row >= mplaylist!!.getRowCount(null)) return
        for (c in 0..mplaylist!!.getColumnCount(null)) {
            val mi = mplaylist!!.getIndex(row, c)
            tvplaylist.itemDelegate.update(tvplaylist.itemWidget(mi), mi, EnumSet.noneOf(ViewItemRenderFlag::class.java))
        }
    }

    fun scrollToCurrent() {
        //mplaylist!!.modelReset().trigger() // need to refresh first because of delayed auto refresh
        val mi = mplaylist!!.getIndex(MusicPlayer.pCurrentPlaylistIdx.value, 0)
        tvplaylist.scrollTo(mi)
    }

    init {
        this.layout = lplaylist
        lplaylist.addWidget(kJwtHBox(this){
            addit(plname, 1)
            addit(KWPushButton("save", "Save current playlist in playlist folder") {
                if (Settings.playlistFolder == "") {
                    WMessageBox.show("Info", "<p>You need to assign a playlist folder first!</p>", EnumSet.of(StandardButton.Ok))
                } else {
                    MusicPlayer.savePlaylist(plname.text)
                    logger.info("saved playlist") // make floating thing.
                }
            })
            addit(KWPushButton("✖✖", "Clear playlist") {
                MusicPlayer.cPlaylist.clear()
            })
            addit(KWPushButton("✖", "Remove selected songs from playlist") {
                tvplaylist.selectedIndexes.map { mi -> mi.row }.reversed().forEach { i ->  MusicPlayer.cPlaylist.removeAt(i) }
            })
            addit(KWPushButton("edit", "Edit selected entry or add new...") {
                val i = tvplaylist.selectedIndexes.firstOrNull()?.row
                EditPLSentry(i).show()
            })
            addit(KWPushButton("+URL", "Add stream URL to playlist...") {
                AddURLWindow().show()
            })
        })
        lplaylist.addWidget(tvplaylist, 1)
        bindprop2widget(app, MusicPlayer.pPlaylistName) { _, newv -> plname.text = newv }
        bindprop2widget(app, MusicPlayer.pCurrentPlaylistIdx) { oldv, newv ->
            println("XXXXXXXXXX $oldv $newv")
            if (oldv != null) if (oldv.toInt() > -1) updateRow(oldv.toInt())
            if (newv.toInt() > -1) updateRow(newv.toInt())
        }
    }
}

class CFiles(private val app: JwtApplication) : WContainerWidget() {
    private val lfiles = WVBoxLayout()
    private var mfiles: ModelFiles? = null
    private val currentfolder = WText("currentfolder")

    private val tvfiles = kJwtGeneric({ KWTableView() }) {
        mfiles = ModelFiles(this)
        model = mfiles
        isSortingEnabled = false
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)
        onLayoutSizeChanged = { w, _ ->
            setColumnWidth(0, WLength(0.0))
            setColumnWidth(1, WLength(w - model.getColumnCount(null)*7.0))
        }
        headerHeight = WLength(0.0)
        selectionMode = SelectionMode.Extended
        selectionBehavior = SelectionBehavior.Rows
        editTriggers = EnumSet.of(EditTrigger.None)
        resize(WLength(2000.0), WLength(2000.0)) // hack to make both lists equal width
        doubleClicked().addListener(this) { mi, _ ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (!f.isDirectory) addFileToPlaylist(f)
            }
        }
        clicked().addListener(this) { mi, _ ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (f.isDirectory) changeDir(f.path)
            }
        }
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
                watcher = path.watch()
                try {
                    // don't loop because callback will start another thread before tak() started again... while (true) {
                    val key = watcher!!.take() // blocks
                    if (key != null) {
                        key.reset()
                        callback()
                    }
                } catch (e: ClosedWatchServiceException) {
                }
            }
        }
    }

    private fun listMusicfilesDirs(fdir: File, includePls: Boolean): List<File> {
        var cc = fdir.listFiles { file ->
            (if (includePls) Constants.soundFilePls else Constants.soundFile).matches(file.name) || (file.isDirectory && !file.name.startsWith("."))
        }
        cc = cc.sortedBy { a -> a.name.toLowerCase() }.toTypedArray()
        return cc.toList()
    }

    private fun recursiveListMusicFiles(f: File): List<File> {
        val these = listMusicfilesDirs(f, false).toList()
        return these.plus(these.filter { ff -> ff.isDirectory }.flatMap { fff -> recursiveListMusicFiles(fff).toList() })
    }

    private val dirWatcher = DirWatcher()
    private fun loadDir(selectFile: File? = null) {
        mfiles!!.lfiles.clear()
        val fdir = File(Settings.pCurrentFolder)
        if (fdir.exists()) {
            currentfolder.setText(Settings.pCurrentFolder)
            val cc = listMusicfilesDirs(fdir, true)
            mfiles!!.lfiles.addAll(cc)
            if (selectFile != null) {
                val sidx = cc.indexOfFirst { c -> c.path == selectFile.path }
                if (sidx > -1) {
                    val mi = mfiles!!.getIndex(sidx, 0)
                    tvfiles.select(mi)
                    tvfiles.scrollTo(mi)
                }
            }
            dirWatcher.watchOneDir(fdir) { doUI(app) { loadDir(selectFile) } }
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
            MusicPlayer.addToPlaylist("file://" + f.path, null, clearPlayListIfPls = false)
        }
    }

    init {
        this.layout = lfiles
        lfiles.addWidget(currentfolder)
        lfiles.addWidget(kJwtHBox(this){
            addit(KWPushButton("+", "Add current file to playlist") {
                tvfiles.selectedIndexes.forEach { mi -> addFileToPlaylist(mfiles!!.lfiles[mi.row])}
            })
            addit(KWPushButton("++", "Add all files in current folder to playlist") {
                mfiles!!.lfiles.forEach { f -> addFileToPlaylist(f) }
            })
            addit(KWPushButton("+++", "Add all files in current folder recursively to playlist") {
                recursiveListMusicFiles(File(currentfolder.text.toString())).forEach { f ->  addFileToPlaylist(f) }
            })
            addit(KWPushButton("⇧", "Go to parent folder") {
                val newcf = File(Settings.pCurrentFolder).parent
                if (newcf != null) changeDir(newcf, File(Settings.pCurrentFolder))
            })
            addit(KWPushButton("⇦", "Go to previous folder") {
                logger.debug("recent dirs ne=${Settings.recentDirs.isNotEmpty()} :" + Settings.recentDirs.joinToString { s -> s })
                if (Settings.recentDirs.isNotEmpty()) changeDir(Settings.recentDirs.pop(), dontStore = true)
            })
            addit(KWPushButton("pls", "Go to playlist folder (or set if empty)") {
                if (Settings.playlistFolder == "") {
                    if (WMessageBox.show("Warning", "<p>Set playlist folder to current folder?</p>", EnumSet.of(StandardButton.Ok, StandardButton.Cancel))
                            == StandardButton.Ok) {
                        Settings.playlistFolder = currentfolder.text.toString()
                        Settings.save()
                    }
                } else changeDir(Settings.playlistFolder)
            })
            addit(KWPushButton("✖", "Delete selected playlist file") {
                tvfiles.selectedIndexes.reversed().forEach { mi ->
                    val f = mfiles!!.lfiles[mi.row]
                    if (f.name.endsWith(".pls")) {
                        f.delete()
                        logger.info("deleted ${f.path}")
                    }
                    loadDir()
                }
            })
            addit(KWPushButton("S", "Settings") {
                SettingsWindow().show()
            })
            addit(KWPushButton("?", "Help") {
                logger.debug("help")
            })
        })
        lfiles.addWidget(tvfiles, 1)
        loadDir()
    }

}

class JwtApplication(env: WEnvironment, isMobile: Boolean) : WApplication(env) {
    private val cplayer = CPlayer(this, isMobile)
    private val cplaylist = CPlaylist(this, isMobile)
    private val cfiles = CFiles(this)
    private val tInfo = WText("info")

    override fun quit() {
        logger.debug("quit application thread=${Thread.currentThread().id} agent=${environment.agent}")
        super.quit()
    }

    init {
        logger.info("initialize Application sid=${getInstance().sessionId} thread=${Thread.currentThread().id} agent=${env.agent}")

        setTitle("WMusicPlayer")

        setCssTheme("polished")

        useStyleSheet(WLink("style/styles.css"))

        // for WText
        styleSheet.addRule("body", "font-family: verdana, helvetica, tahoma, sans-serif; font-size: 13px;")

        val lmain = WVBoxLayout()
        root.layout = lmain
        lmain.addWidget(cplayer, 0, AlignmentFlag.Left)
        if (!isMobile) {
            lmain.addWidget(kJwtHBox(root) {
                addit(cplaylist, 1)
                addit(cfiles, 1)
            }, 1)
        } else {
            lmain.addWidget(kJwtGeneric({WTabWidget(root)}) {
                // TODO fonts much bigger, ...
                addTab(cplaylist, "Playlist", ContentLoading.Eager) // TODO WTabWidget.LoadPolicy.PreLoading)
                addTab(cfiles, "Files", ContentLoading.Eager) // TODO WTabWidget.LoadPolicy.PreLoading)
                this.styleClass = "tabwidget"
            })
        }
        lmain.addWidget(kJwtHBox(root) {
            addit(KWPushButton("debug", "...") {
                MusicPlayer.dogetMediaInfo()
                //logger.debug("debug playlist=" + MusicPlayer.cPlaylist.joinToString { pli -> pli.name })
            }, 0)
            addit(tInfo, 1)
        }, 0)

        enableUpdates()

        logger.info("Application initialized!")
        logger.debug("env: ${env.hasAjax()} ${env.hasJavaScript()} ${env.hasWebGL()}")

        // init stuff after shown
        val app = this
        Timer().schedule(timerTask { // ugly hack, but I can't find another way.
            logger.debug("resetting modelplaylist... ${Thread.currentThread().id}")
            doUI(app) { cplaylist.scrollToCurrent() }
            logger.debug("resetting modelplaylist done! ${Thread.currentThread().id}")
        }, 1000)
    }
}

class JwtServlet : WtServlet() {

    override fun createApplication(env: WEnvironment): WApplication {
        logger.debug("env: ${env.dpiScale} ${env.parameterMap} w ${env.screenWidth}")
        val ismobile = false // TODO env.screenWidth <= 500
        return JwtApplication(env, ismobile)
    }

    companion object {
        private const val serialVersionUID = 1L
    }

    init {
        logger.info("servlet init...")
//        configuration.setProgressiveBootstrap(true) // should I?
        configuration.favicon = "/favicon.ico" // TODO nothing works, hardcoded paths in jwt...


        super.init()
        logger.info("servlet config: pbs:${configuration.progressiveBootstrap("/")} ua:${configuration.uaCompatible}")
        logger.info("servlet config: ${configuration.metaHeaders} x ${configuration.properties} y ${configuration.internalDeploymentSize()}")

        // Enable websockets only if the servlet container has support for JSR-356 (Jetty 9, Tomcat 7, ...)
        // configuration.setWebSocketsEnabled(true);
        logger.info("servlet initialized!")
    }
}