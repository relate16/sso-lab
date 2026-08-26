plugins {
    base
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.ssolab"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val backendServicePaths = setOf(
    ":backend:auth-server",
    ":backend:admin-server",
    ":backend:hr-server",
    ":backend:approval-server",
)

val backendLibraryPaths = setOf(
    ":backend:shared-infrastructure",
)

subprojects {
    if (path in backendLibraryPaths) {
        apply(plugin = "java-library")
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
        return@subprojects
    }

    if (path !in backendServicePaths) {
        return@subprojects
    }

    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
        }
    }
}
