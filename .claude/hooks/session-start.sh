#!/bin/bash
# Warm the Maven cache before a Claude Code on the web session starts.
#
# A web session gets a fresh container with an empty ~/.m2, so the first `mvn test` spends
# several minutes downloading the build before it runs a single test. That download is what
# this hook moves to session start, where the container image caches it afterwards.
#
# A local checkout already has a warm ~/.m2 and gains nothing from running Maven on every
# session start, so this does nothing outside the remote environment.

set -uo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
	exit 0
fi

# Claude Code sets CLAUDE_PROJECT_DIR when it runs the hook. Falling back to this script's
# own location keeps it runnable by hand, which is the only way to test a change to it.
cd "${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}" || exit 0

# org.spigotmc:spigot-api is published only to hub.spigotmc.org and is on no mirror --
# not Maven Central, not Sonatype -- so the whole build needs that one host. When the
# environment's network policy does not allow it, every Maven goal here fails on the same
# artifact, and the session is far easier to work in knowing that up front than discovering
# it later on a `mvn test` that cannot run.
#
# Deliberately exit 0 either way. A cold cache makes a session slow, not broken, and a hook
# that fails session start over it would be the worse outcome.
if ! mvn --batch-mode --no-transfer-progress --quiet dependency:go-offline; then
	echo "Maven could not resolve this project's dependencies, so 'mvn test' will not run here." >&2
	echo "The build needs hub.spigotmc.org, which serves org.spigotmc:spigot-api and no other" >&2
	echo "repository carries. Allow that host in the environment's network policy." >&2
	echo "Building against Paper (-Dpaper.api.version) also needs repo.papermc.io, and Purpur repo.purpurmc.org." >&2
	exit 0
fi

# Compiling the tests proves the toolchain end to end -- the API jar really resolved, and
# both source trees really build against it -- and leaves target/ populated, so the first
# real `mvn test` only has to run the tests.
if ! mvn --batch-mode --no-transfer-progress --quiet test-compile; then
	echo "Dependencies resolved but 'mvn test-compile' failed; the checkout may not build." >&2
	exit 0
fi
