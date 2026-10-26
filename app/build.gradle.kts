import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Home-network addresses, kept out of git: see lobby.properties.example.
val lobby = Properties().apply {
    rootProject.file("lobby.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

// The WiiM's Spotify Connect endpoint (port 5356) only speaks plain HTTP: allow it for the WiiM's address alone.
abstract class NetworkSecurityConfig : DefaultTask() {
    @get:Input
    abstract val wiimHost: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val domain = wiimHost.get().takeIf { it.isNotEmpty() }?.let {
            "\n    <domain-config cleartextTrafficPermitted=\"true\">\n        <domain includeSubdomains=\"false\">$it</domain>\n    </domain-config>\n"
        }.orEmpty()
        outputDir.get().file("xml/network_security_config.xml").asFile.apply { parentFile.mkdirs() }
            .writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<network-security-config>$domain</network-security-config>\n")
    }
}

val networkSecurityConfig = tasks.register<NetworkSecurityConfig>("networkSecurityConfig") {
    wiimHost.set(lobby.getProperty("wiim", ""))
    outputDir.set(layout.buildDirectory.dir("generated/networkSecurity"))
}

androidComponents {
    onVariants { variant -> variant.sources.res?.addGeneratedSourceDirectory(networkSecurityConfig, NetworkSecurityConfig::outputDir) }
}

// The release key lives outside the repo; see "Signing" in the README.
val signing = Properties().apply {
    val path = System.getenv("TV_LOBBY_SIGNING") ?: "${System.getProperty("user.home")}/.config/tv-lobby/signing.properties"
    file(path).takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.leo.tvlobby"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.tvlobby"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "WIIM_HOST", "\"${lobby.getProperty("wiim", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (!signing.isEmpty) {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // CecTool is started by class name through app_process, so names must survive.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("dev.mobile:dadb:1.2.10")
}
