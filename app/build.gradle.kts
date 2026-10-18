plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "de.maarthen.qrdirekt"
    compileSdk = 34
    defaultConfig {
        applicationId = "de.maarthen.qrdirekt"
        minSdk = 23
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
