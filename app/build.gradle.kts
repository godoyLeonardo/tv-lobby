import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
