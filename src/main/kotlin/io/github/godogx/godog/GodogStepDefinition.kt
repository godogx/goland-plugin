package io.github.godogx.godog

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
) : AbstractStepDefinition(element) {

    // godog steps are plain regex handler funcs, not named/typed parameters
    // Cucumber can introspect - nothing to offer here for now.
    override fun getVariableNames(): List<String> = emptyList()

    override fun getCucumberRegexFromElement(element: PsiElement?): String? = step.expr
}
