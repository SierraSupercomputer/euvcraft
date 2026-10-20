#!/usr/bin/env node
/**
 * EUVCRAFT M1 Starter Texture Generator (Node >= 22, built-ins only: fs/path/url/zlib/crypto).
 *
 * Reads tools/starter-textures.json (schema v2: recipes, not char patterns) and:
 *  - rasterizes 23 item sprites (transparent background) and 14 block face tiles (fully opaque),
 *  - encodes deterministic RGBA8 PNGs (filter 0, level-9 deflate, unsigned zlib.crc32 CRCs),
 *  - writes 23 item models + 10 block models, plus a 6x nearest-neighbor preview atlas,
 *  - --check mode compares shipped assets without writing; the optional preview is ignored.
 *
 * Writes are limited to the validated output paths; nothing is deleted or overwritten outside them.
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { resolve, dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { deflateSync, crc32 } from 'zlib';
import { createHash } from 'crypto';
import { computeJoinedPanels } from './lib/joined-panels.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Exact expected production asset sets (M1). Anything extra/missing in the manifest fails.
const EXPECTED_ITEM_IDS = [
  'foup_mk1', 'foup_mk2', 'logic_chip_t1', 'logic_chip_t2', 'memory_chip_t2',
  'chip_scrap', 'silicon', 'silicon_boule', 'resist_t1', 'resist_t2',
  'chip_package', 'blank_mask', 'mask', 'mercury_lamp', 'i_line_lamp',
  'contact_plate', 'reduction_lens', 'manual_aligner', 'stepper_stage',
  'basic_air_filter', 'fine_air_filter', 'optical_microscope', 'overlay_scope',
];
// Ten block models. Controller state suffixes are matched exactly ('_formed'/'_unformed'),
// never via a loose endsWith('formed') which would also hit 'unformed'.
const EXPECTED_BLOCK_IDS = [
  'crystal_puller', 'wafer_slicer', 'test_package', 'pattern_generator',
  'process_cell_controller_formed', 'process_cell_controller_unformed',
  'process_cell_casing_formed', 'process_cell_casing_unformed',
  'process_cell_power_port_formed', 'process_cell_power_port_unformed',
];
const CONTROLLER_IDS = new Set(EXPECTED_BLOCK_IDS.slice(4, 6));
// The only writable destinations. Hardcoded; manifest values must match exactly.
const EXPECTED_OUTPUT = {
  itemsDir: 'src/main/resources/assets/lithmod/models/item',
  blocksDir: 'src/main/resources/assets/lithmod/models/block',
  texturesItemsDir: 'src/main/resources/assets/lithmod/textures/item',
  texturesBlocksDir: 'src/main/resources/assets/lithmod/textures/block',
  previewPath: 'build/starter-textures-preview.png',
};

function fail(errors) {
  throw new Error('Validation failed:\n' + errors.join('\n'));
}

/** Hex (#RRGGBB or #RRGGBBAA) to [r,g,b,a]; missing alpha means fully opaque. */
function hexToRgba(hex) {
  const h = hex.startsWith('#') ? hex.slice(1) : hex;
  if (!/^[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/.test(h)) {
    throw new Error(`Invalid hex color: ${hex}`);
  }
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16),
    h.length === 8 ? parseInt(h.slice(6, 8), 16) : 255,
  ];
}

function validateLists(object, list, kind, ids) {
  const errors = [];
  if (!object || typeof object !== 'object' || Array.isArray(object) || !Array.isArray(list)) {
    return [`${kind} requires an asset object and an ID array`];
  }
  const seen = new Set();
  for (const id of list) {
    if (typeof id !== 'string') errors.push(`${kind} entry not a string: ${JSON.stringify(id)}`);
    else if (seen.has(id)) errors.push(`${kind} duplicate: ${id}`);
    seen.add(id);
  }
  const set = Object.keys(object);
  const exact = set.length === ids.length && ids.every((id) => set.includes(id) && seen.has(id)) && seen.size === ids.length;
  if (!exact) errors.push(`${kind} entries must equal the exact expected ${ids.length} IDs`);
  return errors;
}

