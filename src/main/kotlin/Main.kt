import eu.webtoolkit.jwt.ServletInit
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource
import javax.sound.sampled.spi.AudioFileReader


fun main() {

    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
    System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "INFO")
    System.setProperty("org.slf4j.simpleLogger.log.eu.webtoolkit.jwt", "INFO")

    val logger = KotlinLogging.logger {} // after set properties!

    logger.info("info")
    logger.debug("debug")
    logger.trace("trace")

//    logger.info("audio file readers [${Thread.currentThread().id}]: " + com.sun.media.sound.JDK13Services.getProviders(AudioFileReader::class.java).joinToString { x -> x.toString() })

//    for (url in (ClassLoader.getSystemClassLoader() as URLClassLoader).urLs) logger.debug("classpath: " + url.file)

    MusicPlayer
//    MusicPlayer.loader = Thread.currentThread().contextClassLoader
//    MusicPlayerBackend.loader = Thread.currentThread().contextClassLoader

    val server = Server(Settings.port)

    val context = ServletContextHandler(ServletContextHandler.SESSIONS)
    context.baseResource = Resource.newResource("/WebRoot") // TODO does this work? no
    context.maxFormKeys = 5000 // important otherwise table fails after some scrolling.

    val shMobile = ServletHolder("mobile", KotlinxHtmlServlet::class.java)
    context.addServlet(shMobile, "/mobile/*")

    val shMobileRes = ServletHolder("mobileres", DefaultServlet::class.java)
    shMobileRes.setInitParameter("dirAllowed","true")
    context.addServlet(shMobileRes, "/mobileres/*")

    val shJwt = ServletHolder("jwt", JwtServlet::class.java)
    shJwt.isAsyncSupported = true
//     shJwt.setInitParameter("tracking-mode", "URL") // doesn't work. the following does:
    context.addEventListener(ServletInit()) // TODO put in separate context because of this?
    context.addServlet(shJwt, "/*") // otherwise (eg /w/*) css paths in generated html wrong. bug? css is there.
        // also not with JwtApp... setInternalPath("/w/", true) has no effect

    server.handler = context

    server.start()
    server.join()
}

