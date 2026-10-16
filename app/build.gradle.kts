import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// The diagnostic APK builds without access to a real Firebase project.
val firebaseConfigured = file("google-services.json").isFile
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")
val localConfig = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}
val mapsKey = providers.environmentVariable("MAPS_API_KEY")
    .orElse(localConfig.getProperty("MAPS_API_KEY", "")).get()
val signingProperties = Properties().apply {
    val config = rootProject.file("keystore.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}
fun signingValue(environment: String, property: String) = providers.environmentVariable(environment)
    .orElse(signingProperties.getProperty(property, "")).get()

android {
    namespace = "pl.slqpl.rodzina"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.slqpl.rodzina"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = providers.gradleProperty("appVersionCode").getOrElse("7").toInt().also { require(it > 0) }
        versionName = providers.gradleProperty("appVersionName").getOrElse("0.7.0").also {
            require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Use a version such as 0.2.0" }
        }
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("boolean", "MAPS_CONFIGURED", mapsKey.isNotBlank().toString())
        manifestPlaceholders["mapsApiKey"] = mapsKey
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue("SIGNING_STORE_FILE", "storeFile").ifBlank { "signing/rodzina-release.jks" })
            storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias").ifBlank { "rodzina" }
            keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