function validateManifest(m) {
  const errors = [];
  if (m.version !== 2) errors.push(`Manifest version must be 2, got ${m.version}`);

  for (const [name, slots] of Object.entries(m.palette || {})) {
    if (typeof slots !== 'object' || slots === null || Array.isArray(slots)) {
      errors.push(`Palette ${name} must be an object of named slots`);
      continue;
    }
    if (Object.keys(slots).length < 4) errors.push(`Palette ${name} needs at least 4 named color slots`);
    for (const [slot, hex] of Object.entries(slots)) {
      try { hexToRgba(hex); } catch (e) { errors.push(`Palette ${name}.${slot}: ${e.message}`); }
    }
  }

  if (!m.shapes || typeof m.shapes !== 'object') errors.push('shapes missing');
  else {
    for (const [name, ops] of Object.entries(m.shapes)) {
      if (!Array.isArray(ops)) errors.push(`Shape ${name} must be an array of ops`);
    }
  }

  errors.push(...validateLists(m.items, m.output?.itemModelsToUpdate, 'itemModelsToUpdate', EXPECTED_ITEM_IDS));
  errors.push(...validateLists(m.blocks, m.output?.blockModelsToUpdate, 'blockModelsToUpdate', EXPECTED_BLOCK_IDS));
  if (JSON.stringify(m.output?.preserveUntouched) !== JSON.stringify(['test_machine'])) {
    errors.push('output.preserveUntouched must be exactly ["test_machine"]');
  }

  for (const [key, expected] of Object.entries(EXPECTED_OUTPUT)) {
    const value = m.output?.[key];
    if (value !== expected) {
      errors.push(`output.${key} must be exactly "${expected}", got ${JSON.stringify(value)}`);
      continue;
    }
    if (/^[A-Za-z]:|^[/\\]|\.\.|[\\]/.test(value) && key !== 'previewPath') {
      errors.push(`output.${key} is not a safe relative path: ${value}`);
    }
  }

  for (const [id, def] of Object.entries(m.items)) {
    if (!m.palette[def.palette]) errors.push(`Item ${id}: unknown palette ${def.palette}`);
    if (!m.shapes[def.shape]) errors.push(`Item ${id}: unknown shape ${def.shape}`);
  }

  for (const [id, def] of Object.entries(m.blocks)) {
    if (!def.faces) errors.push(`Block ${id} missing faces`);
    else {
      const expectedFaces = CONTROLLER_IDS.has(id)
        ? ['side', 'top', 'front']
        : ['all'];
      if (JSON.stringify(Object.keys(def.faces)) !== JSON.stringify(expectedFaces)) {
        errors.push(`Block ${id} faces must be [${expectedFaces}], got [${Object.keys(def.faces)}]`);
      }
      for (const [face, faceDef] of Object.entries(def.faces)) {
        if (!m.palette[faceDef.palette]) errors.push(`Block ${id}.${face}: unknown palette ${faceDef.palette}`);
        if (!m.shapes[faceDef.shape]) errors.push(`Block ${id}.${face}: unknown shape ${faceDef.shape}`);
      }
    }
  }

  if (errors.length) fail(errors);
}

