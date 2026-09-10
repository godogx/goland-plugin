package io.github.godogx.godog

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * A step definition backed by godog's runtime step discovery ([GodogStep]),
 * anchored on the real Go PSI element (function/method) that implements it
 * where that could be resolved, so navigation lands in actual source.
 */
class GodogStepDefinition(
    element: PsiElement,
    private val step: GodogStep,
    private val manifestDir: VirtualFile,
) : AbstractStepDefinition(element) {

    // godog steps are plain regex handler funcs, not named/typed parameters
    // Cucumber can introspect - nothing to offer here for now.
    override fun getVariableNames(): List<String> = emptyList()

    override fun getCucumberRegexFromElement(element: PsiElement?): String? = step.expr

    // loadStepsFor(Module) is module-wide, not per-feature-file, so a monorepo of several
    // independent godog projects sharing one IntelliJ module (e.g. godog's own _examples/*)
    // would otherwise offer every manifest's steps to every feature file - a step in one
    // example matching, by sheer regex coincidence, a same-looking step registered for a
    // completely unrelated example elsewhere in the module. Only apply where this step's own
    // manifest is actually an ancestor of the .feature file being checked.
    override fun supportsStep(element: PsiElement): Boolean {
        val featureFile = element.containingFile?.virtualFile ?: return false
        return VfsUtilCore.isAncestor(manifestDir, featureFile, false)
    }
}
