#!/usr/bin/env bash
# Builds build/RankedEnhancements-<version>.jar.
# Compiled against the Paper 1.21.1 API (the oldest supported) and checked against the newest one too.
# Needs JDK 25 and the API jars from fetch-libs.sh (the plugin itself runs on Java 21+).
set -euo pipefail
cd "$(dirname "$0")"
VERSION=1.0.0
SRC=$(find src/main/java -name '*.java')
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) S=";" ;; *) S=":" ;; esac
rm -rf build && mkdir -p build/classes build/check
javac --release 21 -Xlint:deprecation -cp "libs/1.21/*${S}libs/common/*" -d build/classes $SRC
if [ -d libs/26.2 ]; then
	javac --release 21 -nowarn -cp "libs/26.2/*${S}libs/common/*" -d build/check $SRC
fi
cp src/main/resources/config.yml build/classes/
sed "s/\${version}/$VERSION/" src/main/resources/plugin.yml > build/classes/plugin.yml
cp LICENSE build/classes/LICENSE
jar --create --file "build/RankedEnhancements-$VERSION.jar" -C build/classes .
echo "built build/RankedEnhancements-$VERSION.jar"
