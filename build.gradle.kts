import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("js") version "1.5.30"
}

group = "org.pongasoft"
version = "1.3.0"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlinx")
    }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.2")
}
kotlin {
    js(IR) {
        browser {
            binaries.executable()
            webpackTask {
                cssSupport.enabled = true
            }
            runTask {
                cssSupport.enabled = true
            }
        }
    }
}

// deploy task copies the relevant files to the website folder hosting it
val deployDir = File("/Volumes/Development/local/jamba.dev-www/assets/quickstart/web/js")

val kotlinWebpackTask = tasks.getByName<KotlinWebpack>("browserProductionWebpack")

tasks.create<Copy>("deploy") {
    from(kotlinWebpackTask.destinationDirectory)
    into(deployDir)
    include(kotlinWebpackTask.outputFileName, "js/jszip.min.js")
}.dependsOn("build")

