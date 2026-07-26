plugins {
    id("com.android.application")
}

android {
    namespace = "cl.streambox.tv"
    compileSdk = 36

    val ciKeystorePath = System.getenv("VIBEDLNA_KEYSTORE_PATH")
    val ciSigningConfig = if (!ciKeystorePath.isNullOrBlank()) {
        signingConfigs.create("github") {
            storeFile = file(ciKeystorePath)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    } else {
        null
    }

    defaultConfig {
        applicationId = "cl.vibedlna.player"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("debug") {
            ciSigningConfig?.let { signingConfig = it }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ciSigningConfig?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    testImplementation("junit:junit:4.13.2")
}
