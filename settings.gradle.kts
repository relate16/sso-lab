pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sso-lab"

include(
    "backend:auth-server",
    "backend:admin-server",
    "backend:hr-server",
    "backend:approval-server",
    "backend:shared-infrastructure",
)
