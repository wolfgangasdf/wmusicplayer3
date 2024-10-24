import azadev.kotlin.css.*
import azadev.kotlin.css.dimens.em
import azadev.kotlin.css.dimens.percent
import azadev.kotlin.css.dimens.px
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import mu.KotlinLogging
import java.io.File
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


private val logger = KotlinLogging.logger {}

class KotlinxHtmlServlet : HttpServlet() {
    private val colBackground = "#B0B0B0"
    private val homescreenName = "WMP Mobile"
    private val submitreloadtimeout = 500 // TODO remove all stuff since this is useless, submit reloads page???

    override fun doGet(request: HttpServletRequest?, response: HttpServletResponse?) {
        val playlab = if (MusicPlayer.dogetPlaying()) "pause" else "play"
        val volume = "%02d".format(MusicPlayer.pVolume.value)
        val currentsong = MusicPlayer.pCurrentSong.value
        val currentfile = MusicPlayer.pCurrentFile.value
        fun getReloadJS(timeout: Int) = "setTimeout(function() { if (navigator.onLine) { window.location.reload(); } }, $timeout)"

        // respond to form submit
        if (request != null) {
            val p = request.getParameter("action")
            if (p != null) {
                operator fun Regex.contains(text: CharSequence): Boolean = this.matches(text)
                when(p) {
                    "play","pause" -> MusicPlayer.dotoggle()
                    "vol+" -> MusicPlayer.incDecVolume(up = true)
                    "vol-" -> MusicPlayer.incDecVolume(up = false)
                    "prev" -> MusicPlayer.playPrevious()
                    "next" -> MusicPlayer.playNext()
                    "refresh" -> {  }
                    else -> {
                        logger.warn("unknown action $p")
                    }
                }
            }
            val quickpls = request.parameterNames.toList().find { pn -> pn.startsWith("pls-") }
            if (quickpls != null) {
                MusicPlayer.loadPlaylist(File(Settings.bQuickPls[quickpls.replace("pls-", "").toInt()]), true)
                MusicPlayer.playFirst()
            }
        }

        val css = Stylesheet {
            body {
                backgroundColor = colBackground
                color = "#000"
                fontFamily = "Arial"
                lineHeight = 120.percent
                fontSize = 18.px
            }
            ".button" {
                boxShadow = "rgba(0,0,0,0.2) 0 1px 0 0"
                color = "#333"
                backgroundColor = "#FA2"
                borderRadius = 7.px
                border = NONE
                fontFamily = "Arial,sans-serif"
                fontSize = 20.px
                fontWeight = 700
                height = 55.px
                padding = "2px 8px"
            }
            ".b2" {
                backgroundColor = "#DAA520"
            }
            ".qpls" {
                lineHeight = 61.px
            }
            ".currentsong" {
                height = 1.2.em
                maxWidth = "30rem"
                lineHeight = 1.2
                overflow = AUTO
            }
            ".currentfile" {
                fontSize = SMALL
                height = 2.4.em
                maxWidth = "30rem"
                lineHeight = 1.2
                overflow = AUTO
            }
        }
        response!!.contentType = "text/html"
        response.writer.appendHTML(true).html {
            head {
                title = homescreenName
                style {
                    unsafe {
                        raw(css.render())
                    }
                }
                link(rel = "apple-touch-icon", href="/res/apple-touch-icon.png")
                link(rel = "icon", type = "image/png", href="/res/apple-touch-icon.png")
                link(rel = "manifest", href="/res/webmanifest.json")
                meta("viewport", metaViewport)
                meta("theme-color", colBackground) // android
                meta("application-name", homescreenName) // android
                meta("apple-mobile-web-app-capable", "yes")
                meta("apple-mobile-web-app-status-bar-style", "black")
                meta("apple-mobile-web-app-title", homescreenName)
            }
            body {
//                onLoad = getReloadJS(reloadtimeout)
                h1 { +"wmusicplayer" }
                form(action = mobileurl, method = FormMethod.get) {
                    onSubmit = getReloadJS(submitreloadtimeout) // browser reload without Post/Redirect/Get
                    target = "myiframe" // to avoid redirect at post, but uses deprecated "target".
                    p {
                        input(name="action", type=InputType.submit, classes = "button" ) { value="prev" }
                        +" "
                        input(name="action", type=InputType.submit, classes = "button") { value=playlab }
                        +" "
                        input(name="action", type=InputType.submit, classes = "button") { value="next" }
                    }
                    p {
                        input(name="action", type=InputType.submit, classes = "button" ) { value="vol-" }
                        +" $volume "
                        input(name="action", type=InputType.submit, classes = "button" ) { value="vol+" }
                        +" "
                        input(name="action", type=InputType.submit, classes = "button" ) { value="refresh" }
                    }
                    div("currentsong") { +currentsong }
                    br
                    div("currentfile") { +currentfile }
                    br
                    div("qpls") {
                        for (i in 0 until Constants.NQUICKPLS) {
                            input(name="pls-$i", type=InputType.submit, classes = "button" ) { value=Settings.getplscap(i) }
                            +" "
                        }
                    }
                    br
                    div("links") {
                        a("/") {
                            + "full player"
                        }
                    }
                }
                iframe {
                    name = "myiframe"
                    style = "display:none;"
                }
            }
        }
    }
}