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
                        0 -> "►"
                        1 -> MusicPlayer.cPlaylist[index.row].title
                        2 -> getMinutes(MusicPlayer.cPlaylist[index.row].length)
                        else -> ""
                    }
            )
            else -> null
        }
    }

    override fun getColumnCount(parent: WModelIndex?): Int = if (parent == null) 3 else 0

    // resetting the model to reload the playlist must not happen with high rate.
    private var tt: TimerTask? = null
    private fun resetModelDelayed() {
        tt?.cancel()
        tt = timerTask {
            MusicPlayer.updateCurrentPlaylistItem() // not nice, should be inside MP
            doUI(app) { modelReset().trigger() }
            logger.debug("resetting modelplaylist done! ${Thread.currentThread().threadId()}")
        }
        Timer().schedule(tt, 500)
    }

    init {
        logger.debug("initialize ModelPlaylist...: " + WApplication.getInstance().sessionId + " threadid: " + Thread.currentThread().threadId())
        MusicPlayer.cPlaylist.addListener(ListChangeListener { resetModelDelayed() })
    }

    override fun dropEvent(e: WDropEvent?, action: DropAction?, row: Int, column: Int, parent: WModelIndex?) {
        logger.debug("drop: $action $row $column")
        super.dropEvent(e, action, row, column, parent)
    }
}


class ModelFiles : WAbstractTableModel() {

    val lfiles = FXCollections.observableArrayList<File>()!!

    override fun getRowCount(parent: WModelIndex?): Int = if (parent == null) { lfiles.size } else 0
    override fun getData(index: WModelIndex?, role: ItemDataRole?): Any? {
        return when (role) {
            ItemDataRole.Display -> WString(if (index?.column == 0) lfiles[index.row].name else "")
            else -> null
        }
    }

    override fun getColumnCount(parent: WModelIndex?): Int = if (parent == null) 1 else 0

    init {
        lfiles.addListener { _: Observable ->
            modelReset().trigger()
        }
    }
}

class CPlayer(app: JwtApplication) : WContainerWidget() {
    private val lplayer = WVBoxLayout()
    private val btplay = KWPushButton("►", "Toggle play/pause") { MusicPlayer.dotoggle() }
    private var sliderGotValue = false
    private val slider = kJwtGeneric( { WSlider() }, {
        isNativeControl = false // otherwise update loop
        tickPosition = WSlider.NoTicks
        height = btplay.height
        width = WLength("10%")
        valueChanged().addListener(this) { i ->
            if (sliderGotValue) { // otherwise skips on reload
                MusicPlayer.skipTo(i.toDouble())
            }
        }
    })
    private val volume = WText("100", TextFormat.Plain).apply { id = "volume" }
    private val timecurr = WText("1:00", TextFormat.Plain).apply { id = "timecurr" }
    private val timelen = WText("1:05:23", TextFormat.Plain).apply { id = "timelen" }
    private val songinfo1 = WText("songi1", TextFormat.Plain).apply { id = "songinfo1" }
    private val songinfo2 = WText("songi2", TextFormat.Plain).apply { id = "songinfo2" }
    private val codecInfo = WText("codecinfo", TextFormat.Plain).apply { id = "codecInfo" }
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
        lplayer.setContentsMargins(0, 0, 0, 0)
        this.layout = lplayer
        width = WLength(500.0) // otherwise too narrow if mobile
        btplay.width = WLength("7%")
        songinfo1.height = WLength(32.0)
        songinfo1.decorationStyle.font.weight = FontWeight.Bold
        songinfo2.height = WLength(30.0)
        codecInfo.height = WLength(15.0)
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
            addit(kJwtHBox(this) {
                addit(timecurr, 0)
                addit(WText("/", TextFormat.Plain), 0)
                addit(timelen, 0)
            }, 0)
            addit(songinfo1, 1)
        })
        lplayer.addWidget(songinfo2)
        lplayer.addWidget(kJwtHBox(this){
            quickbtns.forEach { qb -> addit(qb) }
            addit(bquickedit)
        })
        lplayer.addWidget(codecInfo)

        bindprop2widget(app, MusicPlayer.pCurrentSong) { _, newv -> songinfo1.setText(WString(newv)) }
        bindprop2widget(app, MusicPlayer.pCurrentFile) { _, newv -> songinfo2.setText(WString(newv)) }
        bindprop2widget(app, MusicPlayer.pCodecInfo) { _, newv -> codecInfo.setText(WString(newv)) }
        bindprop2widget(app, MusicPlayer.pTimePos) { _, newv ->
            timecurr.setText(getMinutes(newv.toInt()))
            slider.value = newv.toInt()
            sliderGotValue = true
        }
        bindprop2widget(app, MusicPlayer.pTimeLen) { _, newv ->
            timelen.setText(getMinutes(newv.toInt()))
            if (slider.maximum != MusicPlayer.pTimeLen.intValue()) slider.maximum = MusicPlayer.pTimeLen.intValue()
        }
        bindprop2widget(app, MusicPlayer.pVolume) { _, newv -> volume.setText(newv.toString()) }
        bindprop2widget(app, MusicPlayer.pIsPlaying) { _, newv -> btplay.setText(if (newv) "❙❙" else "►") }
    }
}

