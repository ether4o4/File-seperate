plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ether4o4.filevault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ether4o4.filevault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Full LibreOffice (LOKit) native libs are enormous. When you add them,
        // list only the ABIs you ship to keep the APK/AAB manageable.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    // The LibreOffice program/ payload (fonts, filters, configuration) is stored
    // uncompressed as assets so it can be memory-mapped at runtime instead of
    // being unpacked. Native .so libraries are likewise left uncompressed.
    androidResources { noCompress += listOf("so") }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = true } // required by LibreOfficeKit loading
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room (virtual folder tree + file metadata)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Viewers
    implementation(libs.coil.compose)          // images
    implementation(libs.androidx.media3.exoplayer) // audio + video
    implementation(libs.androidx.media3.ui)

    // Optional: LibreOfficeKit AAR, dropped into ./libs by you (see docs/LIBREOFFICE.md).
    // Uncomment once present:
    // implementation(name = "libreofficekit", ext = "aar")
}
