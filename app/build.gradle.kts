plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The project remains buildable before Firebase Console produces this file.
// Once app/google-services.json exists, the normal Firebase resource wiring is
// applied automatically on the next Gradle sync.
if (file("google-services.json").isFile) {
    pluginManager.apply("com.google.gms.google-services")
}

android {
    namespace = "com.portico.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.portico.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val clerkPublishableKey = providers.gradleProperty("CLERK_PUBLISHABLE_KEY")
            .orElse(providers.environmentVariable("CLERK_PUBLISHABLE_KEY"))
            .orElse("")
            .get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")

        val firebaseBridgeUrl = providers.gradleProperty("FIREBASE_TOKEN_BRIDGE_URL")
            .orElse(providers.environmentVariable("FIREBASE_TOKEN_BRIDGE_URL"))
            .orElse("")
            .get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "FIREBASE_TOKEN_BRIDGE_URL", "\"$firebaseBridgeUrl\"")
        val fileApiUrl = providers.gradleProperty("PORTICO_FILE_API_URL")
            .orElse(providers.environmentVariable("PORTICO_FILE_API_URL"))
            .orElse(firebaseBridgeUrl.replace("/firebase-token", "/files"))
            .get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "PORTICO_FILE_API_URL", "\"$fileApiUrl\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", file("google-services.json").isFile.toString())


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.clerk:clerk-android-api:1.0.37")
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