class CPlaylist(app: JwtApplication) : WContainerWidget() {
    private val lplaylist = WVBoxLayout().apply { setContentsMargins(0, 0, 0, 0) }
    private var mplaylist: ModelPlaylist? = null
    private val plname = WLineEdit("plname")

    inner class PlsItemDelegate: WItemDelegate() {
        override fun update(widget: WWidget?, index: WModelIndex?, flags: EnumSet<ViewItemRenderFlag>?): WWidget? {
            val wid = super.update(widget, index, flags)
            // println("uuuuuuu: wid=$wid ${wid?.javaClass} mi=$index col: ${index?.column}")
            if (wid is IndexText) {
                wid.toggleStyleClass("Wt-valid", (wid.index.row == MusicPlayer.pCurrentPlaylistIdx.value))
            }
            return wid
        }
    }

    private val tvplaylist = kJwtGeneric({ KWTableView() }) {
        mplaylist = ModelPlaylist(app)
        model = mplaylist
        isSortingEnabled = false
        isScrollVisibilityEnabled = true
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)
        onLayoutSizeChanged = { w, _ ->
            setColumnWidth(0, WLength(25.0))
            setColumnWidth(1, WLength(w - model.getColumnCount(null)*7.0 - 25 - 50))
            setColumnWidth(2, WLength(50.0))
        }
        headerHeight = WLength(0.0)
        selectionMode = if (app.isMobile) SelectionMode.Single else SelectionMode.Extended
        selectionBehavior = SelectionBehavior.Rows
        editTriggers = EnumSet.of(EditTrigger.None)
        height = WLength("10000") // bugfix, scrollbars don't appear otherwise (but tvfiles is ok)!

        clicked().addListener(this) { mi, _ ->
            if (mi != null && mi.column == 0) {
                MusicPlayer.dosetCurrentPlaylistIdx(mi.row)
                MusicPlayer.playSong()
            }
        }

        itemDelegate = PlsItemDelegate()
    }

    private fun updateRow(row: Int) {
        if (row >= mplaylist!!.getRowCount(null)) return
        for (c in 0..mplaylist!!.getColumnCount(null)) {
            val mi = mplaylist!!.getIndex(row, c)
            val w = tvplaylist.itemWidget(mi)
            w?.toggleStyleClass("Wt-valid", (row == MusicPlayer.pCurrentPlaylistIdx.value))
        }
    }

    fun scrollToCurrent() {
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
            if (oldv != null) if (oldv.toInt() > -1) updateRow(oldv.toInt())
            if (newv.toInt() > -1) updateRow(newv.toInt())
        }
    }
}

class CFiles(private val app: JwtApplication) : WContainerWidget() {
    private val lfiles = WVBoxLayout().apply { setContentsMargins(0, 0, 0, 0) }
    private var mfiles: ModelFiles? = null
    private val currentfolder = WText("currentfolder", TextFormat.Plain)

    private val tvfiles = kJwtGeneric({ KWTableView() }) {
        mfiles = ModelFiles()
        model = mfiles
        isSortingEnabled = false
        isScrollVisibilityEnabled = true
        setAlternatingRowColors(true)
        rowHeight = WLength(28.0)
        onLayoutSizeChanged = { w, _ ->
            setColumnWidth(0, WLength(w - model.getColumnCount(null)*7.0))
        }
        headerHeight = WLength(0.0)
        selectionMode = if (app.isMobile) SelectionMode.Single else SelectionMode.Extended
        selectionBehavior = SelectionBehavior.Rows
        editTriggers = EnumSet.of(EditTrigger.None)

        doubleClicked().addListener(this) { mi, _ ->
            if (mi != null) {
                val f = mfiles!!.lfiles[mi.row]
                if (!f.isDirectory) addFileToPlaylist(f, true)
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
                } catch (_: ClosedWatchServiceException) {
                    // forgot why this is ok
                }
            }
        }
    }

    private fun listMusicfilesDirs(fdir: File, includePls: Boolean): List<File> {
        var cc = fdir.listFiles { file ->
            (if (includePls) Constants.soundFilePls else Constants.soundFile).matches(file.name) || (file.isDirectory && !file.name.startsWith("."))
        }
        if (cc == null) cc = arrayOf<File>()
        cc = cc.sortedBy { a -> a.name.lowercase(Locale.getDefault()) }.toTypedArray()
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

    private fun addFileToPlaylist(f: File, clearPlayListIfPls: Boolean = false) {
        if (!f.isDirectory) {
            MusicPlayer.addToPlaylist("file://" + f.path, null, clearPlayListIfPls)
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
        })
        lfiles.addWidget(tvfiles, 1)
        loadDir()
    }

}

