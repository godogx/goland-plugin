# goland-plugin

GoLand support for resolving Gherkin steps in `.feature` files to their
[godog](https://github.com/cucumber/godog) (Go) step definitions.

**Status: experimental.** Not on the JetBrains Marketplace - install the
built zip manually (Settings > Plugins > gear icon > Install Plugin from
Disk) from a [release](https://github.com/godogx/goland-plugin/releases) or
a local build. Early/advanced users only for now.

## How it works

godog's `TestSuite.WriteManifest(dir)` runs a suite's initializers for real
and writes every registered step (regex, keyword, call site, and the step
handler's own definition site) - plus which test called WriteManifest - to
`<dir>/.godog-gherkin.json`: see `run.go` in cucumber/godog. This plugin
reads that file instead of statically parsing Go source, which is what
makes it work even when steps are registered through helper functions or
come from third-party Go modules: godog already resolved all of that by
actually running the code.

Convention: call `WriteManifest(".")` directly inside the test function
that also calls `Run()` (not through a helper - `WriteManifest` records its
immediate caller as "the test that runs these scenarios", and that's only
meaningful if it's actually the test), let it regenerate on every local
`go test` run.

Paths inside the manifest are portable, so the file can be gitignored (the
simplest option - regenerates every run, never goes stale) or committed
(so CI/teammates get resolution without running the suite first): a file
in the module under test (including its `vendor/` tree) is written
relative to the manifest's own directory, and a file in a dependency
that's genuinely off in the Go module cache is written as
`<MOD_PATH>/<module>@<version>/...`, which a reader resolves against its
own `GOMODCACHE` - see `portablePath` in `run.go`.

## Status

Step resolution (kills false "undefined step reference" warnings,
go-to-definition into the real Go step function), and a Run/Debug gutter
icon for a Feature or a plain Scenario (built on the deterministic test
location WriteManifest records - see `GodogRunConfigurationProducer`). Not yet
handled: Scenario Outline/Examples rows, Rule blocks, and "Create step
definition" quick-fix (the extension point's default no-op is used for
that last one).

## Build

Requires a pinned GoLand build in `gradle.properties` (`platformVersion`).

```
./gradlew buildPlugin   # -> build/distributions/*.zip, install via Settings > Plugins > Install from disk
./gradlew runIde        # launch a sandboxed GoLand with the plugin loaded
./gradlew verifyPlugin  # checks binary compatibility against the declared since-build/until-build range
```

Tested against GoLand 2025.3 (gherkin 253.28294.218) and 2026.2 (gherkin
262.8665.173) - `CucumberJvmExtensionPoint`'s ABI changed between those, and
`GodogCucumberExtension` implements both the old and new method shapes so a
single build works against either (see the comment on the `gherkin` version
pin in `build.gradle.kts`). If `gherkin` fails to load on some other version,
that interface is the first place to check.
