import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ========== 项目级签名：固化到项目内 keystore，换电脑构建签名一致 ==========
val releaseKeystoreFile = rootProject.file("keystore/release.keystore")
val ReleaseKeyAlias = "androiddebugkey"
val ReleaseKeyPassword = "android"

// ========== 版本状态：每次 release 构建成功后自动 +1 ==========
val versionPropsFile = rootProject.file("version.properties")

fun loadVersionProps(): Properties {
    val props = Properties()
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { props.load(it) }
    }
    return props
}

fun bumpVersionName(name: String): String {
    val parts = name.split(".").toMutableList()
    if (parts.isEmpty()) return "1.0.1"
    val last = parts.last().toIntOrNull() ?: 0
    parts[parts.size - 1] = (last + 1).toString()
    return parts.joinToString(".")
}

/** versionName → versionCode 对齐推导：1.0.4 → 10004，1.1.0 → 10100。 */
fun versionCodeFromName(name: String): Int {
    val parts = name.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}

val versionProps = loadVersionProps()
val ciVersionCode = providers.environmentVariable("VERSION_CODE").map { it.toInt() }.orNull
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull
val buildVersionName = ciVersionName ?: (versionProps.getProperty("versionName") ?: "1.0.0")
val buildVersionCode = ciVersionCode ?: versionCodeFromName(buildVersionName)

android {
    namespace = "com.sky22333.skyadb"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sky22333.skyadb"
        minSdk = 24
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            keyAlias = ReleaseKeyAlias
            storePassword = ReleaseKeyPassword
            keyPassword = ReleaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 项目内固定签名（与历史 1.0.2 / 1.0.3 同一证书，可覆盖安装）。
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Bouncy Castle's Java multi-release OSGi descriptors are not Android runtime resources.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            merges += "META-INF/LICENSE.md"
        }
    }
}

// assembleRelease 构建成功后，把版本号 +1 写回 version.properties（供下次构建自动使用）。
tasks.configureEach {
    if (name == "assembleRelease") {
        doLast {
            val props = Properties()
            props.setProperty("versionName", bumpVersionName(buildVersionName))
            versionPropsFile.outputStream().use {
                props.store(it, "Skyadb next build version (auto-increment)")
            }
            println(
                "Version auto-incremented -> next build: " +
                    "${props.getProperty("versionName")} / ${versionCodeFromName(props.getProperty("versionName"))}"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kadb)
    implementation(libs.kadb.mdns)
    implementation(libs.fastboot.java)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
