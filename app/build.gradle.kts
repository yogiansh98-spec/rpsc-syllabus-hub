plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.syllabushub.rpsc"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.syllabushub.rpsc"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0-multilingual"
        // Public GitHub raw JSON; publish only validated and manually reviewed syllabus data.
        val catalogUrl = providers.gradleProperty("catalogUrl")
            .orElse("https://raw.githubusercontent.com/yogiansh98-spec/rpsc-syllabus-hub/main/catalog/catalog.json")
            .get().ifBlank { "https://raw.githubusercontent.com/yogiansh98-spec/rpsc-syllabus-hub/main/catalog/catalog.json" }
        require(catalogUrl.isEmpty() || catalogUrl.startsWith("https://")) { "HTTPS catalog URL required" }
        buildConfigField("String", "CATALOG_URL", "\"$catalogUrl\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
}
