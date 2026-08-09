plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mythos.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mythos.client"
        minSdk = 23
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.0-professional-ui"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Used for QR import/export. Xray itself comes from the pinned official libXray AAR.
    implementation("com.google.zxing:core:3.5.3")
    implementation(files("libs/libXray.aar"))
}
