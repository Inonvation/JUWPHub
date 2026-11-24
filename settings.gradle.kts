pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "JUWPHub"
include(":app")
// iOS 移植 P0（2026-11-24）：跨平台共享模块。域层先搬进来，UI 随后。
include(":shared")
