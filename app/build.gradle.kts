plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.jayyo.readeverything"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "io.github.jayyo.readeverything"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/{DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
            excludes += "/META-INF/versions/**"
            pickFirsts += "META-INF/*"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Image loading + SVG + GIF (Coil 3)
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-svg:3.2.0")
    implementation("io.coil-kt.coil3:coil-gif:3.2.0")

    // Markdown (Compose-native)
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.27.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.27.0")

    // Code syntax highlighting
    implementation("dev.snipme:highlights:1.0.0")

    // JSON tree viewer
    implementation("com.sebastianneubauer.jsontree:jsontree:2.5.0")

    // RTF parsing (text extraction)
    implementation("com.github.joniles:rtfparserkit:1.16.0")

    // HWP / HWPX offline text-extraction fallback
    implementation("kr.dogfoot:hwplib:1.1.9")
    implementation("kr.dogfoot:hwpxlib:1.0.5")

    // Networking for the conversion server (office/hwp -> pdf)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
