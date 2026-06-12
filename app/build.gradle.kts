/**
 * 应用模块构建配置
 *
 * Textediting - Android 文本编辑器
 * 技术栈：Kotlin 2.1 + Jetpack Compose + Material 3
 * 最低支持 Android 7.0 (API 24)，目标 Android 14 (API 34)，编译 SDK 36
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 读取签名密钥属性文件（用于 Release 构建签名）
val keystorePropertiesFile = file("keystore.properties")
val acsProps = if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.readLines()
        .filter { '=' in it }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
} else emptyMap()

android {
    namespace = "com.dlam.textediting"
    compileSdk = 36  // 编译 SDK：Android 16 (Baklava)

    defaultConfig {
        applicationId = "com.dlam.textediting"
        minSdk = 24       // 最低支持：Android 7.0
        targetSdk = 34     // 目标版本：Android 14
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true    // 启用 Jetpack Compose
    }

    // Java 17 编译选项
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        }
    }

    // Release 签名配置（从 keystore.properties 读取）
    signingConfigs {
        maybeCreate("release").apply {
            val sf = acsProps["storeFile"]
            val sp = acsProps["storePassword"]
            val ka = acsProps["keyAlias"]
            val kp = acsProps["keyPassword"]
            if (!sf.isNullOrBlank()) storeFile = file(sf)
            if (!sp.isNullOrBlank()) storePassword = sp
            if (!ka.isNullOrBlank()) keyAlias = ka
            if (!kp.isNullOrBlank()) keyPassword = kp
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    // AndroidX 核心
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))  // Compose BOM 统一版本管理
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)  // Material Icons 扩展库

    // SAF 文件访问
    implementation(libs.androidx.documentfile)

    // 调试工具
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
