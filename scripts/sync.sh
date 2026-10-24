#!/usr/bin/env bash
# Pulls upstream vibenilla changes into each module directory.
# Usage: scripts/sync.sh [module...]   (defaults to all modules)
set -euo pipefail

declare -A branches=(
    [pvp]=master
    [worldgen]=main
    [placement]=main
    [crafting]=main
    [fluids]=master
    [loot]=master
)

modules=("$@")
[[ ${#modules[@]} -eq 0 ]] && modules=("${!branches[@]}")

for module in "${modules[@]}"; do
    branch=${branches[$module]:?unknown module: $module}
    echo "Syncing $module ($branch)"
    git fetch -q "https://github.com/vibenilla/$module" "$branch"
    git merge --no-edit -X subtree="$module/" FETCH_HEAD -m "chore($module): sync from upstream"
done
