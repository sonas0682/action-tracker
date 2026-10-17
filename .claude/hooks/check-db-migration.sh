#!/usr/bin/env bash
# PostToolUse hook: after an edit to data/Data.kt, make sure DB_VERSION has a
# matching Migration registered in addMigrations. A build whose version has no
# migration path crashes at launch on Sona's phone, so catch it before pushing.
input=$(cat)
case "$input" in
  *'data/Data.kt"'*) ;;
  *) exit 0 ;;
esac

file="${CLAUDE_PROJECT_DIR:-.}/app/src/main/java/com/sonas/actiontracker/data/Data.kt"
[ -f "$file" ] || exit 0

version=$(sed -n 's/^const val DB_VERSION = \([0-9]\+\).*/\1/p' "$file")
[ -n "$version" ] || exit 0
prev=$((version - 1))
name="MIGRATION_${prev}_${version}"

problems=()
grep -q "Migration(${prev}, ${version})" "$file" || problems+=("no Migration(${prev}, ${version}) object")
grep -q "addMigrations(.*\b${name}\b" "$file" || problems+=("${name} is not passed to addMigrations(...)")

if [ ${#problems[@]} -gt 0 ]; then
  {
    (IFS=";"; echo "DB_VERSION is ${version} but: ${problems[*]}.")
    echo "Every schema version needs a migration from the previous one, or the app crashes at launch."
    echo "See .claude/skills/add-room-migration/SKILL.md."
  } >&2
  exit 2
fi
exit 0
