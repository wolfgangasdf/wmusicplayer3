import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val kotlinversion = "1.3.31"

buildscript {
    repositories {
        mavenCentral()
        jcenter() // shadowJar
    }
}

group = "com.wolle"
version = ""

plugins {
    kotlin("jvm") version "1.3.31"
    id("idea")
    id("application")
    id("com.github.ben-manes.versions") version "0.21.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("edu.sc.seis.macAppBundle") version "2.3.0"
}

tasks.withType<Wrapper> {
    gradleVersion = "5.4.1"
}

application {
    mainClassName = "MainKt"
    //defaultTasks = tasks.run
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
                "Description" to "wmusicplayer jar",
                "Implementation-Title" to "WMusicPlayer",
                "Implementation-Version" to version,
                "Main-Class" to "MainKt"
        ))
    }
}

tasks.withType<ShadowJar> {
    // uses manifest from above!
    archiveBaseName.set("wmusicplayer")
    archiveClassifier.set("")
    mergeServiceFiles() // essential to enable flac etc
}

repositories {
    mavenLocal() // for jwt
    mavenCentral()
    jcenter() // for kotlinx.html, aza-css
    // maven { setUrl("https://jitpack.io") } // jaadec
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinversion")
    compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    compile("io.github.microutils:kotlin-logging:1.6.26")
    compile("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr
    compile("org.eclipse.jetty:jetty-server:9.4.18.v20190429")
    compile("org.eclipse.jetty:jetty-servlet:9.4.18.v20190429")

    // jwt
    compile("eu.webtoolkit:jwt:3.3.12")
    runtime("eu.webtoolkit:jwt:3.3.12")
    compile("com.google.code.gson:gson:2.8.5") // otherwise, error with slider if opened with mac dashboard

    // kotlinx.html
    compile("org.jetbrains.kotlinx:kotlinx-html-jvm:0.6.12")

    // css dsl
    compile ("azadev.kotlin:aza-kotlin-css:1.0")

    // sound
    compile("uk.co.caprica:vlcj:4.1.0") // 4.1.0
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

task("dist") {
    dependsOn("shadowJar") // fat jar
}

