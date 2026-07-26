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
        versionCode = 6
        versionName = "0.3.3"
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("org.jupnp:org.jupnp:3.0.4")
    implementation("org.jupnp:org.jupnp.support:3.0.4")
    implementation("org.jupnp:org.jupnp.android:3.0.4")
    implementation("javax.servlet:javax.servlet-api:4.0.1")
    implementation("org.eclipse.jetty:jetty-server:9.2.30.v20200428")
    implementation("org.eclipse.jetty:jetty-servlet:9.2.30.v20200428")
    implementation("org.eclipse.jetty:jetty-client:9.2.30.v20200428")

    testImplementation("junit:junit:4.13.2")
}