/** Apply one validated drawing op. Ops: rect/frame/line/dot with palette slot colors. */
function drawOp(img, op, palette, ids) {
  const [kind, color, ...nums] = op;
  const rgba = palette[color];
  if (!rgba) fail([`${ids}: op ${JSON.stringify(op)} uses unknown palette slot "${color}"`]);
  if (nums.some((n) => typeof n !== 'number' || !Number.isInteger(n))) {
    fail([`${ids}: op ${JSON.stringify(op)} must use integer coordinates`]);
  }
  let minXY, maxXY;
  if (kind === 'rect' || kind === 'frame') {
    if (nums.length !== 4) fail([`${ids}: ${kind} needs x,y,w,h`]);
    const [x, y, w, h] = nums;
    if (w <= 0 || h <= 0) fail([`${ids}: op ${JSON.stringify(op)} has invalid w/h`]);
    minXY = [x, y]; maxXY = [x + w - 1, y + h - 1];
  } else if (kind === 'dot') {
    if (nums.length !== 2) fail([`${ids}: dot needs x,y`]);
    minXY = maxXY = [nums[0], nums[1]];
  } else if (kind === 'line') {
    if (nums.length !== 4) fail([`${ids}: line needs x0,y0,x1,y1`]);
    minXY = [nums[0], nums[1]]; maxXY = [nums[2], nums[3]];
  } else {
    fail([`${ids}: unknown op kind "${kind}" (supported: rect, frame, dot, line)`]);
  }
  if ([...minXY, ...maxXY].some((coordinate) => coordinate < 0 || coordinate > 15)) {
    fail([`${ids}: op ${JSON.stringify(op)} exceeds the 16x16 canvas`]);
  }
  const put = (px, py) => {
    const i = (py * 16 + px) * 4;
    img[i] = rgba[0]; img[i + 1] = rgba[1]; img[i + 2] = rgba[2]; img[i + 3] = rgba[3];
  };
  if (kind === 'rect' || kind === 'frame') {
    const [x, y, w, h] = nums;
    if (kind === 'rect') {
      for (let py = y; py < y + h; py++) for (let px = x; px < x + w; px++) put(px, py);
    } else {
      for (let px = x; px < x + w; px++) { put(px, y); put(px, y + h - 1); }
      for (let py = y; py < y + h; py++) { put(x, py); put(x + w - 1, py); }
    }
  } else if (kind === 'dot') {
    put(nums[0], nums[1]);
  } else {
    let [x0, y0, x1, y1] = nums;
    const dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
    const dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      put(x0, y0);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  }
}

/** Item canvas starts transparent; a fully painted background must never be accepted. */
function rasterizeItem(id, def, palettes, shapes) {
  const img = new Uint8Array(16 * 16 * 4);
  for (const op of shapes[def.shape]) drawOp(img, op, palettes[def.palette], `item ${id}`);
  for (let i = 3; i < img.length; i += 4) {
    if (img[i] === 0) return img;
  }
  fail([`Item ${id} would fully cover the canvas; at least one transparent pixel is required`]);
}

/** Block canvas must end fully opaque; transparency in block faces is a validation error. */
function rasterizeBlockFace(id, face, def, palettes, shapes) {
  const img = new Uint8Array(16 * 16 * 4);
  for (const op of shapes[def.shape]) drawOp(img, op, palettes[def.palette], `block ${id}.${face}`);
  for (let i = 3; i < img.length; i += 4) {
    if (img[i] !== 255) {
      fail([`Block ${id}.${face} must be fully opaque; transparent pixels are not allowed`]);
    }
  }
  return img;
}

function chunk(type, data) {
  const head = Buffer.alloc(4);
  head.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(body) >>> 0, 0); // unsigned chunk CRC
  return Buffer.concat([head, body, crcBuf]);
}

