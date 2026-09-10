import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Machine-local override, e.g. "localIdePath=/Applications/GoLand.app", to build
// against an already-installed IDE instead of downloading one - gitignored, and
// deliberately a project-root file rather than ~/.gradle/gradle.properties: that
// file is shared by every Gradle project on the machine, so a generic property
// name there collides across projects. See local.properties.example.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

dependencies {
    intellijPlatform {
        val localIdePath = localProperties.getProperty("localIdePath")
        if (!localIdePath.isNullOrBlank()) {
            local(localIdePath)
        } else {
            goland(providers.gradleProperty("platformVersion"))
        }

        // Go PSI (GoFunctionDeclaration, GoMethodDeclaration, ...).
        bundledPlugin("org.jetbrains.plugins.go")

        // GoTestRunConfiguration extends SMRunnerConsolePropertiesProvider, which as of the
        // 2026.2-era platform module split lives in its own bundled plugin rather than coming
        // in transitively with org.jetbrains.plugins.go.
        bundledPlugin("intellij.testRunner.plugin")

        // Gherkin/Cucumber support: CucumberJvmExtensionPoint, AbstractStepDefinition, GherkinFile.
        // NOT bundled with GoLand - it's a separate Marketplace plugin (id "gherkin") users
        // install themselves, so it needs an explicit version, not bundledPlugin(). Pin it to
        // whatever's actually installed locally (Help > About, or the plugin's own
        // META-INF/plugin.xml under GoLand's user plugins dir) rather than guessing.
        //
        // CucumberJvmExtensionPoint's ABI has moved twice since 253.28294.218 (the version
        // this was first built against): isStepLikeFile/isWritableStepLikeFile dropped their
        // `parent` param and the 2-arg overloads were deleted outright (JetBrains/intellij-
        // plugins@43a83686, @9097de46), and loadStepsFor(PsiFile?, Module) was replaced by
        // loadStepsFor(Module) (@a52500cb). GodogCucumberExtension implements both old and new
        // shapes so one build satisfies whichever ABI is actually loaded at runtime - compile
        // against the newest available so the new-shape overrides are checked by the compiler;
        // the old-shape methods are plain (non-override) methods matching the deleted
        // signatures, so they still exist in the .class file for older gherkin installs.
        plugin("gherkin", "262.8665.173")

        pluginVerifier()
        zipSigner()
    }

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

// Not jvmToolchain(21): that requires an actual JDK 21 install. Compile with
// whatever JDK runs Gradle, just target the bytecode level GoLand's runtime
// (JBR 21) needs.
//
// Not the `kotlin { compilerOptions {} }` project-level DSL either: the
// intellij-platform Gradle plugin registers its own tasks.withType<KotlinCompile>
// .configureEach { jvmTarget.convention(...) } targeting the IDE's own required
// Java language version (e.g. 25 for a 2026.2 target) - and since the project-level
// DSL is itself just another convention on the same per-task property, whichever
// one's callback runs last during task realization wins, which isn't reliably us.
// A real per-task jvmTarget.set() always beats any convention regardless of order.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)

    // GoLand's own platform modules (fleet.*) are written in Kotlin and land on our compile
    // classpath; they're built with a newer Kotlin compiler than this project's, so their
    // .kotlin_module metadata version trips a hard compile error otherwise. We never read
    // Kotlin-specific metadata from them (just plain JVM classes/methods), so skipping the
    // version check is safe - this is JetBrains' own suggested workaround for the error.
    compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

intellijPlatform {
    pluginConfiguration {
        // id/name/version come from plugin.xml - don't duplicate/override them
        // here, that's what clobbered the friendly name with gradle.properties'
        // pluginName ("godog-intellij-plugin") on the first build.
        //
        // 253 = GoLand 2025.3, the version this plugin was first built and tested
        // against; verified working through 262 (2026.2) too via the dual-ABI
        // handling in GodogCucumberExtension, but 253 is the actual floor we've
        // tested, not a guess.
        ideaVersion {
            sinceBuild = "253"
        }
    }
}
