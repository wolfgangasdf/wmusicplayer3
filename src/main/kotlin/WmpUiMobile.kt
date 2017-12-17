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
        val volume = MusicPlayer.pVolume.toString(2) //formatted("%02d")
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

        response!!.contentType = "text/html"

        response.writer.appendHTML(true).html {
            head {
                title = "WMP Mobile"
                style {
                    unsafe {
                        raw("""
                            |body {
                            |    background-color:#B0B0B0;
                            |    color: #000;
                            |    font-family: Arial;
                            |    line-height: 120%;
                            |    font-size:18px;
                            |}
                            |.button {
                            |    -webkit-box-shadow:rgba(0,0,0,0.2) 0 1px 0 0;
                            |    -moz-box-shadow:rgba(0,0,0,0.2) 0 1px 0 0;
                            |    box-shadow:rgba(0,0,0,0.2) 0 1px 0 0;
                            |    color:#333;
                            |    background-color:#FA2;
                            |    border-radius:7px;
                            |    -moz-border-radius:7px;
                            |    -webkit-border-radius:7px;
                            |    border:none;
                            |    font-family:Arial,sans-serif;
                            |    font-size:20px;
                            |    font-weight:700;
                            |    height:55px;
                            |    padding:2px 8px;
                            |    text-shadow:#FE6 0 1px 0
                            |}
                            |
                            |.b2 {
                            |    background-color:#DAA520;
                            |}""".trimMargin())
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
                    div { style = "font-size:2"
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
        println("post " + req!!.getParameter("action"))
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
        doGet( req, resp)
    }
}