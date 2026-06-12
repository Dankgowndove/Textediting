/**
 * 根构建脚本
 *
 * 声明所有子项目共用的 Gradle 插件（使用 apply false 仅声明不应用）。
 * 包含 clean 任务用于清理所有构建产物。
 */

plugins {
    // 插件声明（apply false = 仅声明，由子模块按需应用）
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 清理任务：删除根项目的 build 目录
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
