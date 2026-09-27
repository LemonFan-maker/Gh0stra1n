plugins {
    id("com.android.application")
}

android {
    namespace = "com.orionisli.gh0stra1n"
    compileSdk = 36

    val gitCommitHash: String = try {
        val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
        val h = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (h.isNotBlank()) h else "11f9618"
    } catch (_: Exception) {
        "11f9618"
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    defaultConfig {
        applicationId = "com.orionisli.gh0stra1n"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.1"
        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
