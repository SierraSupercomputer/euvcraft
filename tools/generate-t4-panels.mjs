#!/usr/bin/env node
/**
 * EUVCRAFT T4 Panel Generator (Node >= 22, built-ins only: fs/path/url/zlib/crypto).
 *
 * Reads tools/t4-panels.json (schema v1) and:
 *  - rasterizes deterministic 16x16 opaque PNG tiles for all T4 panel faces
 *  - generates blockstate variants for 5 block IDs with facing/formed/large/part selectors
 *  - generates block item models and client item refs
 *  - --check mode compares shipped assets without writing; exits nonzero on mismatch
 *  - writes are limited to validated output paths under the repo asset root
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { resolve, dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { createHash } from 'crypto';
import { computeT4Panels } from './lib/t4-panels.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Expected output paths (validated against config)
const EXPECTED_OUTPUT = {
  texturesDir: 'src/main/resources/assets/lithmod/textures/block',
  modelsDir: 'src/main/resources/assets/lithmod/models/block',
  blockstatesDir: 'src/main/resources/assets/lithmod/blockstates',
  itemsDir: 'src/main/resources/assets/lithmod/models/item',
  clientItemsDir: 'src/main/resources/assets/lithmod/items'
};

const BLOCK_IDS = [
  'deposition_etcher',
  'advanced_test_package',
  'advanced_casing',
  'advanced_power_port',
  'advanced_operating_panel'
];

function fail(errors) {
  throw new Error('Validation failed:\n' + errors.join('\n'));
}

function validateConfig(cfg) {
  const errors = [];
  if (!cfg || cfg.version !== 1) errors.push('Config version must be 1');
  if (!cfg.palette || typeof cfg.palette !== 'object') errors.push('Palette missing');
  else {
    for (const [name, hex] of Object.entries(cfg.palette)) {
      if (typeof hex !== 'string' || !/^#[0-9a-f]{6}$/i.test(hex)) {
        errors.push(`Palette ${name}: must be opaque #RRGGBB`);
      }
    }
    if (!cfg.palette.steel) errors.push('Steel base color required');
  }
  if (!cfg.faceRecipes || typeof cfg.faceRecipes !== 'object') errors.push('faceRecipes missing');
  if (!cfg.markings || typeof cfg.markings !== 'object') errors.push('markings missing');
  if (!cfg.unformedRecipes || typeof cfg.unformedRecipes !== 'object') errors.push('unformedRecipes missing');

  // Check required face recipes
  const faces = ['north', 'south', 'west', 'east', 'up', 'down'];
  for (const size of ['large', 'small']) {
    for (const face of faces) {
      if (!cfg.faceRecipes?.[size + '_' + face]) {
        errors.push(`Missing face recipe: ${size}_${face}`);
      }
    }
  }

  // Check required markings
  for (const m of ['controller_front', 'operating_panel_front', 'power_port', 'casing']) {
    if (!cfg.markings?.[m]) errors.push(`Missing marking: ${m}`);
  }

  // Check required unformed recipes
  for (const r of ['controller', 'casing', 'power_port', 'operating_panel']) {
    if (!cfg.unformedRecipes?.[r]) errors.push(`Missing unformed recipe: ${r}`);
  }

  if (errors.length) fail(errors);
}

function loadConfig() {
  return JSON.parse(readFileSync(join(ROOT, 'tools', 't4-panels.json'), 'utf8'));
}

function writeAll(computed) {
  const dirs = new Set([
    EXPECTED_OUTPUT.texturesDir,
    EXPECTED_OUTPUT.modelsDir,
    EXPECTED_OUTPUT.blockstatesDir,
    EXPECTED_OUTPUT.itemsDir,
    EXPECTED_OUTPUT.clientItemsDir
  ]);
  for (const dir of dirs) {
    const abs = join(ROOT, dir);
    if (!existsSync(abs)) mkdirSync(abs, { recursive: true });
  }

  let pngCount = 0, jsonCount = 0;
  for (const [rel, buf] of computed.pngs) {
    writeFileSync(join(ROOT, rel), buf);
    pngCount++;
  }
  for (const [rel, text] of computed.models) {
    writeFileSync(join(ROOT, rel), text, 'utf8');
    jsonCount++;
  }
  console.log(`Wrote ${pngCount} PNGs, ${jsonCount} JSON assets.`);
  if (computed.preview) {
    const previewPath = join(ROOT, 'build/t4-panels-preview.png');
    mkdirSync(dirname(previewPath), { recursive: true });
    writeFileSync(previewPath, computed.preview);
    console.log(`Preview written to build/t4-panels-preview.png`);
  }
}

function checkAll(computed) {
  let bad = 0;
  for (const [rel, buf] of computed.pngs) {
    const path = join(ROOT, rel);
    if (!existsSync(path)) {
      console.log(`MISSING: ${rel}`);
      bad++;
      continue;
    }
    const expected = createHash('sha256').update(buf).digest('hex');
    const actual = createHash('sha256').update(readFileSync(path)).digest('hex');
    if (expected !== actual) {
      console.log(`STALE: ${rel}`);
      bad++;
    }
  }
  const normalize = (s) => s.replace(/\r\n?/g, '\n');
  for (const [rel, text] of computed.models) {
    const path = join(ROOT, rel);
    if (!existsSync(path)) {
      console.log(`MISSING: ${rel}`);
      bad++;
      continue;
    }
    if (normalize(readFileSync(path, 'utf8')) !== normalize(text)) {
      console.log(`STALE: ${rel}`);
      bad++;
    }
  }
  if (bad) {
    console.log(`Check FAILED: ${bad} asset(s) missing or stale.`);
    process.exit(1);
  }
  console.log(`Check PASSED: ${computed.pngs.size} PNGs and ${computed.models.size} JSON assets match.`);
}

function usage() {
  console.error('Usage: node tools/generate-t4-panels.mjs [--check]');
}

function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== '--check')) {
    usage();
    process.exit(2);
  }

  const cfg = loadConfig();
  validateConfig(cfg);

  // Compute everything before any write/check
  const computed = computeT4Panels(cfg);

  if (args[0] === '--check') {
    checkAll(computed);
  } else {
    writeAll(computed);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}