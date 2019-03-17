import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val kotlinversion = "1.3.21"

buildscript {
    repositories {
        mavenCentral()
        jcenter() // shadowJar
    }
//    dependencies {
//        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
//        classpath "com.github.jengelman.gradle.plugins:shadow:4.0.2"
//        classpath "com.github.ben-manes:gradle-versions-plugin:0.20.0"
//    }
}

group = "com.wolle"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.3.21"
    id("idea")
    id("application")
    id("com.github.ben-manes.versions") version "0.21.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("edu.sc.seis.macAppBundle") version "2.3.0"
}

tasks.withType<Wrapper> {
    gradleVersion = "5.2.1"
}

application {
    mainClassName = "MainKt"
    //defaultTasks = tasks.run
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
                "Description" to "wmusicplayer3 jar",
                "Implementation-Title" to "WMusicPlayer",
                "Implementation-Version" to version,
                "Main-Class" to "MainKt"
        ))
    }
}

tasks.withType<ShadowJar> {
    // uses manifest from above!
    baseName = "wmusicplayer3"
    classifier = ""
    version = ""
    mergeServiceFiles() // essential to enable flac etc
}

repositories {
    mavenCentral()
    mavenLocal() // for jwt
    jcenter() // for kotlinx.html, aza-css
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinversion")
    compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    compile("io.github.microutils:kotlin-logging:1.6.25")
    compile("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr
    compile("org.eclipse.jetty:jetty-server:9.4.15.v20190215")
    compile("org.eclipse.jetty:jetty-servlet:9.4.15.v20190215")

    // jwt
    compile("eu.webtoolkit:jwt:3.3.12")
    runtime("eu.webtoolkit:jwt:3.3.12")
    compile("com.google.code.gson:gson:2.8.5") // otherwise, error with slider if opened with mac dashboard

    // kotlinx.html
    compile("org.jetbrains.kotlinx:kotlinx-html-jvm:0.6.12")

    // css dsl
    compile ("azadev.kotlin:aza-kotlin-css:1.0")

    // sound
    compile("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    compile("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    compile("com.googlecode.soundlibs:jlayer:1.0.1.4")
    compile("com.googlecode.soundlibs:basicplayer:3.0.0.0")
    compile("org.jflac:jflac-codec:1.5.2")
    // "com.googlecode.soundlibs" % "tritonus-share" % "0.3.7.4"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

task("dist") {
    dependsOn("shadowJar") // fat jar
}

