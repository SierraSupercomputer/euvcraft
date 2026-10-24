#!/usr/bin/env node
/**
 * EUVCRAFT Scanner Module Generator (Node >= 22, built-ins only: fs/path/url/zlib).
 *
 * Generates deterministic 16x16 opaque PNG tiles and block models for 7 scanner modules.
 * Reuses t4_unformed_casing for top/bottom faces. All 4 horizontal faces share the same
 * component art per module (nondirectional, place-anywhere). Models parent minecraft:block/cube.
 * --check mode validates without writing.
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { resolve, dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { deflateSync, crc32 } from 'node:zlib';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const A = 'src/main/resources/assets/lithmod/';
const D = 'src/main/resources/data/lithmod/';

const SCANNER_MODULES = [
  'exposure_module',
  'optics_module',
  'stage_module',
  'filtration_module',
  'metrology_module',
  'immersion_module',
  'diagnostic_panel'
];

const BLOCK_IDS = SCANNER_MODULES.map(m => 'scanner_' + m);

// PNG encoding (same pattern as other generators)
function chunk(type, data) {
  const t = Buffer.from(type);
  const n = Buffer.alloc(4);
  const c = Buffer.alloc(4);
  n.writeUInt32BE(data.length);
  c.writeUInt32BE(crc32(Buffer.concat([t, data])) >>> 0);
  return Buffer.concat([n, t, data, c]);
}

function png(w, h, pixels) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w);
  header.writeUInt32BE(h, 4);
  header[8] = 8;
  header[9] = 6;
  const rows = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) pixels.copy(rows, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

const json = (x) => JSON.stringify(x, null, 2) + '\n';

// Color palette for scanner modules (white/gray industrial casing + module accents)
const PALETTE = {
  // Casing colors (cohesive white/gray industrial)
  casing_base:      [0xD0, 0xD8, 0xE0, 0xFF],  // Light gray-blue base
  casing_light:     [0xE8, 0xF0, 0xF8, 0xFF],  // Highlight
  casing_dark:      [0x90, 0xA0, 0xB0, 0xFF],  // Shadow
  casing_edge:      [0x60, 0x70, 0x80, 0xFF],  // Edge/frame
  casing_bolt:      [0xA0, 0xB0, 0xC0, 0xFF],  // Bolt/rivet

  // Module-specific accent colors
  exposure_yellow:  [0xFF, 0xE0, 0x40, 0xFF],  // Light emitter yellow
  exposure_orange:  [0xFF, 0xB0, 0x20, 0xFF],  // Light emitter orange
  exposure_glow:    [0xFF, 0xF0, 0x80, 0xFF],  // Bright glow

  optics_blue:      [0x40, 0xA0, 0xFF, 0xFF],  // Lens blue
  optics_light:     [0x80, 0xC0, 0xFF, 0xFF],  // Lens highlight
  optics_dark:      [0x20, 0x60, 0xA0, 0xFF],  // Lens shadow
  optics_glare:     [0xC0, 0xE0, 0xFF, 0xFF],  // Lens glare

  stage_gray:       [0x80, 0x88, 0x90, 0xFF],  // Rail gray
  stage_light:      [0xB0, 0xB8, 0xC0, 0xFF],  // Rail highlight
  stage_dark:       [0x50, 0x58, 0x60, 0xFF],  // Rail shadow

  filte_green:      [0x40, 0xC0, 0x60, 0xFF],  // Filtration green
  filte_light:      [0x80, 0xE0, 0xA0, 0xFF],  // Filtration highlight
  filte_dark:       [0x20, 0x80, 0x40, 0xFF],  // Filtration shadow

  metro_teal:       [0x20, 0xC0, 0xA0, 0xFF],  // Metrology teal
  metro_light:      [0x60, 0xE0, 0xD0, 0xFF],  // Metrology highlight
  metro_dark:       [0x10, 0x80, 0x70, 0xFF],  // Metrology shadow

  immer_blue:       [0x20, 0x80, 0xE0, 0xFF],  // Immersion blue fluid
  immer_light:      [0x60, 0xB0, 0xFF, 0xFF],  // Immersion highlight
  immer_dark:       [0x10, 0x50, 0xA0, 0xFF],  // Immersion shadow
  immer_lens:       [0x40, 0xA0, 0xFF, 0xFF],  // Immersion lens

  diag_bg:          [0x10, 0x18, 0x20, 0xFF],  // Diagnostic dark display bg
  diag_amber:       [0xE8, 0xA0, 0x30, 0xFF],  // Diagnostic amber curve
  diag_cyan:        [0x30, 0xE0, 0xE8, 0xFF],  // Diagnostic cyan curve
  diag_dim:         [0x60, 0x40, 0x10, 0xFF],  // Diagnostic dim amber
};

// Fill a 16x16 canvas with a solid color
function fill16(c) {
  const raw = new Uint8Array(16 * 16 * 4);
  for (let i = 0; i < 16 * 16; i++) raw.set(c, i * 4);
  return raw;
}

// Drawing helpers on 16x16 canvas
function rect(raw, x, y, w, h, c) {
  for (let py = y; py < y + h; py++) {
    for (let px = x; px < x + w; px++) {
      if (px >= 0 && px < 16 && py >= 0 && py < 16) {
        const i = (py * 16 + px) * 4;
        raw[i] = c[0]; raw[i + 1] = c[1]; raw[i + 2] = c[2]; raw[i + 3] = c[3];
      }
    }
  }
}

function frame(raw, x, y, w, h, c) {
  for (let px = x; px < x + w; px++) { if (px >= 0 && px < 16) { put(raw, px, y, c); put(raw, px, y + h - 1, c); } }
  for (let py = y; py < y + h; py++) { if (py >= 0 && py < 16) { put(raw, x, py, c); put(raw, x + w - 1, py, c); } }
}

function put(raw, x, y, c) {
  if (x >= 0 && x < 16 && y >= 0 && y < 16) {
    const i = (y * 16 + x) * 4;
    raw[i] = c[0]; raw[i + 1] = c[1]; raw[i + 2] = c[2]; raw[i + 3] = c[3];
  }
}

function line(raw, x0, y0, x1, y1, c) {
  const dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
  const dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
  let err = dx + dy;
  for (;;) {
    put(raw, x0, y0, c);
    if (x0 === x1 && y0 === y1) break;
    const e2 = 2 * err;
    if (e2 >= dy) { err += dy; x0 += sx; }
    if (e2 <= dx) { err += dx; y0 += sy; }
  }
}

function dot(raw, x, y, c) { put(raw, x, y, c); }

// Base casing face (shared by all modules for horizontal faces before module art)
function drawCasingBase() {
  const raw = fill16(PALETTE.casing_base);
  // Subtle bevel/frame
  frame(raw, 0, 0, 16, 16, PALETTE.casing_edge);
  rect(raw, 1, 1, 14, 1, PALETTE.casing_light);
  rect(raw, 1, 2, 1, 12, PALETTE.casing_light);
  rect(raw, 14, 2, 1, 12, PALETTE.casing_dark);
  rect(raw, 2, 13, 12, 1, PALETTE.casing_dark);
  // Corner bolts
  dot(raw, 2, 2, PALETTE.casing_bolt);
  dot(raw, 13, 2, PALETTE.casing_bolt);
  dot(raw, 2, 13, PALETTE.casing_bolt);
  dot(raw, 13, 13, PALETTE.casing_bolt);
  return raw;
}

// Module-specific face drawing (on top of casing base)
function drawExposureFace() {
  const raw = drawCasingBase();
  // Light emitter: central bright rectangle with glow
  rect(raw, 5, 5, 6, 6, PALETTE.exposure_yellow);
  rect(raw, 6, 6, 4, 4, PALETTE.exposure_glow);
  rect(raw, 7, 7, 2, 2, [0xFF, 0xFF, 0xC0, 0xFF]);
  // Surrounding trim
  frame(raw, 4, 4, 8, 8, PALETTE.casing_edge);
  return raw;
}

function drawOpticsFace() {
  const raw = drawCasingBase();
  // Lens: large central circle-approximation
  rect(raw, 4, 4, 8, 8, PALETTE.optics_dark);
  rect(raw, 5, 5, 6, 6, PALETTE.optics_blue);
  rect(raw, 6, 6, 4, 4, PALETTE.optics_light);
  // Glare highlight
  rect(raw, 7, 7, 2, 2, PALETTE.optics_glare);
  // Lens housing ring
  frame(raw, 3, 3, 10, 10, PALETTE.casing_edge);
  return raw;
}

function drawStageFace() {
  const raw = drawCasingBase();
  // Positioning rails: horizontal lines with teeth
  for (let y = 4; y <= 11; y += 3) {
    rect(raw, 2, y, 12, 1, PALETTE.stage_dark);
    rect(raw, 2, y + 1, 12, 1, PALETTE.stage_gray);
    // Teeth
    for (let x = 3; x < 14; x += 3) {
      dot(raw, x, y, PALETTE.stage_light);
    }
  }
  // Cross rail
  rect(raw, 7, 3, 2, 10, PALETTE.stage_dark);
  rect(raw, 8, 3, 2, 10, PALETTE.stage_gray);
  return raw;
}

function drawFiltrationFace() {
  const raw = drawCasingBase();
  // Grille: vertical slots
  for (let x = 3; x < 13; x += 2) {
    rect(raw, x, 3, 1, 10, PALETTE.filte_dark);
    rect(raw, x + 1, 3, 1, 10, PALETTE.filte_green);
  }
  // Frame
  frame(raw, 2, 2, 12, 12, PALETTE.casing_edge);
  // Filter media indicator dots
  dot(raw, 5, 5, PALETTE.filte_light);
  dot(raw, 10, 5, PALETTE.filte_light);
  dot(raw, 5, 10, PALETTE.filte_light);
  dot(raw, 10, 10, PALETTE.filte_light);
  return raw;
}

function drawMetrologyFace() {
  const raw = drawCasingBase();
  // Sensor array: grid of small squares
  for (let gy = 0; gy < 3; gy++) {
    for (let gx = 0; gx < 3; gx++) {
      const x = 3 + gx * 4;
      const y = 3 + gy * 4;
      rect(raw, x, y, 3, 3, PALETTE.metro_dark);
      rect(raw, x + 1, y + 1, 1, 1, PALETTE.metro_light);
    }
  }
  // Central readout
  rect(raw, 6, 6, 4, 4, PALETTE.metro_teal);
  rect(raw, 7, 7, 2, 2, PALETTE.metro_light);
  frame(raw, 2, 2, 12, 12, PALETTE.casing_edge);
  return raw;
}

function drawImmersionFace() {
  const raw = drawCasingBase();
  // Fluid chamber: blue fill with lens window
  rect(raw, 3, 3, 10, 10, PALETTE.immer_dark);
  rect(raw, 4, 4, 8, 8, PALETTE.immer_blue);
  // Fluid highlight (meniscus)
  rect(raw, 4, 4, 8, 2, PALETTE.immer_light);
  // Lens window in center
  rect(raw, 6, 6, 4, 4, PALETTE.immer_lens);
  rect(raw, 7, 7, 2, 2, PALETTE.optics_glare);
  // Chamber frame
  frame(raw, 2, 2, 12, 12, PALETTE.casing_edge);
  return raw;
}

function drawDiagnosticFace() {
  const raw = drawCasingBase();
  // Dark display background
  rect(raw, 2, 2, 12, 12, PALETTE.diag_bg);
  frame(raw, 2, 2, 12, 12, PALETTE.casing_edge);
  // Amber curve (waveform)
  for (let x = 0; x < 10; x++) {
    const y = Math.round(4 * Math.sin(x * 0.6) + 8);
    dot(raw, 3 + x, y, PALETTE.diag_amber);
    if (y > 4) dot(raw, 3 + x, y - 1, PALETTE.diag_dim);
  }
  // Cyan curve (inverse phase)
  for (let x = 0; x < 10; x++) {
    const y = Math.round(-3 * Math.sin(x * 0.6 + 1.5) + 8);
    dot(raw, 3 + x, y, PALETTE.diag_cyan);
  }
  // Corner indicators
  dot(raw, 3, 3, PALETTE.diag_amber);
  dot(raw, 12, 3, PALETTE.diag_cyan);
  dot(raw, 3, 12, PALETTE.diag_cyan);
  dot(raw, 12, 12, PALETTE.diag_amber);
  return raw;
}

const MODULE_DRAWERS = {
  exposure_module: drawExposureFace,
  optics_module: drawOpticsFace,
  stage_module: drawStageFace,
  filtration_module: drawFiltrationFace,
  metrology_module: drawMetrologyFace,
  immersion_module: drawImmersionFace,
  diagnostic_panel: drawDiagnosticFace,
};

// Generate all assets
export function outputs() {
  const out = new Map();
  const put = (path, value) => {
    if (out.has(path)) throw Error('Duplicate ' + path);
    out.set(path, Buffer.isBuffer(value) ? value : Buffer.from(json(value)));
  };

  // Generate each scanner module
  for (const module of SCANNER_MODULES) {
    const blockId = 'scanner_' + module;
    const drawFace = MODULE_DRAWERS[module];

    // Horizontal face texture (all 4 sides same)
    const faceRaw = drawFace();
    const facePng = png(16, 16, Buffer.from(faceRaw));
    put(A + 'textures/block/' + blockId + '.png', facePng);

    // Block model: parent minecraft:block/cube with proper six faces
    // Top/bottom use t4_unformed_casing, all 4 horizontals use module face
    const model = {
      parent: 'minecraft:block/cube',
      textures: {
        particle: 'lithmod:block/' + blockId,
        north: 'lithmod:block/' + blockId,
        south: 'lithmod:block/' + blockId,
        east: 'lithmod:block/' + blockId,
        west: 'lithmod:block/' + blockId,
        up: 'lithmod:block/t4_unformed_casing',
        down: 'lithmod:block/t4_unformed_casing'
      }
    };
    put(A + 'models/block/' + blockId + '.json', model);


  }

  return out;
}

function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== '--check')) {
    console.error('Usage: node tools/generate-scanner-assets.mjs [--check]');
    process.exit(2);
  }

  const map = outputs();
  let stale = 0;

  for (const [path, bytes] of map) {
    // Validate output paths stay within expected directories
    const allowedPrefixes = [
      A + 'textures/block/',
      A + 'models/block/',
      A + 'blockstates/',
      A + 'models/item/',
      A + 'items/'
    ];
    const allowed = allowedPrefixes.some(p => path.startsWith(p));
    if (!allowed) throw Error('Output escapes scope ' + path);

    const to = join(ROOT, path);
    if (args[0] === '--check') {
      if (!existsSync(to) || !readFileSync(to).equals(bytes)) {
        console.error('Missing/stale ' + path);
        stale++;
      }
    } else {
      mkdirSync(dirname(to), { recursive: true });
      writeFileSync(to, bytes);
    }
  }

  if (stale) throw Error('Stale scanner outputs: ' + stale);
  console.log((args[0] === '--check' ? 'Checked ' : 'Generated ') + map.size + ' deterministic scanner assets');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();