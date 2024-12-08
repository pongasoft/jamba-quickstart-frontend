import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("js") version "1.8.21"
}

group = "org.pongasoft"
version = "1.4.2"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.2")
}

kotlin {
    js(IR) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
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

