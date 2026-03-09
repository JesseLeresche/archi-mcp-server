#!/usr/bin/env bash
#
# Installs ALL OSGi bundles from a local Archi installation into your local
# Maven repository so that Maven Tycho can resolve them via pomDependencies=consider.
#
# This covers Archi's own bundles, its custom GEF/Draw2d, and any other
# transitive OSGi dependencies that aren't in the Eclipse p2 repository.
#
# Usage:
#   ./scripts/setup-target-platform.sh /path/to/Archi
#
# Examples:
#   macOS:   ./scripts/setup-target-platform.sh /Applications/Archi.app
#   Linux:   ./scripts/setup-target-platform.sh /opt/Archi
#   Windows: bash scripts/setup-target-platform.sh "C:/Program Files/Archi"
#

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <path-to-archi-installation>"
    echo ""
    echo "Examples:"
    echo "  macOS:   $0 /Applications/Archi.app"
    echo "  Linux:   $0 /opt/Archi"
    echo "  Windows: $0 'C:/Program Files/Archi'"
    exit 1
fi

ARCHI_DIR="$1"

# Find the plugins directory
if [ -d "$ARCHI_DIR/plugins" ]; then
    PLUGINS_DIR="$ARCHI_DIR/plugins"
elif [ -d "$ARCHI_DIR/Contents/Eclipse/plugins" ]; then
    PLUGINS_DIR="$ARCHI_DIR/Contents/Eclipse/plugins"
else
    echo "ERROR: Cannot find plugins/ directory under $ARCHI_DIR"
    echo "  Tried: $ARCHI_DIR/plugins"
    echo "  Tried: $ARCHI_DIR/Contents/Eclipse/plugins"
    exit 1
fi

echo "Archi plugins directory: $PLUGINS_DIR"
echo ""

# Extract Bundle-SymbolicName from a MANIFEST.MF file
# Returns empty string if not found
get_bundle_symbolic_name() {
    local manifest="$1"
    # Bundle-SymbolicName may have directives like ;singleton:=true — strip them
    grep -m1 "^Bundle-SymbolicName:" "$manifest" 2>/dev/null \
        | sed 's/Bundle-SymbolicName: *//' \
        | sed 's/;.*//' \
        | tr -d '[:space:]'
}

INSTALLED=0
SKIPPED=0

for entry in "$PLUGINS_DIR"/*; do
    basename="$(basename "$entry")"

    # Skip non-bundle entries
    if [[ "$basename" == *.source_* ]] || [[ "$basename" == *.feature_* ]]; then
        continue
    fi

    jar_file=""
    manifest_file=""

    if [ -d "$entry" ]; then
        # Directory-shaped bundle
        manifest_file="$entry/META-INF/MANIFEST.MF"
        if [ ! -f "$manifest_file" ]; then
            SKIPPED=$((SKIPPED + 1))
            continue
        fi
    elif [[ "$entry" == *.jar ]]; then
        # JAR bundle — extract manifest to temp
        manifest_file="/tmp/_archi_manifest_$$.MF"
        unzip -p "$entry" META-INF/MANIFEST.MF > "$manifest_file" 2>/dev/null || {
            rm -f "$manifest_file"
            SKIPPED=$((SKIPPED + 1))
            continue
        }
        jar_file="$entry"
    else
        continue
    fi

    bsn="$(get_bundle_symbolic_name "$manifest_file")"

    # Clean up temp manifest for JARs
    if [[ "$entry" == *.jar ]]; then
        rm -f "$manifest_file"
    fi

    if [ -z "$bsn" ]; then
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    # Derive groupId from BSN: com.archimatetool.model → com.archimatetool
    # For single-segment names, use the BSN itself
    if [[ "$bsn" == *.* ]]; then
        group_id="${bsn%.*}"
    else
        group_id="$bsn"
    fi
    artifact_id="$bsn"

    # Extract version from directory/filename
    # Pattern: <bsn>_<version> or <bsn>_<version>.jar
    version="${basename#${bsn}_}"
    version="${version%.jar}"

    if [ -z "$version" ]; then
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    # For directory bundles, create a JAR preserving the OSGi manifest
    if [ -d "$entry" ]; then
        jar_file="/tmp/${basename}.jar"
        jar cfm "$jar_file" "$entry/META-INF/MANIFEST.MF" -C "$entry" .
    fi

    mvn -q install:install-file \
        -Dfile="$jar_file" \
        -DgroupId="$group_id" \
        -DartifactId="$artifact_id" \
        -Dversion="$version" \
        -Dpackaging=jar \
        -DgeneratePom=true 2>/dev/null || {
        SKIPPED=$((SKIPPED + 1))
        # Clean up temp jar
        if [ -d "$entry" ]; then rm -f "$jar_file"; fi
        continue
    }

    # Clean up temp jar for directory bundles
    if [ -d "$entry" ]; then
        rm -f "$jar_file"
    fi

    INSTALLED=$((INSTALLED + 1))
done

echo "Installed $INSTALLED bundles to local Maven repository ($SKIPPED skipped)."
echo ""
echo "Done. You can now build with:"
echo "  mvn clean verify"
