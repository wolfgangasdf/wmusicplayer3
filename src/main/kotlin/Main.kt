import eu.webtoolkit.jwt.ServletInit
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource



fun main(args: Array<String>) {

    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
    System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "INFO")
    System.setProperty("org.slf4j.simpleLogger.log.eu.webtoolkit.jwt", "INFO")

    val logger = KotlinLogging.logger {} // after set properties!

    logger.info("info")
    logger.debug("debug")
    logger.trace("trace")

    val server = Server(8080)

    val context = ServletContextHandler(ServletContextHandler.SESSIONS)

    context.baseResource = Resource.newResource("/WebRoot") // TODO does this work? no

    val shMobile = ServletHolder("mobile", KotlinxHtmlServlet::class.java)
    context.addServlet(shMobile, "/mobile/*")

    val shMobileRes = ServletHolder("mobileres", DefaultServlet::class.java)
    shMobileRes.setInitParameter("dirAllowed","true")
    context.addServlet(shMobileRes, "/mobileres/*")

    val shJwt = ServletHolder("jwt", JwtServlet::class.java)
    // shJwt.setInitParameter("tracking-mode", "URL") // doesn't work.
    context.addEventListener(ServletInit()) // TODO put in separate context because of this?
    context.addServlet(shJwt, "/*")

    server.handler = context

    server.start()
    server.join()
}

