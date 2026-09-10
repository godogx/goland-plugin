#!/usr/bin/env bash
# Bootstraps the Gradle wrapper (needs network once) if missing, then runs
# the given Gradle task(s) - defaults to `runIde` (launches a sandboxed
# GoLand, using the local install pinned in gradle.properties).
#
# Usage:
#   ./build.sh              # runIde
#   ./build.sh buildPlugin  # produce build/distributions/*.zip

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ ! -x ./gradlew ]; then
    echo "No Gradle wrapper yet, bootstrapping one..."

    bootstrap_gradle=""
    if command -v gradle >/dev/null 2>&1; then
        bootstrap_gradle="gradle"
    else
        # Reuse a Gradle distribution already cached by wrapper usage elsewhere
        # on this machine, so we don't need `gradle` on PATH just to bootstrap.
        cached=$(find "$HOME/.gradle/wrapper/dists" -maxdepth 6 -type f -name gradle -path '*/bin/gradle' 2>/dev/null | sort -V | tail -1)
        if [ -n "$cached" ]; then
            bootstrap_gradle="$cached"
        fi
    fi

    if [ -z "$bootstrap_gradle" ]; then
        echo "error: no 'gradle' on PATH and no cached wrapper distribution found under ~/.gradle/wrapper/dists" >&2
        echo "install one first, e.g.: brew install gradle" >&2
        exit 1
    fi

    # 8.13 (platform-gradle-plugin's stated minimum) doesn't fully support
    # JDK 24+'s bytecode and fails with obscure generics errors; use a newer
    # Gradle that does.
    "$bootstrap_gradle" wrapper --gradle-version 9.5.0 --distribution-type bin
fi

./gradlew "${@:-runIde}"
