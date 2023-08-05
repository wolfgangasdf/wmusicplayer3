import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*


group = "com.wolle"
version = "1.0-SNAPSHOT"
val cPlatforms = listOf("mac", "linux", "win") // compile for these platforms. "mac", "mac-aarch64", "linux", "win"
val kotlinversion = "1.8.10"
val javaVersion = 19
println("Current Java version: ${JavaVersion.current()}")
if (JavaVersion.current().majorVersion.toInt() != javaVersion) throw GradleException("Use Java $javaVersion")

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.8.10"
    id("idea")
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("com.github.ben-manes.versions") version "0.44.0"
    id("org.beryx.runtime") version "1.13.0"
}

kotlin {
    jvmToolchain(javaVersion)
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-Dprism.verbose=true", "-Dprism.order=sw", // use software renderer
    	"-Dorg.eclipse.jetty.server.Request.maxFormKeys=2000")
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

repositories {
    mavenCentral()
    maven {
        setUrl("https://jitpack.io")
        content {
            includeModule("org.bitbucket.ijabz", "jaudiotagger")
            includeModule("com.github.emweb", "jwt")
            includeModule("com.github.olegcherr", "Aza-Kotlin-CSS")
        }
    }
}

javafx {
    version = "$javaVersion"
    modules("javafx.base")
    // set compileOnly for crosspackage to avoid packaging host javafx jmods for all target platforms
    if (project.gradle.startParameter.taskNames.intersect(listOf("crosspackage", "dist")).isNotEmpty()) {
        configuration = "compileOnly"
    }
}
val javaFXOptions = the<org.openjfx.gradle.JavaFXOptions>()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinversion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.7") // no colors, everything stderr
    implementation("org.eclipse.jetty:jetty-server:9.4.49.v20220914") // don't upgrade to >9, jwt needs servlet 3 container
    implementation("org.eclipse.jetty:jetty-servlet:9.4.49.v20220914")

    // jwt
    implementation("com.github.emweb:jwt:4.8.2") // https://jitpack.io/#emweb/jwt
    implementation("com.google.code.gson:gson:2.10.1") // otherwise, error with slider if opened with mac dashboard
    implementation("commons-fileupload:commons-fileupload:1.5") // needed for jwt, bug?

    // kotlinx.html
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")  {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }

    // css dsl
    implementation("com.github.olegcherr:Aza-Kotlin-CSS:d152fc49ab")

    // sound
    implementation("uk.co.caprica:vlcj:4.8.2")

    // media info
    implementation("org.bitbucket.ijabz:jaudiotagger:3.0.1") // https://jitpack.io/#org.bitbucket.ijabz/jaudiotagger


    cPlatforms.forEach {platform ->
        val cfg = configurations.create("javafx_$platform")
        org.openjfx.gradle.JavaFXModule.getJavaFXModules(javaFXOptions.modules).forEach { m ->
            project.dependencies.add(cfg.name,"org.openjfx:${m.artifactName}:${javaFXOptions.version}:$platform")
        }
    }
}

runtime {
    imageZip.set(project.file("${project.buildDir}/image-zip/WMusicPlayer"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    modules.set(listOf("java.desktop", "java.logging", "java.prefs", "java.xml", "jdk.unsupported", "jdk.jfr", "jdk.jsobject", "jdk.xml.dom"))

    // sets targetPlatform JDK for host os from toolchain, for others (cross-package) from adoptium / jdkDownload
    // https://github.com/beryx/badass-runtime-plugin/issues/99
    // if https://github.com/gradle/gradle/issues/18817 is solved: use toolchain
    fun setTargetPlatform(jfxplatformname: String) {
        val platf = if (jfxplatformname == "win") "windows" else jfxplatformname // jfx expects "win" but adoptium needs "windows"
        val os = org.gradle.internal.os.OperatingSystem.current()
        var oss = if (os.isLinux) "linux" else if (os.isWindows) "windows" else if (os.isMacOsX) "mac" else ""
        if (oss == "") throw GradleException("unsupported os")
        if (System.getProperty("os.arch") == "aarch64") oss += "-aarch64"// https://github.com/openjfx/javafx-gradle-plugin#4-cross-platform-projects-and-libraries
        if (oss == platf) {
            targetPlatform(jfxplatformname, javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.parentFile.parentFile.absolutePath)
        } else { // https://api.adoptium.net/q/swagger-ui/#/Binary/getBinary
            targetPlatform(jfxplatformname) {
                val ddir = "${if (os.isWindows) "c:/" else "/"}tmp/jdk$javaVersion-$platf"
                println("downloading jdks to or using jdk from $ddir, delete folder to update jdk!")
                @Suppress("INACCESSIBLE_TYPE")
                setJdkHome(
                    jdkDownload("https://api.adoptium.net/v3/binary/latest/$javaVersion/ga/$platf/x64/jdk/hotspot/normal/eclipse?project=jdk",
                        closureOf<org.beryx.runtime.util.JdkUtil.JdkDownloadOptions> {
                            downloadDir = ddir // put jdks here so different projects can use them!
                            archiveExtension = if (platf == "windows") "zip" else "tar.gz"
                        }
                    )
                )
            }
        }
    }
    cPlatforms.forEach { setTargetPlatform(it) }
}

tasks.withType(CreateStartScripts::class).forEach {script ->
    script.doFirst {
        script.classpath =  files("lib/*")
    }
}

// copy jmods for each platform
tasks["runtime"].doLast {
    cPlatforms.forEach { platform ->
        println("Copy jmods for platform $platform")
        val cfg = configurations["javafx_$platform"]
        cfg.resolvedConfiguration.files.forEach { f ->
            copy {
                from(f)
                into("${project.runtime.imageDir.get()}/${project.name}-$platform/lib")
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "$javaVersion"
}

task("dist") {
    dependsOn("runtimeZip")
    doLast {
        println("Deleting build/[jre,install]")
        project.delete(project.runtime.jreDir.get(), "${project.buildDir.path}/install")
        println("Created zips in build/image-zip")
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase(Locale.getDefault()).contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
    gradleReleaseChannel = "current"
}
