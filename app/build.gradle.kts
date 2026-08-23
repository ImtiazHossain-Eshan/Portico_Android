import java.util.Properties

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
    pluginManager.apply("com.google.firebase.crashlytics")
}

/*
 * Release signing is read from keystore.properties, which is gitignored and
 * never committed. Without it the release build falls back to the debug key so
 * `assembleRelease` still works for local verification; it simply cannot be
 * uploaded to Play. Create the keystore yourself with keytool -- no build file
 * should ever contain a password.
 */
/*
 * Build keys live in keys.properties, which is gitignored, so no key of any kind
 * is tracked. keys.properties.example documents what belongs there. Values may
 * also come from -P or the environment, which is how CI supplies them.
 * Missing values are empty rather than fatal: the project still builds, the app
 * simply cannot reach Clerk or the bridge.
 */
val keyProperties = Properties().apply {
    val file = rootProject.file("keys.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun buildKey(name: String): String =
    keyProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name)
            .orElse(providers.environmentVariable(name))
            .orElse("")
            .get()

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")?.let {
    rootProject.file(it).isFile
} ?: false

android {
    namespace = "com.portico.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.portico.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val clerkPublishableKey = buildKey("CLERK_PUBLISHABLE_KEY")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")

        val firebaseBridgeUrl = buildKey("FIREBASE_TOKEN_BRIDGE_URL")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "FIREBASE_TOKEN_BRIDGE_URL", "\"$firebaseBridgeUrl\"")
        val fileApiUrl = buildKey("PORTICO_FILE_API_URL")
            .ifEmpty { firebaseBridgeUrl.replace("/firebase-token", "/files") }
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "PORTICO_FILE_API_URL", "\"$fileApiUrl\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", file("google-services.json").isFile.toString())


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // No applicationIdSuffix: google-services.json carries a single
            // client for com.portico.android, and a suffixed debug package
            // would need its own app registered in the Firebase Console.
            versionNameSuffix = "-debug"
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
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
    // Per-app language on API 26-32; the framework only gained it in 33.
    implementation("androidx.appcompat:appcompat:1.7.0")
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
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Custom Tabs for the payment gateway page. A WebView would work, but the
    // member would be typing a card into a page with no address bar and no way
    // to see whose page it is.
    implementation("androidx.browser:browser:1.8.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
