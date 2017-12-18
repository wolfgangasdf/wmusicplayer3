import azadev.kotlin.css.*
import azadev.kotlin.css.dimens.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import mu.KotlinLogging
import java.io.File
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


private val logger = KotlinLogging.logger {}

class KotlinxHtmlServlet : HttpServlet() {

    override fun doGet(request: HttpServletRequest?, response: HttpServletResponse?) {

        val playlab = if (MusicPlayer.dogetPlaying()) "pause" else "play"
        val volume = "%02d".format(MusicPlayer.pVolume)
        val currentsong = MusicPlayer.pCurrentSong
        val currentfile = MusicPlayer.pCurrentFile
        fun getplscap(ii: Int): String {
            return if (Settings.bQuickPls[ii] == "")
                "none"
            else {
                val f = File(Settings.bQuickPls[ii]).name.replace(".pls","")
                val res = ii.toString() + ": " + f.substring(0, listOf(5,f.length).min()!!)
                res
            }
        }





        val css = Stylesheet {
            body {
                backgroundColor = "#B0B0B0"
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
                textShadow = "#FE6 0 1px 0"
            }

            ".b2" {
                backgroundColor = "#DAA520"
            }
        }
        logger.debug("CSS = " + css.render())

        response!!.contentType = "text/html"

        response.writer.appendHTML(true).html {
            head {
                title = "WMP Mobile"
                style {
                    unsafe {
                        raw(css.render())
                    }
                }
                meta("viewport", "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0")
            }
            body {
                form(action = "/mobile", method = FormMethod.post) {
                    p {
                        h1 { +"WMusicPlayer" }
                        input(name="action", type=InputType.submit, classes = "button" ) { value="prev" }
                        +" "
                        input(name="action", type=InputType.submit, classes = "button") { value=playlab }
                        +" "
                        input(name="action", type=InputType.submit, classes = "button") { value="next" }
                    }
                    p {
                        input(name="action", type=InputType.submit, classes = "button" ) { value="vol-" }
                        +" "
                        +volume
                        +" "
                        input(name="action", type=InputType.submit, classes = "button" ) { value="vol+" }
                        +" "
                        input(name="action", type=InputType.submit, classes = "button" ) { value="refresh" }
                    }
                    +currentsong
                    br
                    div { style = "font-size:small"
                        +currentfile
                    }
                    br
                    for (i in 0 until Constants.NQUICKPLS) {
                        input(name="action", type=InputType.submit, classes = "button" ) { value=getplscap(i) }
                        +" "
                    }

                }
            }
        }
    }

    override fun doPost(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req != null) {
            val p = req.getParameter("action")
            if (p != null) {
                val plpatt = "([0-9]): (.*)".toRegex()
                operator fun Regex.contains(text: CharSequence): Boolean = this.matches(text)
                when(p) {
                    "play","pause" -> MusicPlayer.dotoggle()
                    "vol+" -> MusicPlayer.incDecVolume(up = true)
                    "vol-" -> MusicPlayer.incDecVolume(up = false)
                    "prev" -> MusicPlayer.playPrevious()
                    "next" -> MusicPlayer.playNext()
                    "refresh" -> {  }
                    else -> {
                        if (plpatt.matches(p)) {
                            val i = plpatt.matchEntire(p)!!.groupValues[1].toInt()
                            MusicPlayer.loadPlaylist(Settings.bQuickPls[i])
                            MusicPlayer.playFirst()
                        } else {
                            logger.warn("unknown action " + p)
                        }
                    }
                }
            }
            resp!!.sendRedirect("/mobile") // reload
        }
    }

}