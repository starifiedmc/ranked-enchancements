#!/usr/bin/env bash
# Downloads the compile-only jars into libs/ (none of them end up in the plugin jar).
#   libs/1.21    Paper 1.21.1 API, compiled against (the oldest supported version)
#   libs/26.2    Paper 26.2 API, a second compile to catch API changes
#   libs/common  PacketEvents API (fast-crystals)
set -euo pipefail
cd "$(dirname "$0")" && mkdir -p libs/1.21 libs/26.2 libs/common
P=https://repo.papermc.io/repository/maven-public
M=https://repo1.maven.org/maven2
C=https://repo.codemc.io/repository/maven-releases
get() { curl -sfo "$1" "$2" || { echo "failed: $2"; exit 1; }; }

cd libs/1.21
get paper-api.jar "$P/io/papermc/paper/paper-api/1.21.1-R0.1-SNAPSHOT/paper-api-1.21.1-R0.1-20250328.161643-128.jar"
get bungeecord-chat.jar "$P/net/md-5/bungeecord-chat/1.20-R0.2-deprecated+build.18/bungeecord-chat-1.20-R0.2-deprecated+build.18.jar"
get adventure-api.jar "$M/net/kyori/adventure-api/4.17.0/adventure-api-4.17.0.jar"
get adventure-key.jar "$M/net/kyori/adventure-key/4.17.0/adventure-key-4.17.0.jar"
get examination-api.jar "$M/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"
get gson.jar "$M/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
get guava.jar "$M/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar"
get joml.jar "$M/org/joml/joml/1.10.5/joml-1.10.5.jar"
get jspecify.jar "$M/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"
get annotations.jar "$M/org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar"

cd ../26.2
V=26.2.build.129-stable
get paper-api.jar "$P/io/papermc/paper/paper-api/$V/paper-api-$V.jar"
get bungeecord-chat.jar "$P/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar"
get adventure-api.jar "$M/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar"
get adventure-key.jar "$M/net/kyori/adventure-key/5.2.0/adventure-key-5.2.0.jar"
get examination-api.jar "$M/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"
get gson.jar "$M/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
get guava.jar "$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar"
get jspecify.jar "$M/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"
get annotations.jar "$M/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar"

cd ../common
for a in packetevents-api packetevents-spigot; do get $a.jar "$C/com/github/retrooper/$a/2.14.0/$a-2.14.0.jar"; done
echo "libs ready"
