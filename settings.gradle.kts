plugins {
    // Lets Gradle auto-download a matching JDK toolchain (into its own cache, not
    // system-wide) instead of failing when the exact version isn't already installed -
    // needed since the intellij-platform Gradle plugin enforces a toolchain matching
    // whatever JDK the target IDE build actually needs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "goland-plugin"
