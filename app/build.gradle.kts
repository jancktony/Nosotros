import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Lee la configuración de Firebase de app/google-services.json (si existe)
// sin necesidad del plugin de Google Services.
data class FbConfig(val apiKey: String, val appId: String, val projectId: String)

fun readFirebaseConfig(): FbConfig {
    val f = file("google-services.json")
    if (!f.exists()) return FbConfig("", "", "")
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(f) as Map<String, Any?>
    val projectInfo = json["project_info"] as Map<String, Any?>
    val clients = json["client"] as List<Map<String, Any?>>
    val client = clients.firstOrNull {
        val info = (it["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>
        info["package_name"] == "com.tony.nosotros"
    } ?: clients.first()
    val appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String
    val apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String
    return FbConfig(apiKey, appId, projectInfo["project_id"] as String)
}

val fb = readFirebaseConfig()
// Local development: ignored root secrets.properties. CI: MAPS3D_API_KEY secret.
val mapSecrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val maps3dKey = (System.getenv("MAPS3D_API_KEY") ?: mapSecrets.getProperty("MAPS3D_API_KEY", "")).trim()
require(maps3dKey.isEmpty() || maps3dKey.matches(Regex("[A-Za-z0-9_-]+"))) {
    "MAPS3D_API_KEY tiene un formato inválido"
}

android {
    namespace = "com.tony.nosotros"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tony.nosotros"
        minSdk = 29
        targetSdk = 35
        versionCode = 9
        versionName = "1.8"
        buildConfigField("String", "GH_REPO", "\"${(project.findProperty("ghRepo") as String?) ?: ""}\"")
        manifestPlaceholders["MAPS3D_API_KEY"] = maps3dKey
        buildConfigField("boolean", "MAPS3D_ENABLED", maps3dKey.isNotEmpty().toString())
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField("String", "FB_API_KEY", "\"${fb.apiKey}\"")
        buildConfigField("String", "FB_APP_ID", "\"${fb.appId}\"")
        buildConfigField("String", "FB_PROJECT_ID", "\"${fb.projectId}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("nosotros.keystore")
            storePassword = System.getenv("KS_PASS")
            keyAlias = "nosotros"
            keyPassword = System.getenv("KS_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("com.google.android.gms:play-services-maps3d:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
