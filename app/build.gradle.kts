import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plaintextlab.dumblauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.plaintextlab.dumblauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.1"
    }
    // Release signing: set SIGNING_KEYSTORE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS,
    // SIGNING_KEY_PASSWORD (CI secrets), or put the same keys in an untracked keystore.properties.
    val signing = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        listOf("SIGNING_KEYSTORE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
            .forEach { k -> System.getenv(k)?.let { setProperty(k, it) } }
    }
    val keystore = signing.getProperty("SIGNING_KEYSTORE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    if (keystore != null) {
        signingConfigs.create("release") {
            storeFile = keystore
            storePassword = signing.getProperty("SIGNING_STORE_PASSWORD")
            keyAlias = signing.getProperty("SIGNING_KEY_ALIAS")
            keyPassword = signing.getProperty("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
