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
        versionCode = 10
        versionName = "1.1.5"
        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = project.findProperty("RELEASE_STORE_FILE") as? String
                ?: System.getenv("RELEASE_STORE_FILE")
            val keystoreFile = storeFilePath?.let { file(it) }

            if (keystoreFile != null && keystoreFile.exists()) {
                fun secret(name: String): String =
                    (project.findProperty(name) as? String)?.takeIf { it.isNotBlank() }
                        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
                        ?: throw GradleException(
                            "Release keystore $keystoreFile exists but $name is unset or blank. " +
                                "Provide it as a Gradle property (-P$name=...), an environment " +
                                "variable, or a repository secret. Refusing to sign with an " +
                                "empty password, which fails as 'Given final block not " +
                                "properly padded' deep in the Android Gradle Plugin."
                        )

                storeFile = keystoreFile
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
