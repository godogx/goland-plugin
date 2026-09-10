package io.github.godogx.godog

/**
 * Mirrors the JSON shape written by godog's TestSuite.WriteManifest
 * (see run.go: registeredStep / WriteManifest in github.com/cucumber/godog).
 */
data class GodogStep(
    val expr: String,
    val file: String,
    val line: Int,
)

/**
 * The Go test that called WriteManifest - by construction the same one that
 * calls Run for these steps (see run.go: registeredTest), so it's a
 * deterministic target for a "run this feature/scenario" action instead of
 * a guess at which test exercises a given features directory.
 */
data class GodogTest(
    val name: String,
    val file: String,
    val line: Int,
)

/** The on-disk shape of a .godog-gherkin.json dump (see run.go: stepsDump). */
data class GodogStepsDump(
    val steps: List<GodogStep>,
    val test: GodogTest?,
)
