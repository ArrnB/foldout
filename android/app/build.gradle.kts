plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The app shows the very same page as the website: foldout-scanner.html and
// its icons are copied in from the repository root at build time, so there
// is only ever one copy of the scanner to change.
val webDir = layout.buildDirectory.dir("generated/web")
val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../foldout-scanner.html"))
    from(rootProject.file("../icons")) { into("icons") }
    into(webDir)
}

android {
    namespace = "app.foldout"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.foldout"
        // Android 10+: saving to Downloads then needs no storage permission.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    sourceSets["main"].assets.srcDir(webDir)

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Serves the page from https://appassets.androidplatform.net, a proper
    // origin, so IndexedDB (saved scans) and fetch() behave as on the web.
    implementation("androidx.webkit:webkit:1.12.1")
}
