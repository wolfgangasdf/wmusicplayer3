import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinversion = "1.7.20"
val javaversion = 18

group = "com.wolle"
version = ""
val cPlatforms = listOf("mac", "linux", "win") // compile for these platforms. "mac", "linux", "win"

println("Current Java version: ${JavaVersion.current()}")
if (JavaVersion.current().majorVersion.toInt() < javaversion) throw GradleException("Use Java >= $javaversion")

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.7.20"
    id("idea")
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("com.github.ben-manes.versions") version "0.43.0"
    id("org.beryx.runtime") version "1.12.7"
}

application {
    mainClass.set("MainKt")
    //defaultTasks = tasks.run
    applicationDefaultJvmArgs = listOf("-Dprism.verbose=true", "-Dprism.order=sw", // use software renderer
    	"-Dorg.eclipse.jetty.server.Request.maxFormKeys=2000")
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

runtime {
    imageZip.set(project.file("${project.buildDir}/image-zip/WMusicPlayer"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
    targetPlatform("mac", System.getenv("JDK_MAC_HOME"))
    targetPlatform("win", System.getenv("JDK_WIN_HOME"))
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
    version = "$javaversion"
    modules("javafx.base")
    // set compileOnly for crosspackage to avoid packaging host javafx jmods for all target platforms
    configuration = if (project.gradle.startParameter.taskNames.intersect(listOf("crosspackage", "dist")).isNotEmpty()) "compileOnly" else "implementation"
}
val javaFXOptions = the<org.openjfx.gradle.JavaFXOptions>()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinversion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    implementation("io.github.microutils:kotlin-logging:3.0.2")
    implementation("org.slf4j:slf4j-simple:2.0.3") // no colors, everything stderr
    implementation("org.eclipse.jetty:jetty-server:9.4.49.v20220914") // don't upgrade to >9, jwt needs servlet 3 container
    implementation("org.eclipse.jetty:jetty-servlet:9.4.49.v20220914")

    // jwt
    implementation("com.github.emweb:jwt:4.8.1") // https://jitpack.io/#emweb/jwt
    implementation("com.google.code.gson:gson:2.9.1") // otherwise, error with slider if opened with mac dashboard
    implementation("commons-fileupload:commons-fileupload:1.4") // needed for jwt, bug?

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
    // first row: suggestedModules
//    modules.set(listOf("java.desktop", "java.logging", "java.prefs", "java.xml", "jdk.unsupported", "jdk.jfr", "jdk.jsobject", "jdk.xml.dom",
//            "jdk.crypto.cryptoki","jdk.crypto.ec")) // for some https (apod: ec)

    if (cPlatforms.contains("mac")) targetPlatform("mac", System.getenv("JDK_MAC_HOME"))
    if (cPlatforms.contains("win")) targetPlatform("win", System.getenv("JDK_WIN_HOME"))
    if (cPlatforms.contains("linux")) targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
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
    kotlinOptions.jvmTarget = "1.8"
}

task("dist") {
    dependsOn("runtimeZip")
    doLast {
        println("Deleting build/[jre,install]")
        project.delete(project.runtime.jreDir.get(), "${project.buildDir.path}/install")
        println("Created zips in build/image-zip")
    }
}

