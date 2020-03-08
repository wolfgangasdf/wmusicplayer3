import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinversion = "1.3.70"

buildscript {
    repositories {
        mavenCentral()
    }
}

group = "com.wolle"
version = ""
val cPlatforms = listOf("mac") // compile for these platforms. "mac", "linux", "win"

println("Current Java version: ${JavaVersion.current()}")
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    if (JavaVersion.current().toString() != "13") throw GradleException("Use Java 13")
}

plugins {
    kotlin("jvm") version "1.3.70"
    id("idea")
    application
    id("org.openjfx.javafxplugin") version "0.0.8"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("org.beryx.runtime") version "1.8.0"
}

application {
    mainClassName = "MainKt"
    //defaultTasks = tasks.run
    applicationDefaultJvmArgs = listOf("-Dorg.eclipse.jetty.server.Request.maxFormKeys=2000")
}

runtime {
    imageZip.set(project.file("${project.buildDir}/image-zip/WMusicPlayer"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
    targetPlatform("mac", System.getenv("JDK_MAC_HOME"))
    targetPlatform("win", System.getenv("JDK_WIN_HOME"))
}

repositories {
    mavenLocal() // for jwt
    mavenCentral()
    jcenter() // for kotlinx.html, aza-css
    maven { // jitpack: jaudiotagger
        setUrl("https://jitpack.io")
        metadataSources { artifact() } // otherwise error for tagged versions
    }
}

javafx {
    version = "13"
    modules("javafx.base")
    // set compileOnly for crosspackage to avoid packaging host javafx jmods for all target platforms
    configuration = if (project.gradle.startParameter.taskNames.intersect(listOf("crosspackage", "dist")).isNotEmpty()) "compileOnly" else "compile"
}
val javaFXOptions = the<org.openjfx.gradle.JavaFXOptions>()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinversion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    implementation("io.github.microutils:kotlin-logging:1.7.8")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr
    implementation("org.eclipse.jetty:jetty-server:9.4.18.v20190429")
    implementation("org.eclipse.jetty:jetty-servlet:9.4.18.v20190429")

    // jwt
    implementation("eu.webtoolkit:jwt:4.2.0")
    implementation("com.google.code.gson:gson:2.8.6") // otherwise, error with slider if opened with mac dashboard

    // kotlinx.html
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.1")

    // css dsl
    implementation("azadev.kotlin:aza-kotlin-css:1.0")

    // sound
    implementation("uk.co.caprica:vlcj:4.4.0")

    // media info
    implementation("org.bitbucket.ijabz:jaudiotagger:master-v2.2.5-g85343bc-481") // https://jitpack.io/#org.bitbucket.ijabz/jaudiotagger/master-SNAPSHOT


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

