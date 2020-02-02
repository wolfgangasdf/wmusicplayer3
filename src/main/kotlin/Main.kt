import eu.webtoolkit.jwt.ServletInit
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder


fun main() {

    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
    System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "INFO")
    System.setProperty("org.slf4j.simpleLogger.log.eu.webtoolkit.jwt", "INFO")

    val logger = KotlinLogging.logger {} // after set properties!

    logger.info("info")
    logger.debug("debug")
    logger.trace("trace")

    MusicPlayer // initialize it!

//    for (url in (ClassLoader.getSystemClassLoader() as URLClassLoader).urLs) logger.debug("classpath: " + url.file)
//    MusicPlayer.loader = Thread.currentThread().contextClassLoader
//    MusicPlayerBackend.loader = Thread.currentThread().contextClassLoader

    val server = Server(Settings.port)

    val context = ServletContextHandler(ServletContextHandler.SESSIONS)

    context.resourceBase = ClassLoader.getSystemResource("webroot").toExternalForm()
    context.maxFormKeys = 5000 // important otherwise table fails after some scrolling.

    val shMobile = ServletHolder("mobile", KotlinxHtmlServlet::class.java)
    context.addServlet(shMobile, "/mobile/*")

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

