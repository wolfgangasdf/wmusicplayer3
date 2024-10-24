import eu.webtoolkit.jwt.ServletInit
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder

const val metaViewport = "user-scalable=no, initial-scale=1, maximum-scale=1, minimum-scale=1, width=device-width, height=device-height, target-densitydpi=device-dpi"
const val mobileurl = "/mobile"

fun main() {

    System.setProperty("jna.encoding", "UTF8")
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyyMMdd-HH:mm:ss")
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
    System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "INFO")
    System.setProperty("org.slf4j.simpleLogger.log.eu.webtoolkit.jwt", "INFO")
    val logger = KotlinLogging.logger {} // after set properties!

    logger.info("info")
    logger.debug("debug")
    logger.trace("trace")

    MusicPlayer // initialize it!

    val server = Server(Settings.port)

    val context = ServletContextHandler(ServletContextHandler.SESSIONS)

    context.resourceBase = ClassLoader.getSystemResource("webroot").toExternalForm()
    context.maxFormKeys = 5000 // important otherwise table fails after some scrolling.

    val shMobile = ServletHolder("mobile", KotlinxHtmlServlet::class.java)
    context.addServlet(shMobile, "$mobileurl/*")

    val shResources = ServletHolder("res", DefaultServlet::class.java)
    shResources.setInitParameter("dirAllowed","true")
    context.addServlet(shResources, "/res/*")

    val shJwt = ServletHolder("jwt", JwtServlet::class.java)
    shJwt.isAsyncSupported = true
    // shJwt.setInitParameter("tracking-mode", "URL") // doesn't work. the following does:
    context.addEventListener(ServletInit()) // put in separate context because of this?
    context.addServlet(shJwt, "/*") // with e.g. "/w/*" the css paths in the generated html wrong. bug?
    // it doesn't help to set in JwtApp.config... setInternalPath("/w/", true)

    server.handler = context

    server.start()
    server.join()
}

