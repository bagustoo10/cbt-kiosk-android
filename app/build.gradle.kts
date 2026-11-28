plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val rawAppName = (findProperty("appName") as String?)?.trim().orEmpty().ifEmpty { "CBT Exam Browser" }
val rawSlug = (findProperty("schoolSlug") as String?)?.trim().orEmpty().ifEmpty { "ruangujibagus" }
val cleanSlug = rawSlug.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "sekolah" }
val safeSlug = if (cleanSlug.first().isDigit()) "s$cleanSlug" else cleanSlug

val rawCbtUrlEnc = (findProperty("cbtUrlEnc") as String?)?.trim().orEmpty()
val rawCbtUrl = (findProperty("cbtUrl") as String?)?.trim().orEmpty().ifEmpty { "https://ruang-uji-bagus.web.app" }
val rawUaKey = (findProperty("uaKey") as String?)?.trim().orEmpty().ifEmpty { "CBT-KIOSK-ANDROID" }
val rawExitPin = (findProperty("exitPin") as String?)?.trim().orEmpty().ifEmpty { "1234" }
val rawEntryPin = (findProperty("entryPin") as String?)?.trim().orEmpty()
val rawThemeColor = (findProperty("themeColor") as String?)?.trim().orEmpty().ifEmpty { "#059669" }
val rawRequirePinning = ((findProperty("requirePinning") as String?)?.trim()?.lowercase() ?: "true") != "false"
val rawRequireLicense = ((findProperty("requireLicenseCheck") as String?)?.trim()?.lowercase() ?: "true") != "false"
val rawLicenseProject = (findProperty("licenseProject") as String?)?.trim().orEmpty().ifEmpty { "owner-apk-bagus" }
val rawSchoolFbProject = (findProperty("schoolFirebaseProject") as String?)?.trim().orEmpty().ifEmpty { "ruang-uji-bagus" }
val rawOwnerWa = (findProperty("ownerWa") as String?)?.trim().orEmpty().ifEmpty { "6281234567890" }

val rawVersionCode = (findProperty("apkVersionCode") as String?)?.trim()?.toIntOrNull() ?: 7

fun esc(v: String): String = v.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.bagustoo.cbtkiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bagustoo.cbt.$safeSlug"
        minSdk = 24
        targetSdk = 34
        versionCode = rawVersionCode
        versionName = "2.6.$rawVersionCode"

        resValue("string", "app_name", rawAppName)
        buildConfigField("String", "SCHOOL_SLUG", "\"${esc(safeSlug)}\"")
        buildConfigField("String", "CBT_URL_ENC", "\"${esc(rawCbtUrlEnc.ifEmpty { rawCbtUrl })}\"")
        buildConfigField("String", "KIOSK_UA_KEY", "\"${esc(rawUaKey)}\"")
        buildConfigField("String", "EXIT_ADMIN_PIN", "\"${esc(rawExitPin)}\"")
        buildConfigField("String", "ENTRY_PIN", "\"${esc(rawEntryPin)}\"")
        buildConfigField("String", "THEME_COLOR", "\"${esc(rawThemeColor)}\"")
        buildConfigField("boolean", "REQUIRE_PINNING", rawRequirePinning.toString())
        buildConfigField("boolean", "REQUIRE_LICENSE_CHECK", rawRequireLicense.toString())
        buildConfigField("String", "LICENSE_SERVER_PROJECT", "\"${esc(rawLicenseProject)}\"")
        buildConfigField("String", "SCHOOL_FIREBASE_PROJECT", "\"${esc(rawSchoolFbProject)}\"")
        buildConfigField("String", "OWNER_WA", "\"${esc(rawOwnerWa)}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val ks = rootProject.file("keystore.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASS")?.ifBlank { null } ?: "bagustoo2026"
                keyAlias = System.getenv("KEYSTORE_ALIAS")?.ifBlank { null } ?: "cbtkiosk"
                keyPassword = System.getenv("KEY_PASS")?.ifBlank { null }
                    ?: (System.getenv("KEYSTORE_PASS")?.ifBlank { null } ?: "bagustoo2026")
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
            val ks = rootProject.file("keystore.jks")
            signingConfig = if (ks.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
