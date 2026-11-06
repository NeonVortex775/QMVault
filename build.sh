#!/usr/bin/env bash
# Plain javac + jar. No Maven, no Gradle, nothing to download.
#
#   bash build.sh              build only
#   bash build.sh install      build and copy into $MC_SERVER/plugins
#
# Dependencies (see lib/README.md):
    lib/spigot-api.jar     required
    lib/dragoncore.jar     required
set -e
cd "$(dirname "$0")"

JAR_NAME="QingMingVault.jar"
OUT="build"

for f in spigot-api.jar dragoncore.jar; do
    [ -f "lib/$f" ] || { echo "missing dependency: lib/$f"; exit 1; }
done

rm -rf "$OUT/classes"
mkdir -p "$OUT/classes"

find src/main/java -name '*.java' | sort > "$OUT/sources.txt"
echo "compiling $(wc -l < "$OUT/sources.txt") files"

javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
      -cp "lib/*" -d "$OUT/classes" @"$OUT/sources.txt"

# Bundle the SQLite driver into the jar so a fresh install does not need it
# dropped into plugins/ separately.
SQLITE="lib/sqlite-jdbc-3.36.0.3.jar"
if [ -f "$SQLITE" ]; then
    rm -rf build/deps && mkdir -p build/deps
    unzip -q -o "$SQLITE" -d build/deps
    rm -f build/deps/META-INF/MANIFEST.MF build/deps/META-INF/*.SF \
          build/deps/META-INF/*.DSA build/deps/META-INF/*.RSA
    for p in FreeBSD DragonFlyBSD Linux-Alpine; do
        rm -rf "build/deps/org/sqlite/native/$p"
    done
    for p in android-arm arm armv6 armv7 ppc64 x86; do
        rm -rf "build/deps/org/sqlite/native/Linux/$p"
    done
    rm -rf build/deps/org/sqlite/native/Windows/armv7 \
           build/deps/org/sqlite/native/Windows/aarch64
    cp -rf build/deps/. build/classes/
    echo "      sqlite driver bundled"
else
    echo "      [!] $SQLITE missing - storage.type: sqlite will not work"
fi

cp -f src/main/resources/*.yml "$OUT/classes/" 2>/dev/null || true
for d in shops gui tables; do
    if [ -d "src/main/resources/$d" ]; then
        mkdir -p "$OUT/classes/$d"
        cp -f src/main/resources/$d/* "$OUT/classes/$d/" 2>/dev/null || true
    fi
done

rm -f "$OUT/$JAR_NAME"
( cd "$OUT/classes" && jar cfe "../$JAR_NAME" com.qingming.vault.VaultPlugin . )
echo "built build/$JAR_NAME"
ls -lh "$OUT/$JAR_NAME"

if [ "$1" = "install" ]; then
    SERVER="${MC_SERVER:-}"
    [ -n "$SERVER" ] || { echo "MC_SERVER is not set"; exit 1; }
    # Overwriting a jar while the server is up leaves the old classes already
    # loaded and breaks every class that was not touched yet. Write .new instead.
    if tasklist 2>/dev/null | grep -qiE "(^|[[:space:]])java(w)?\.exe[[:space:]]"; then
        cp -f "$OUT/$JAR_NAME" "$SERVER/plugins/$JAR_NAME.new"
        echo "server is running - wrote plugins/$JAR_NAME.new, move it after shutdown"
    else
        cp -f "$OUT/$JAR_NAME" "$SERVER/plugins/$JAR_NAME"
        echo "copied to $SERVER/plugins/$JAR_NAME"
    fi
fi
