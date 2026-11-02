// Flux Core: what the Flux apps (Keyboard, Gaming, Streaming, Desktop) share. Each app has this
// repository as a submodule in core/ and builds it with itself.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.fluxsniffermods.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api("androidx.core:core-ktx:1.17.0")
    api("androidx.activity:activity-compose:1.10.1")
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    api(platform("androidx.compose:compose-bom:2024.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")
    api("com.squareup.okhttp3:okhttp:4.11.0")
    api("androidx.work:work-runtime-ktx:2.9.1")
    // Shizuku, for the ADB shell when it's running
    api("dev.rikka.shizuku:api:13.1.5")
    api("dev.rikka.shizuku:provider:13.1.5")
    // The built-in shell: pairs with wireless debugging and starts the shell helper
    api("com.github.MuntashirAkon:libadb-android:3.1.1")
    api("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
}
