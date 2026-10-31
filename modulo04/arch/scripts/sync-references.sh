#!/bin/sh
# Copies the single source in reference/ into the references/ and scripts/ folders of each skill,
# so every skill folder works on its own (inside the plugin or copied loose into a skills folder).
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

# skill:file pairs. vocabulary.md and language.md come from reference/, the other .md files from
# reference/templates/ (copied to the skill's references/), and the .java files from
# reference/scripts/ (copied to the skill's scripts/).
PAIRS="
setup-architecture:vocabulary.md
setup-architecture:language.md
setup-architecture:dossier-readme.md
setup-architecture:agents-architecture.md
grill-architecture:vocabulary.md
grill-architecture:language.md
grill-architecture:brief.md
grill-architecture:adr.md
grill-architecture:dossier-readme.md
grill-architecture:NextAdrNumber.java
to-characteristics:vocabulary.md
to-characteristics:language.md
to-characteristics:characteristics.md
to-characteristics:dossier-readme.md
to-adr:vocabulary.md
to-adr:language.md
to-adr:adr.md
to-adr:dossier-readme.md
to-adr:NextAdrNumber.java
to-risk:vocabulary.md
to-risk:language.md
to-risk:risk.md
to-risk:dossier-readme.md
to-risk:RiskScore.java
"

for pair in $PAIRS; do
  skill=${pair%%:*}
  file=${pair#*:}
  case "$file" in
    vocabulary.md | language.md)
      source="$PLUGIN_ROOT/reference/$file"
      target="$PLUGIN_ROOT/skills/$skill/references/$file"
      ;;
    *.java)
      source="$PLUGIN_ROOT/reference/scripts/$file"
      target="$PLUGIN_ROOT/skills/$skill/scripts/$file"
      ;;
    *)
      source="$PLUGIN_ROOT/reference/templates/$file"
      target="$PLUGIN_ROOT/skills/$skill/references/$file"
      ;;
  esac

  if [ "$MODE" = "--check" ]; then
    if ! cmp -s "$source" "$target"; then
      echo "out of sync: ${target#"$PLUGIN_ROOT"/}"
      STATUS=1
    fi
  else
    mkdir -p "$(dirname "$target")"
    cp "$source" "$target"
    echo "copied: ${target#"$PLUGIN_ROOT"/}"
  fi
done

if [ "$MODE" = "--check" ] && [ "$STATUS" -eq 0 ]; then
  echo "all references in sync"
fi
exit "$STATUS"
