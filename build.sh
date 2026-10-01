#!/usr/bin/env bash
# Builds the plugin against a local WebStorm install (no Gradle needed). Requires JDK 21+.
#   WEBSTORM     - WebStorm install dir     (default: D:/programs/webstorm)
#   USER_PLUGINS - user plugins dir         (default: $APPDATA/JetBrains/WebStorm2026.2/plugins)
# Plugins updated from Marketplace (deno, javascript-debugger) live in USER_PLUGINS and win over bundled copies.
set -euo pipefail
cd "$(dirname "$0")"

WEBSTORM="${WEBSTORM:-D:/programs/webstorm}"
USER_PLUGINS="${USER_PLUGINS:-$APPDATA/JetBrains/WebStorm2026.2/plugins}"
CP="$USER_PLUGINS/deno/lib/*"
for p in javascript-debugger; do
  CP="$CP;$USER_PLUGINS/$p/lib/*;$USER_PLUGINS/$p/lib/modules/*"
done
CP="$CP;$WEBSTORM/lib/*"
for p in json javascript-plugin javascript-debugger; do
  CP="$CP;$WEBSTORM/plugins/$p/lib/*;$WEBSTORM/plugins/$p/lib/modules/*"
done

VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' src/META-INF/plugin.xml)

rm -rf out dist
mkdir -p out dist/deno-tasks/lib
javac --release 21 -Xlint:deprecation -encoding UTF-8 -cp "$CP" -d out $(find src -name '*.java')
cp -r src/META-INF out/
jar --create --file dist/deno-tasks/lib/deno-tasks.jar -C out .
# Installable via Settings | Plugins | gear | Install Plugin from Disk
jar --create --no-manifest --file "dist/webstorm-deno-tasks-$VERSION.zip" -C dist deno-tasks
echo "Built dist/webstorm-deno-tasks-$VERSION.zip"
