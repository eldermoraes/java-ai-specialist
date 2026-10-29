#!/bin/sh
# Copies the single source in reference/ into the references/ folder of each skill, so every
# skill folder works on its own (inside the plugin or copied loose into a skills folder).
#
# Usage, from anywhere:
#   scripts/sync-references.sh          copy the files
#   scripts/sync-references.sh --check  only compare; exit 1 if any copy differs from the source
#
# Maintainer tool. The skills never run it.

set -eu

PLUGIN_ROOT=$(cd "$(dirname "$0")/.." && pwd)
MODE=${1:-sync}
STATUS=0

# skill:file pairs. vocabulary.md and language.md come from reference/, the others from
# reference/templates/.
PAIRS="
setup-architecture:vocabulary.md
setup-architecture:language.md
setup-architecture:dossier-readme.md
setup-architecture:agents-architecture.md
grill-architecture:vocabulary.md
grill-architecture:language.md
grill-architecture:brief.md
grill-architecture:adr.md
to-characteristics:vocabulary.md
to-characteristics:language.md
to-characteristics:characteristics.md
"

for pair in $PAIRS; do
  skill=${pair%%:*}
  file=${pair#*:}
  if [ "$file" = "vocabulary.md" ] || [ "$file" = "language.md" ]; then
    source="$PLUGIN_ROOT/reference/$file"
  else
    source="$PLUGIN_ROOT/reference/templates/$file"
  fi
  target="$PLUGIN_ROOT/skills/$skill/references/$file"

  if [ "$MODE" = "--check" ]; then
    if ! cmp -s "$source" "$target"; then
      echo "out of sync: skills/$skill/references/$file"
      STATUS=1
    fi
  else
    mkdir -p "$(dirname "$target")"
    cp "$source" "$target"
    echo "copied: skills/$skill/references/$file"
  fi
done

if [ "$MODE" = "--check" ] && [ "$STATUS" -eq 0 ]; then
  echo "all references in sync"
fi
exit "$STATUS"
