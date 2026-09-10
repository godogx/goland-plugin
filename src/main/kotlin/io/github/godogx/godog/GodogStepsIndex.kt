package io.github.godogx.godog

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/** The Go test function that dumped a given .godog-gherkin.json, resolved to real PSI. */
data class ResolvedGodogTest(val name: String, val element: PsiElement)

private data class ParsedGodogFile(
    val steps: List<AbstractStepDefinition>,
    val test: ResolvedGodogTest?,
)

/**
 * Loads and caches godog step definitions (and the test that produced them),
 * from every .godog-gherkin.json found in a module's content (see GodogStep
 * / WriteManifest in github.com/cucumber/godog).
 *
 * File discovery is [GodogStepsFileTracker]'s job (VFS-event based, not a
 * directory walk per call). Each JSON file is parsed+resolved once and
 * cached keyed on that file's own PSI, so editing unrelated files elsewhere
 * in a large project doesn't force a reparse/re-resolve here.
 */
object GodogStepsIndex {

    fun loadStepsFor(module: Module): List<AbstractStepDefinition> {
        val project = module.project

        return project.service<GodogStepsFileTracker>()
            .filesFor(module)
            .flatMap { parseFile(project, it).steps }
    }

    /**
     * The test that produced the .godog-gherkin.json governing [featureFile], i.e. the
     * deterministic "what do I run for this feature" anchor for a run/debug action - the
     * nearest such file whose directory is an ancestor of featureFile (matching the
     * convention of a features/ folder living next to the test file that dumps for it).
     */
    fun testLocationFor(project: Project, featureFile: VirtualFile): ResolvedGodogTest? {
        val module = ModuleUtilCore.findModuleForFile(featureFile, project) ?: return null
        val tracker = project.service<GodogStepsFileTracker>()

        val jsonFile = tracker.filesFor(module)
            .filter { jsonFile -> jsonFile.parent?.let { VfsUtilCore.isAncestor(it, featureFile, false) } == true }
            .maxByOrNull { it.parent!!.path.length }
            ?: return null

        return parseFile(project, jsonFile).test
    }

    private fun parseFile(project: Project, vFile: VirtualFile): ParsedGodogFile {
        val manifestDir = vFile.parent ?: return ParsedGodogFile(emptyList(), null)
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return ParsedGodogFile(emptyList(), null)

        return CachedValuesManager.getManager(project).getCachedValue(psiFile) {
            val text = String(vFile.contentsToByteArray(), Charsets.UTF_8)

            val dump = runCatching {
                GodogStepJson.parse(text)
            }.getOrElse {
                thisLogger().warn("Failed to parse ${vFile.path}", it)
                GodogStepsDump(emptyList(), null)
            }

            val steps = dump.steps.mapNotNull { step ->
                val element = resolveElement(project, manifestDir, step.defFile, step.defLine)
                if (element == null) {
                    thisLogger().warn("Could not resolve element for step '${step.expr}' at ${step.defFile}:${step.defLine}")
                    return@mapNotNull null
                }
                GodogStepDefinition(element, step)
            }

            val test = dump.test?.let { t ->
                resolveElement(project, manifestDir, t.file, t.line)?.let { ResolvedGodogTest(t.name, it) }
            }

            thisLogger().info(
                "godog steps from ${vFile.path}: parsed ${dump.steps.size}, resolved ${steps.size}, " +
                    "test=${dump.test?.name} resolved=${test != null}"
            )

            // Depend on this JSON file's own PSI: it's what defines validity of everything we
            // just resolved (defFile/defLine are only meaningful relative to a given dump), and
            // godog rewrites the whole file on every WriteManifest call rather than patching it.
            CachedValueProvider.Result.create(ParsedGodogFile(steps, test), psiFile)
        }
    }

    /**
     * Resolves a 1-based file:line to the first real (non-whitespace) token on that line.
     * file is whatever form portablePath (run.go) wrote - see [GodogPathResolver].
     *
     * Used to be "walk up to the enclosing func/method declaration", which was fine while
     * DefLine only ever pointed at a function's own header line (registering a plain
     * top-level function as a step handler) - but DefLine can now also be a step's
     * registration call site inside some other function (the bound-method-value fallback in
     * godog's test_context.go), and walking up there jumped past that line to the whole
     * enclosing function. Landing on the line itself works for both.
     */
    private fun resolveElement(project: Project, manifestDir: VirtualFile, file: String, line: Int): PsiElement? {
        val vFile = project.service<GodogPathResolver>().resolve(manifestDir, file) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null

        val lineIndex = (line - 1).coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        val offset = document.getLineStartOffset(lineIndex)
        var element = psiFile.findElementAt(offset) ?: return null

        // findElementAt(lineStart) usually lands on leading indentation; skip forward to the
        // first real token so navigation lands on the code, not column 0.
        while (element is PsiWhiteSpace) {
            element = PsiTreeUtil.nextLeaf(element) ?: return null
        }

        return element
    }
}
