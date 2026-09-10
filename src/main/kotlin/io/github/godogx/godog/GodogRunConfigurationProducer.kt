package io.github.godogx.godog

import com.goide.execution.GoBuildingRunConfiguration
import com.goide.execution.testing.GoTestRunConfiguration
import com.goide.execution.testing.GoTestRunConfigurationType
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import java.nio.file.Paths

/**
 * "Run"/"Debug" for a Gherkin Feature or Scenario in a godog project: builds
 * a Go test run configuration (godog is just a regular `go test`) targeting
 * the test that WriteManifest recorded as its caller (see GodogStepsIndex,
 * GodogTest) - deterministic, not a guess at which test exercises a given
 * features directory.
 *
 * Scenario Outline/Examples rows and Rule blocks aren't handled yet
 * (produces nothing for those - same as clicking Run got before this).
 */
class GodogRunConfigurationProducer : LazyRunConfigurationProducer<GoTestRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory = GoTestRunConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: GoTestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val target = findTarget(context) ?: return false

        configuration.name = target.name
        configuration.kind = GoBuildingRunConfiguration.Kind.DIRECTORY
        configuration.directoryPath = target.testDirectory
        configuration.workingDirectory = target.testDirectory
        configuration.pattern = target.testPattern
        configuration.setParams(target.godogArgs)

        sourceElement.set(target.sourceElement)
        return true
    }

    override fun isConfigurationFromContext(configuration: GoTestRunConfiguration, context: ConfigurationContext): Boolean {
        val target = findTarget(context) ?: return false

        return configuration.kind == GoBuildingRunConfiguration.Kind.DIRECTORY &&
            configuration.directoryPath == target.testDirectory &&
            configuration.pattern == target.testPattern &&
            configuration.params == target.godogArgs
    }

    private fun findTarget(context: ConfigurationContext): RunTarget? {
        val element = context.psiLocation ?: return null
        val featureFile = element.containingFile as? GherkinFile ?: return null
        val vFeatureFile = featureFile.virtualFile ?: return null

        val test = GodogStepsIndex.testLocationFor(context.project, vFeatureFile) ?: return null
        val testDirectory = test.element.containingFile?.virtualFile?.parent?.path ?: return null

        val featureRelativePath = Paths.get(testDirectory).relativize(Paths.get(vFeatureFile.path)).toString()

        val scenario = PsiTreeUtil.getNonStrictParentOfType(element, GherkinStepsHolder::class.java)
        val (godogPath, sourceElement) = when {
            scenario is GherkinScenario && !scenario.isBackground -> {
                val line = lineOf(scenario) ?: return null
                "$featureRelativePath:$line" to scenario
            }
            PsiTreeUtil.getNonStrictParentOfType(element, GherkinFeature::class.java) != null ->
                featureRelativePath to featureFile
            else -> return null
        }

        return RunTarget(
            name = "godog: ${featureFile.name}",
            testDirectory = testDirectory,
            testPattern = "^${test.name}$",
            godogArgs = "-godog.paths=$godogPath",
            sourceElement = sourceElement,
        )
    }

    private fun lineOf(element: PsiElement): Int? {
        val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    private data class RunTarget(
        val name: String,
        val testDirectory: String,
        val testPattern: String,
        val godogArgs: String,
        val sourceElement: PsiElement,
    )
}
