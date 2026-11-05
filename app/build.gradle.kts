import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.quackify.app"
    compileSdk {
        version = release(37)
    }

    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("local.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    defaultConfig {
        applicationId = "com.quackify.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    flavorDimensions += "islandId"
    productFlavors {
        create("standard") {
            dimension = "islandId"
            applicationId = "com.quackify.app"
        }
        create("spotify") {
            dimension = "islandId"
            applicationId = "com.spotify.music"
        }
        create("apple") {
            dimension = "islandId"
            applicationId = "com.apple.android.music"
        }
    }

    signingConfigs {
        create("quackify") {
            val envStoreFile = System.getenv("RELEASE_STORE_FILE")
            if (envStoreFile != null && file(envStoreFile).exists()) {
                storeFile = file(envStoreFile)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                val path = keystoreProperties["signing.storeFile"] as String?
                if (path != null && file(path).exists()) {
                    storeFile = file(path)
                    storePassword = keystoreProperties["signing.storePassword"] as String?
                    keyAlias = keystoreProperties["signing.keyAlias"] as String?
                    keyPassword = keystoreProperties["signing.keyPassword"] as String?
                } else {
                    storeFile = rootProject.file("keystore/Quackify.jks")
                    storePassword = "quackifyrebranded"
                    keyAlias = "quackify"
                    keyPassword = "quackifyrebranded"
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "Quackify β"
            signingConfig = signingConfigs.getByName("quackify")
        }
        release {
            manifestPlaceholders["appName"] = "Quackify"
            signingConfig = signingConfigs.getByName("quackify")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

val islandFlavors = mapOf(
    "spotify" to "com.spotify.music",
    "apple" to "com.apple.android.music"
)

androidComponents {
    onVariants { variant ->
        islandFlavors[variant.flavorName]?.let { id ->
            variant.applicationId.set(id)
        }
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val appName = "Quackify"
                val versionName = android.defaultConfig.versionName ?: "1.0.0"
                val flavor = variant.flavorName ?: "standard"
                val type = variant.buildType ?: "debug"
                output.outputFileName.set("$appName-v$versionName-$flavor-$type.apk")
            }
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":krespot-connect"))
    implementation(project(":krespot-player"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
