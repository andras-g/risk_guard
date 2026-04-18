#!/usr/bin/env node
/**
 * Story 10.1 AC #17 (retro T6) — i18n alphabetical-ordering lint.
 *
 * Parses every JSON file under frontend/app/i18n/{hu,en}/*.json and asserts that
 * object keys are in alphabetical order at every nesting level. Ordering drift was
 * a recurrent patch item across Epic 9 (9.2 P12, 9.5 R2-P9); this script exists so
 * the same bug can never re-land silently.
 *
 * Invoke as:
 *   node scripts/lint-i18n-alphabetical.mjs         (exit 1 on violation)
 *   npm --prefix frontend run lint:i18n             (same, from frontend scripts)
 *
 * The native JSON.parse() preserves source insertion order for string keys in V8,
 * so Object.keys(parsed) reflects the file's textual order — which is what we check.
 */

import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join, relative, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = resolve(__dirname, '..');
const I18N_ROOT = join(REPO_ROOT, 'frontend', 'app', 'i18n');

function collectJsonFiles(root) {
  if (!existsSync(root)) return [];
  const out = [];
  for (const locale of readdirSync(root, { withFileTypes: true })) {
    if (!locale.isDirectory()) continue;
    const dir = join(root, locale.name);
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.isFile() && entry.name.endsWith('.json')) {
        out.push(join(dir, entry.name));
      }
    }
  }
  return out.sort();
}

function walk(obj, path, violations, file) {
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) return;
  const keys = Object.keys(obj);
  const sorted = [...keys].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  for (let i = 0; i < keys.length; i++) {
    if (keys[i] !== sorted[i]) {
      violations.push({
        file: relative(REPO_ROOT, file),
        path: path.length === 0 ? '<root>' : path.join('.'),
        offending: keys[i],
        expected: sorted[i],
      });
      return;
    }
  }
  for (const key of keys) {
    walk(obj[key], [...path, key], violations, file);
  }
}

function lint() {
  const files = collectJsonFiles(I18N_ROOT);
  if (files.length === 0) {
    console.warn(`[lint:i18n] no JSON files found under ${relative(REPO_ROOT, I18N_ROOT)} — nothing to check.`);
    return 0;
  }
  const violations = [];
  for (const file of files) {
    let parsed;
    try {
      parsed = JSON.parse(readFileSync(file, 'utf8'));
    } catch (err) {
      console.error(`[lint:i18n] cannot parse ${relative(REPO_ROOT, file)}: ${err.message}`);
      return 1;
    }
    walk(parsed, [], violations, file);
  }
  if (violations.length === 0) {
    console.log(`[lint:i18n] ${files.length} files OK — keys alphabetical at every level.`);
    return 0;
  }
  const first = violations[0];
  console.error(
    `[lint:i18n] alphabetical-ordering violation:\n` +
    `  file: ${first.file}\n` +
    `  at:   ${first.path}\n` +
    `  got:  ${first.offending}\n` +
    `  want: ${first.expected} (should appear before "${first.offending}")\n` +
    `  (${violations.length} violation${violations.length === 1 ? '' : 's'} total — first shown)`
  );
  return 1;
}

process.exit(lint());
