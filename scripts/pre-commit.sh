#!/usr/bin/env bash
# Story 10.1 AC #17 (retro T6) — i18n alphabetical-ordering gate.
#
# Lightweight, dependency-free pre-commit shim. Runs before every commit that
# touches a file under frontend/app/i18n/. The canonical gate lives in
# scripts/lint-i18n-alphabetical.mjs and is also wired into CI via
# `npm --prefix frontend run lint:i18n`.
#
# Install once per clone (opt-in — repo does not mandate Husky/lefthook):
#   ln -s ../../scripts/pre-commit.sh .git/hooks/pre-commit
#   chmod +x scripts/pre-commit.sh
#
# Alternatively, run on demand:
#   npm --prefix frontend run lint:i18n

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"

# Skip if no staged i18n files — fast path for non-i18n commits.
if ! git diff --cached --name-only --diff-filter=ACMR | grep -q '^frontend/app/i18n/.*\.json$'; then
    exit 0
fi

echo "[pre-commit] i18n changes detected — running alphabetical-ordering check..."
if ! node "$REPO_ROOT/scripts/lint-i18n-alphabetical.mjs"; then
    echo ""
    echo "[pre-commit] i18n key ordering violation blocked the commit."
    echo "[pre-commit] Sort keys alphabetically and try again. See Story 10.1 AC #17."
    exit 1
fi
