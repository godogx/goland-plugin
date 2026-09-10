package io.github.godogx.godog

import com.goide.GoFileType
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.cucumber.BDDFrameworkType
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * Registers godog (Go) as a Cucumber step-definition source, backed by the
 * .godog-gherkin.json index produced by TestSuite.WriteManifest
 * (see github.com/cucumber/godog).
 */
class GodogCucumberExtension : CucumberJvmExtensionPoint {

    // CucumberJvmExtensionPoint's ABI has moved: isStepLikeFile/isWritableStepLikeFile used to
    // take a second `parent` param, and gherkin builds through ~253.28294.218 (GoLand 2025.3)
    // still require that 2-arg shape as abstract. Since JetBrains/intellij-plugins@43a83686 the
    // `parent` param is dropped (unused there too), and @9097de46 deleted the 2-arg overloads
    // entirely - so a build compiled only against the new 1-arg shape throws
    // AbstractMethodError on an older gherkin install expecting the 2-arg one, and vice versa.
    // Implementing both - the current shape as a real `override`, the deleted one as a plain
    // method with a matching JVM signature - satisfies whichever ABI is actually loaded, since
    // interface satisfaction at class-link time only checks for a matching method signature,
    // not whether our source called it an override.
    override fun isStepLikeFile(child: PsiElement): Boolean =
        child is PsiFile && child.fileType == GoFileType.INSTANCE

    override fun isWritableStepLikeFile(child: PsiElement): Boolean = isStepLikeFile(child)

    @Suppress("unused")
    fun isStepLikeFile(child: PsiElement, parent: PsiElement): Boolean = isStepLikeFile(child)

    @Suppress("unused")
    fun isWritableStepLikeFile(child: PsiElement, parent: PsiElement): Boolean = isWritableStepLikeFile(child)

    override fun getStepFileType(): BDDFrameworkType = BDDFrameworkType(GoFileType.INSTANCE)

    override fun getStepDefinitionCreator(): StepDefinitionCreator = GodogStepDefinitionCreator

    // Same story as isStepLikeFile above: loadStepsFor(PsiFile?, Module) was replaced by
    // loadStepsFor(Module) (JetBrains/intellij-plugins@a52500cb) - featureFile was always
    // unused here anyway, we resolve every .godog-gherkin.json reachable from the module
    // regardless of which feature file asked.
    override fun loadStepsFor(module: Module): List<AbstractStepDefinition> =
        GodogStepsIndex.loadStepsFor(module)

    // Unlike isStepLikeFile/isWritableStepLikeFile above, this 2-arg overload is still present
    // as a deprecated default method rather than deleted (as of 262.8665.173) - Kotlin requires
    // `override` for it here, but it's harmless either way: a real method with this exact JVM
    // signature exists in the .class file regardless, which is all an older gherkin needs.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun loadStepsFor(featureFile: PsiFile?, module: Module): List<AbstractStepDefinition> =
        loadStepsFor(module)

    override fun getStepDefinitionContainers(file: GherkinFile): Collection<PsiFile> {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return emptyList()
        val project = module.project

        return project.service<GodogStepsFileTracker>()
            .filesFor(module)
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }
    }
}

/**
 * Steps are registered from Go code at runtime (see godog's ScenarioContext.Step),
 * not created by templated codegen the way JVM annotation-based frameworks are, so
 * "Create step definition" isn't wired up here yet - createStepDefinition() keeps
 * its CucumberJvmExtensionPoint default of returning false.
 */
private object GodogStepDefinitionCreator : StepDefinitionCreator {
    override fun createStepDefinitionContainer(dir: PsiDirectory, name: String): PsiFile {
        throw UnsupportedOperationException("creating godog step definitions is not supported yet")
    }

    override fun getDefaultStepDefinitionFolderPath(step: GherkinStep): String =
        step.containingFile?.containingDirectory?.virtualFile?.path ?: ""

    override fun getStepDefinitionFilePath(file: PsiFile): String = file.virtualFile?.path ?: file.name

    override fun getDefaultStepFileName(step: GherkinStep): String = "steps.go"
}
