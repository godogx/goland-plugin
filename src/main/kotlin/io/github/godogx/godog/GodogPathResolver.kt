package io.github.godogx.godog

import com.goide.sdk.GoSdkService
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

private const val MOD_PATH_PREFIX = "<MOD_PATH>/"

/**
 * Resolves a path string from a .godog-gherkin.json manifest to a real file on this
 * machine. Manifests use three forms so they survive being committed and checked out
 * elsewhere (see run.go: portablePath):
 *  - a path relative to the manifest's own directory (the common case: the module
 *    under test, including a vendor/ tree)
 *  - "<MOD_PATH>/<escaped-module-path>@<version>/rest..." for a dependency living in
 *    the (machine-specific) Go module cache - GOMODCACHE substitutes for the
 *    placeholder, and the rest is already the exact on-disk directory name Go uses,
 *    identical on every machine
 *  - a plain absolute path (older manifests, or a manifest that was never meant to
 *    be portable in the first place)
 */
@Service(Service.Level.PROJECT)
class GodogPathResolver(private val project: Project) {

    @Volatile private var cachedGoModCache: String? = null

    fun resolve(manifestDir: VirtualFile, path: String): VirtualFile? = when {
        path.startsWith(MOD_PATH_PREFIX) -> {
            val cache = goModCache()
            if (cache == null) null else LocalFileSystem.getInstance().findFileByPath("$cache/${path.removePrefix(MOD_PATH_PREFIX)}")
        }
        Paths.get(path).isAbsolute -> LocalFileSystem.getInstance().findFileByPath(path)
        else -> VfsUtilCore.findRelativeFile(path, manifestDir)
    }

    private fun goModCache(): String? {
        cachedGoModCache?.let { return it }

        val go = goExecutablePath() ?: return null

        return runCatching {
            val process = ProcessBuilder(go, "env", "GOMODCACHE").start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            output.ifEmpty { null }
        }.onFailure {
            thisLogger().warn("Could not run `$go env GOMODCACHE`", it)
        }.getOrNull()?.also { cachedGoModCache = it }
    }

    /**
     * GoLand already knows where the configured Go SDK lives per-project; use that instead of
     * hoping "go" is on PATH - a GUI-launched IDE on macOS often doesn't inherit the shell's
     * PATH (e.g. Homebrew's /opt/homebrew/bin), so a bare "go" via ProcessBuilder can fail even
     * though `go` works fine from a terminal.
     */
    private fun goExecutablePath(): String? {
        val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: return null
        return GoSdkService.getInstance(project).getSdk(module)?.executable?.path
    }
}