/** Encode raw RGBA to PNG: signature + IHDR + filter-0 IDAT + IEND. Level-9 deflate. */
function encodePng(rgba, width, height) {
  const stride = width * 4 + 1;
  const raw = Buffer.alloc(stride * height);
  const src = Buffer.from(rgba);
  for (let y = 0; y < height; y++) {
    raw[y * stride] = 0; // filter type 0
    src.copy(raw, y * stride + 1, y * width * 4, (y + 1) * width * 4);
  }
  const compressed = deflateSync(raw, { level: 9 });
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // color type RGBA8
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    chunk('IHDR', ihdr),
    chunk('IDAT', compressed),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function jsonText(obj) {
  return JSON.stringify(obj, null, 2) + '\n';
}

function itemModelObject(id) {
  return { parent: 'minecraft:item/generated', textures: { layer0: `lithmod:item/${id}` } };
}

function blockModelObject(id) {
  if (CONTROLLER_IDS.has(id)) {
    return {
      parent: 'minecraft:block/orientable',
      textures: {
        top: `lithmod:block/${id}_top`,
        bottom: `lithmod:block/${id}_top`,
        side: `lithmod:block/${id}_side`,
        front: `lithmod:block/${id}_front`,
        particle: `lithmod:block/${id}_side`,
      },
    };
  }
  return {
    parent: 'minecraft:block/cube_all',
    textures: { all: `lithmod:block/${id}` },
  };
}

/** Every generated texture in deterministic display order for the preview legend. */
function previewOrder(manifest) {
  const out = [];
  for (const id of manifest.output.itemModelsToUpdate) out.push(id);
  for (const id of manifest.output.blockModelsToUpdate) {
    if (CONTROLLER_IDS.has(id)) out.push(`${id}_side`, `${id}_top`, `${id}_front`);
    else out.push(id);
  }
  return out;
}

/** Nearest-neighbor x6 atlas over a neutral 8px checkerboard, alpha composited. */
function buildPreview(sprites) {
  const scale = 6, pad = 4, cell = 16 * scale;
  const n = sprites.length;
  const cols = 8;
  const rows = Math.ceil(n / cols);
  const atlasW = cols * cell + (cols + 1) * pad;
  const atlasH = rows * cell + (rows + 1) * pad;
  const atlas = Buffer.alloc(atlasW * atlasH * 4);
  const checker = (x, y) => (((x >> 3) + (y >> 3)) & 1) === 0 ? [0xE8, 0xE8, 0xE8] : [0xB8, 0xB8, 0xB8];
  for (let y = 0; y < atlasH; y++) {
    for (let x = 0; x < atlasW; x++) {
      const [r, g, b] = checker(x, y);
      const i = (y * atlasW + x) * 4;
      atlas[i] = r; atlas[i + 1] = g; atlas[i + 2] = b; atlas[i + 3] = 255;
    }
  }
  sprites.forEach((img, idx) => {
    const cx = idx % cols, cy = Math.floor(idx / cols);
    const ox = pad + cx * (cell + pad), oy = pad + cy * (cell + pad);
    for (let y = 0; y < 16; y++) {
      for (let x = 0; x < 16; x++) {
        const s = (y * 16 + x) * 4;
        const a = img[s + 3];
        for (let dy = 0; dy < scale; dy++) {
          for (let dx = 0; dx < scale; dx++) {
            const dst = (((oy + y * scale + dy) * atlasW) + (ox + x * scale + dx)) * 4;
            const bg = checker(ox + x * scale + dx, oy + y * scale + dy);
            for (let c = 0; c < 3; c++) {
              atlas[dst + c] = (img[s + c] * a + bg[c] * (255 - a) + 127) / 255 | 0;
            }
            atlas[dst + 3] = 255;
          }
        }
      }
    }
  });
  return { png: encodePng(atlas, atlasW, atlasH), width: atlasW, height: atlasH, cols, rows };
}

/** Compute every output buffer before any write/check happens. */
function computeAll(manifest) {
  const palettes = {};
  for (const [name, slots] of Object.entries(manifest.palette)) {
    palettes[name] = {};
    for (const [slot, hex] of Object.entries(slots)) palettes[name][slot] = hexToRgba(hex);
  }
  const raw = new Map();   // relpath -> 16x16 RGBA array
  const pngs = new Map();  // relpath -> Buffer
  const models = new Map(); // relpath -> string (LF-only JSON text)

  for (const id of manifest.output.itemModelsToUpdate) {
    const rel = `${manifest.output.texturesItemsDir}/${id}.png`;
    raw.set(rel, rasterizeItem(id, manifest.items[id], palettes, manifest.shapes));
    pngs.set(rel, encodePng(raw.get(rel), 16, 16));
    models.set(`${manifest.output.itemsDir}/${id}.json`, jsonText(itemModelObject(id)));
  }
  for (const id of manifest.output.blockModelsToUpdate) {
    const def = manifest.blocks[id];
    for (const face of Object.keys(def.faces)) {
      const file = CONTROLLER_IDS.has(id) ? `${id}_${face}` : id;
      const rel = `${manifest.output.texturesBlocksDir}/${file}.png`;
      raw.set(rel, rasterizeBlockFace(id, face, def.faces[face], palettes, manifest.shapes));
      pngs.set(rel, encodePng(raw.get(rel), 16, 16));
    }
    models.set(`${manifest.output.blocksDir}/${id}.json`, jsonText(blockModelObject(id)));
  }
  const preview = buildPreview(previewOrder(manifest).map((name) => raw.get(
    manifest.output.itemModelsToUpdate.includes(name)
      ? `${manifest.output.texturesItemsDir}/${name}.png`
      : `${manifest.output.texturesBlocksDir}/${name}.png`
  )));
  return { raw, pngs, models, preview };
}

function writeAll(manifest, computed) {
  const dirs = new Set([
    manifest.output.itemsDir, manifest.output.blocksDir,
    manifest.output.texturesItemsDir, manifest.output.texturesBlocksDir,
    dirname(manifest.output.previewPath),
  ]);
  for (const dir of dirs) {
    const abs = join(ROOT, dir);
    if (!existsSync(abs)) mkdirSync(abs, { recursive: true });
  }
  for (const [rel, buf] of computed.pngs) writeFileSync(join(ROOT, rel), buf);
  for (const [rel, text] of computed.models) writeFileSync(join(ROOT, rel), text, 'utf8');
  writeFileSync(join(ROOT, manifest.output.previewPath), computed.preview.png);
  writeFileSync(join(ROOT, 'build/joined-panels-preview.png'), computed.joinedPreview);
  console.log(`Wrote ${computed.pngs.size} PNGs, ${computed.models.size} JSON assets, preview ${manifest.output.previewPath} (${computed.preview.width}x${computed.preview.height}).`);
}

/** Compare without writing anything; reports every missing/stale asset, then exits nonzero. */
function checkAll(manifest, computed) {
  let bad = 0;
  for (const [rel, buf] of computed.pngs) {
    const path = join(ROOT, rel);
    if (!existsSync(path)) { console.log(`MISSING: ${rel}`); bad++; continue; }
    const expected = createHash('sha256').update(buf).digest('hex');
    const actual = createHash('sha256').update(readFileSync(path)).digest('hex');
    if (expected !== actual) { console.log(`STALE: ${rel}`); bad++; }
  }
  const normalize = (s) => s.replace(/\r\n?/g, '\n');
  for (const [rel, text] of computed.models) {
    const path = join(ROOT, rel);
    if (!existsSync(path)) { console.log(`MISSING: ${rel}`); bad++; continue; }
    if (normalize(readFileSync(path, 'utf8')) !== normalize(text)) {
      console.log(`STALE: ${rel}`); bad++;
    }
  }
  if (bad) {
    console.log(`Check FAILED: ${bad} asset(s) missing or stale (preview not checked).`);
    process.exit(1);
  }
  console.log(`Check PASSED: ${computed.pngs.size} PNGs and ${computed.models.size} JSON assets match (preview not checked).`);
}

function usage() {
  console.error('Usage: node tools/generate-starter-textures.mjs [--check]');
}

export function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== '--check')) {
    usage();
    process.exit(2);
  }
  const manifest = loadManifest();
  validateManifest(manifest);
  const computed = computeAll(manifest); // all buffers computed before any write
  const joined = computeJoinedPanels(manifest.joinedPanels);
  for (const [path, bytes] of joined.pngs) {
    if (computed.pngs.has(path)) throw new Error('Joined PNG output collision: ' + path);
    computed.pngs.set(path, bytes);
  }
  for (const [path, bytes] of joined.models) {
    if (computed.models.has(path)) throw new Error('Joined JSON output collision: ' + path);
    computed.models.set(path, bytes);
  }
  computed.joinedPreview = joined.preview;
  if (args[0] === '--check') checkAll(manifest, computed);
  else writeAll(manifest, computed);
}

function loadManifest() {
  return JSON.parse(readFileSync(join(ROOT, 'tools', 'starter-textures.json'), 'utf8'));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
