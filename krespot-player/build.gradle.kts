plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.quackify.backend.player"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":krespot-connect"))
    implementation(libs.kotlinx.coroutines.android)
}
