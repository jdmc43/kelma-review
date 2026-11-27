#!/usr/bin/env bash
# Builds kelma-hello.jar. The Kelma Review API (tech.kelma.plugin) comes from the app at run time,
# so compile against it without bundling it: by default the shared JVM jar built from this repo
# (./gradlew :shared:jvmJar), or set KELMA_API_JAR to the shared-jvm jar of an installed app.
set -euo pipefail
cd "$(dirname "$0")"
api_jar="${KELMA_API_JAR:-$(ls ../../shared/build/libs/shared-jvm*.jar 2>/dev/null | head -1)}"
[ -f "$api_jar" ] || { echo "Build the API first: ./gradlew :shared:jvmJar (or set KELMA_API_JAR)" >&2; exit 1; }
rm -rf build && mkdir -p build/classes
javac --release 17 -cp "$api_jar" -d build/classes $(find src -name '*.java')
cp kelma-plugin.json LICENSE NOTICE build/classes/
jar --create --file build/kelma-hello.jar -C build/classes .
echo "Built $(pwd)/build/kelma-hello.jar"
