/**
 * Gradle 项目设置
 *
 * 配置插件和依赖仓库源，声明项目名称和子模块。
 */

// 插件管理：配置插件查找仓库
pluginManagement {
    repositories {
        google()             // Google 官方仓库（Android 插件）
        mavenCentral()       // Maven 中央仓库
        gradlePluginPortal() // Gradle 插件门户
    }
}

// 依赖解析管理
dependencyResolutionManagement {
    // 强制使用项目级仓库配置，禁止子模块单独声明仓库
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 根项目名称
rootProject.name = "Textediting"

// 包含 app 子模块
include(":app")