class JwtApplication(env: WEnvironment, val isMobile: Boolean) : WApplication(env) {
    private val cplayer = CPlayer(this).apply { id = "cplayer" }
    private val cplaylist = CPlaylist(this).apply { id = "cplaylist" }
    private val cfiles = CFiles(this).apply { id = "cfiles" }

    override fun quit() {
        logger.debug("quit application thread=${Thread.currentThread().threadId()} agent=${environment.agent}")
        super.quit()
    }

    init {
        logger.info("initialize Application sid=${getInstance().sessionId} thread=${Thread.currentThread().threadId()} agent=${env.agent}")

        setTitle("WMP") // also web app name

        setCssTheme("polished")

        styleSheet.addRule("body", "font-family: verdana, helvetica, tahoma, sans-serif; font-size: 14px;")
        styleSheet.addRule("#cPlayerScrollable", "-webkit-flex: 1 0 auto !important;") // TODO hack chrome desktop player height
        styleSheet.addRule("#cPlayerScrollable", "flex: 1 0 auto !important;") // TODO hack chrome desktop player height

        val cPlayerScrollable = WContainerWidget().apply {
            id = "cPlayerScrollable"
            setOverflow(Overflow.Auto, Orientation.Horizontal)
            setOverflow(Overflow.Auto, Orientation.Vertical) // bug, should be big enough.
            addWidget(cplayer)
        }

        val lBelow = WVBoxLayout().apply {
            setContentsMargins(0, 0, 0, 0)
        }
        val cBelow = WContainerWidget().apply {
            id = "cBelow"
            layout = lBelow
        }

        if (!isMobile) {
            lBelow.addWidget(kJwtHBox(cBelow) {
                setMargin(0)
                cplaylist.width = WLength("50%")
                cfiles.width = WLength("50%")
                addit(cplaylist, 1)
                addit(cfiles, 1)
            })
        } else {
            lBelow.addWidget(kJwtGeneric({WTabWidget(cBelow)}) {
                setMargin(0)
                addTab(cplaylist, "Playlist", ContentLoading.Eager)
                addTab(cfiles, "Files", ContentLoading.Eager)
                id = "idTabwidget"
            })
        }

        // needed to fill vertically and show scrollbars
        cplaylist.height = WLength("100%")
        cfiles.height = WLength("100%")

        val lroot = WVBoxLayout()
        if (isMobile) lroot.setContentsMargins(0, 0, 0, 0)
        lroot.addWidget(cPlayerScrollable)
        lroot.addWidget(cBelow)
        root.layout = lroot

        enableUpdates()

        logger.info("Application initialized!")
        logger.debug("env ajax=${env.hasAjax()} js=${env.hasJavaScript()} webgl=${env.hasWebGL()}")

        // init stuff after shown
        val app = this
        Timer().schedule(timerTask { // ugly hack, but I can't find another way.
            logger.debug("resetting modelplaylist... ${Thread.currentThread().threadId()}")
            doUI(app) { cplaylist.scrollToCurrent() }
            logger.debug("resetting modelplaylist done! ${Thread.currentThread().threadId()}")
        }, 1000)
    }
}

class JwtServlet : WtServlet() {

    override fun createApplication(env: WEnvironment): WApplication {
        logger.debug("env dpis=${env.dpiScale} parms=${env.parameterMap} w=${env.screenWidth} ip=${env.internalPath}")
        val ismobile = env.screenWidth <= 500
        return JwtApplication(env, ismobile)
    }

    companion object {
        private const val serialVersionUID = 1L
    }

    init {
        super.init()
        logger.info("jwt servlet init...")
        configuration.favicon = "/res/favicon.ico"

        // need to define meta headers here (config) if not progressive bootstrap: https://redmine.webtoolkit.eu/boards/1/topics/14326
        configuration.metaHeaders.add(MetaHeader(MetaHeaderType.Meta, "viewport", metaViewport, "", ""))
        configuration.metaHeaders.add(MetaHeader(MetaHeaderType.Meta, "mobile-web-app-capable", "yes", "", ""))

        logger.info("servlet config: pbs:${configuration.progressiveBootstrap("/")} ua:${configuration.uaCompatible}")
        logger.info("servlet config: ${configuration.metaHeaders} x ${configuration.properties} y ${configuration.internalDeploymentSize()}")
        logger.info("jwt servlet initialized!")
    }
}
