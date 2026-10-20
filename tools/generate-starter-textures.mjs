#!/usr/bin/env node
/**
 * EUVCRAFT M1 Starter Texture Generator
 *
 * Pure Node.js (builtins only: fs, path, url, zlib, crypto)
 * Deterministic PNG encoder (RGBA8, filter 0, correct CRC32)
 * Reads tools/starter-textures.json manifest ONLY
 * Writes: textures, updated models, preview atlas
 * --check mode: validates only, never writes
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync } from 'fs';
import { resolve, dirname, join, relative, basename } from 'path';
import { fileURLToPath } from 'url';
import { createDeflate, crc32 } from 'zlib';
import { createHash } from 'crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = resolve(__dirname, '..');
const MANIFEST_PATH = join(ROOT, 'tools', 'starter-textures.json');

/** Read and parse manifest */
function loadManifest() {
  const raw = readFileSync(MANIFEST_PATH, 'utf8');
  return JSON.parse(raw);
}

/** Hex color (#RRGGBB or #RRGGBBAA) to [r,g,b,a] */
function hexToRgba(hex) {
  const h = hex.startsWith('#') ? hex.slice(1) : hex;
  if (h.length === 6) {
    return [
      parseInt(h.slice(0, 2), 16),
      parseInt(h.slice(2, 4), 16),
      parseInt(h.slice(4, 6), 16),
      255
    ];
  }
  if (h.length === 8) {
    return [
      parseInt(h.slice(0, 2), 16),
      parseInt(h.slice(2, 4), 16),
      parseInt(h.slice(4, 6), 16),
      parseInt(h.slice(6, 8), 16)
    ];
  }
  throw new Error(`Invalid hex color: ${hex}`);
}

/** Expand 16-char pattern row to 16 RGBA pixels using palette */
function expandPatternRow(row, paletteColors) {
  if (row.length !== 16) throw new Error(`Pattern row must be 16 chars, got ${row.length}`);
  const out = new Uint8Array(16 * 4);
  for (let i = 0; i < 16; i++) {
    const ch = row[i];
    let idx;
    if (ch === '.') {
      idx = 0; // transparent
    } else if (ch === '#') {
      idx = 1; // first opaque shade
    } else if (ch >= '0' && ch <= '9') {
      idx = parseInt(ch, 10) + 1;
    } else if (ch >= 'A' && ch <= 'F') {
      idx = parseInt(ch, 16) + 1;
    } else {
      throw new Error(`Invalid pattern char: ${ch}`);
    }
    const color = paletteColors[Math.min(idx, paletteColors.length - 1)];
    out[i * 4] = color[0];
    out[i * 4 + 1] = color[1];
    out[i * 4 + 2] = color[2];
    out[i * 4 + 3] = color[3];
  }
  return out;
}

/** Build 16x16 RGBA image from pattern array and palette name */
function buildImage(pattern, paletteColors) {
  const img = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) {
    const row = expandPatternRow(pattern[y], paletteColors);
    img.set(row, y * 16 * 4);
  }
  return img;
}

/** PNG chunk helper */
function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crc = crc32(Buffer.concat([typeBuf, data]));
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc, 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

/** Encode raw RGBA8 (16x16) to PNG with filter 0, deterministic */
function encodePng(rgba) {
  const width = 16;
  const height = 16;
  // IDAT: each row = filter byte (0) + 16*4 bytes
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter type 0
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  const compressed = new Uint8Array(createDeflate(Buffer.from(raw)).buffer);
  // PNG signature + IHDR + IDAT + IEND
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // color type: RGBA
  ihdr[10] = 0; // compression
  ihdr[11] = 0; // filter
  ihdr[12] = 0; // interlace
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', Buffer.from(compressed)),
    pngChunk('IEND', Buffer.alloc(0))
  ]);
}

/** Deterministic SHA-256 of file contents for staleness check */
function fileHash(path) {
  if (!existsSync(path)) return null;
  const data = readFileSync(path);
  return createHash('sha256').update(data).digest('hex');
}

/** Ensure directory exists */
function ensureDir(dir) {
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
}

/** Validate manifest structure and invariants */
function validateManifest(manifest) {
  const errors = [];

  // Version
  if (manifest.version !== 1) errors.push(`Manifest version must be 1, got ${manifest.version}`);

  // Palette entries must be arrays of 6 hex colors
  for (const [name, colors] of Object.entries(manifest.palette)) {
    if (!Array.isArray(colors) || colors.length !== 6) {
      errors.push(`Palette ${name} must have exactly 6 colors`);
    }
    for (const c of colors) {
      if (!/^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/.test(c)) {
        errors.push(`Palette ${name} color ${c} invalid hex`);
      }
    }
  }

  // Items
  const itemIds = new Set();
  for (const [id, def] of Object.entries(manifest.items)) {
    if (itemIds.has(id)) errors.push(`Duplicate item ID: ${id}`);
    itemIds.add(id);
    if (!manifest.palette[def.palette]) errors.push(`Item ${id} references unknown palette ${def.palette}`);
    if (!Array.isArray(def.pattern) || def.pattern.length !== 16) {
      errors.push(`Item ${id} pattern must be 16 rows`);
    } else {
      for (let y = 0; y < 16; y++) {
        if (def.pattern[y].length !== 16) {
          errors.push(`Item ${id} row ${y} must be 16 chars`);
        }
        for (const ch of def.pattern[y]) {
          if (!'.#0123456789ABCDEF'.includes(ch)) {
            errors.push(`Item ${id} row ${y} invalid char: ${ch}`);
          }
        }
      }
    }
  }

  // Blocks
  const blockIds = new Set();
  for (const [id, def] of Object.entries(manifest.blocks)) {
    if (blockIds.has(id)) errors.push(`Duplicate block ID: ${id}`);
    blockIds.add(id);
    if (!def.textures) errors.push(`Block ${id} missing textures`);
    else {
      for (const [face, tex] of Object.entries(def.textures)) {
        if (!manifest.palette[tex.palette]) errors.push(`Block ${id} face ${face} unknown palette ${tex.palette}`);
        if (!Array.isArray(tex.pattern) || tex.pattern.length !== 16) {
          errors.push(`Block ${id} face ${face} pattern must be 16 rows`);
        } else {
          for (let y = 0; y < 16; y++) {
            if (tex.pattern[y].length !== 16) {
              errors.push(`Block ${id} face ${face} row ${y} must be 16 chars`);
            }
          }
        }
      }
    }
  }

  // Output paths
  const out = manifest.output;
  if (!out.itemModelsToUpdate || !Array.isArray(out.itemModelsToUpdate)) errors.push('output.itemModelsToUpdate missing');
  if (!out.blockModelsToUpdate || !Array.isArray(out.blockModelsToUpdate)) errors.push('output.blockModelsToUpdate missing');
  if (!out.preserveUntouched || !Array.isArray(out.preserveUntouched)) errors.push('output.preserveUntouched missing');

  // Check item models exist in manifest
  for (const id of out.itemModelsToUpdate) {
    if (!manifest.items[id]) errors.push(`output.itemModelsToUpdate references unknown item: ${id}`);
  }
  for (const id of out.blockModelsToUpdate) {
    if (!manifest.blocks[id]) errors.push(`output.blockModelsToUpdate references unknown block: ${id}`);
  }
  for (const id of out.preserveUntouched) {
    if (manifest.items[id] || manifest.blocks[id]) {
      errors.push(`output.preserveUntouched ${id} should not be in items/blocks`);
    }
  }

  // No path traversal in output dirs
  for (const key of ['itemsDir', 'blocksDir', 'texturesItemsDir', 'texturesBlocksDir', 'previewPath']) {
    const p = out[key];
    if (p.includes('..')) errors.push(`output.${key} contains path traversal: ${p}`);
  }

  if (errors.length) throw new Error('Manifest validation failed:\n' + errors.join('\n'));
}

/** Generate item texture PNG */
function generateItemTexture(itemDef, paletteMap) {
  const colors = paletteMap[itemDef.palette].map(hexToRgba);
  const img = buildImage(itemDef.pattern, colors);
  return encodePng(img);
}

/** Generate block texture PNG for a face */
function generateBlockTexture(faceDef, paletteMap) {
  const colors = paletteMap[faceDef.palette].map(hexToRgba);
  const img = buildImage(faceDef.pattern, colors);
  return encodePng(img);
}

/** Write item model JSON (LF only) */
function writeItemModel(outDir, itemId) {
  const content = `{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "lithmod:item/${itemId}"
  }
}`;
  const path = join(outDir, `${itemId}.json`);
  writeFileSync(path, content, { encoding: 'utf8', flag: 'w' });
}

/** Write block model JSON (LF only) */
function writeBlockModel(outDir, blockId, manifest) {
  const blockDef = manifest.blocks[blockId];
  let content;
  if (blockId === 'crystal_puller' || blockId === 'wafer_slicer' || blockId === 'test_package' || blockId === 'pattern_generator') {
    // Simple cube_all-style models
    content = `{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "lithmod:block/${blockId}"
  }
}`;
  } else if (blockId.startsWith('process_cell_controller')) {
    const formed = blockId.endsWith('formed');
    const base = formed ? 'process_cell_controller_formed' : 'process_cell_controller_unformed';
    content = `{
  "parent": "minecraft:block/orientable",
  "textures": {
    "top": "lithmod:block/${base}_top",
    "bottom": "lithmod:block/${base}_top",
    "side": "lithmod:block/${base}_side",
    "front": "lithmod:block/${base}_front"
  }
}`;
  } else if (blockId.startsWith('process_cell_casing')) {
    content = `{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "lithmod:block/${blockId}"
  }
}`;
  } else if (blockId.startsWith('process_cell_power_port')) {
    content = `{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "lithmod:block/${blockId}"
  }
}`;
  } else {
    throw new Error(`Unknown block model type: ${blockId}`);
  }
  const path = join(outDir, `${blockId}.json`);
  writeFileSync(path, content, { encoding: 'utf8', flag: 'w' });
}

/** Generate preview atlas (nearest-neighbor grid on checkerboard) */
function generatePreviewAtlas(manifest, itemTextures, blockTextures) {
  const itemIds = manifest.output.itemModelsToUpdate;
  const blockIds = manifest.output.blockModelsToUpdate;
  const allIds = [...itemIds, ...blockIds];
  const count = allIds.length;
  const cols = Math.ceil(Math.sqrt(count));
  const rows = Math.ceil(count / cols);
  const cell = 16;
  const pad = 1;
  const atlasW = cols * (cell + pad) + pad;
  const atlasH = rows * (cell + pad) + pad;
  const atlas = new Uint8Array(atlasW * atlasH * 4);

  // Checkerboard background
  for (let y = 0; y < atlasH; y++) {
    for (let x = 0; x < atlasW; x++) {
      const cx = Math.floor((x - pad) / (cell + pad));
      const cy = Math.floor((y - pad) / (cell + pad));
      const isCell = x >= pad && y >= pad && (x - pad) % (cell + pad) < cell && (y - pad) % (cell + pad) < cell;
      const checker = (cx + cy) % 2 === 0 ? 200 : 180;
      const idx = (y * atlasW + x) * 4;
      if (isCell) {
        // Will be overwritten by texture
        atlas[idx] = checker;
        atlas[idx + 1] = checker;
        atlas[idx + 2] = checker;
        atlas[idx + 3] = 255;
      } else {
        atlas[idx] = checker;
        atlas[idx + 1] = checker;
        atlas[idx + 2] = checker;
        atlas[idx + 3] = 255;
      }
    }
  }

  // Blit textures
  for (let i = 0; i < allIds.length; i++) {
    const id = allIds[i];
    const cx = i % cols;
    const cy = Math.floor(i / cols);
    const ox = pad + cx * (cell + pad);
    const oy = pad + cy * (cell + pad);
    let tex;
    if (itemIds.includes(id)) tex = itemTextures[id];
    else tex = blockTextures[id];
    if (!tex) continue;
    for (let y = 0; y < 16; y++) {
      for (let x = 0; x < 16; x++) {
        const src = (y * 16 + x) * 4;
        const dst = ((oy + y) * atlasW + (ox + x)) * 4;
        atlas[dst] = tex[src];
        atlas[dst + 1] = tex[src + 1];
        atlas[dst + 2] = tex[src + 2];
        atlas[dst + 3] = tex[src + 3];
      }
    }
  }

  // Add number labels (simple 3x5 digits) - optional, skip for simplicity
  return encodePng(atlas);
}

/** Main generation */
async function generate(checkOnly = false) {
  const manifest = loadManifest();
  validateManifest(manifest);

  const paletteMap = {};
  for (const [name, colors] of Object.entries(manifest.palette)) {
    paletteMap[name] = colors.map(hexToRgba);
  }

  // Generate item textures
  const itemTextures = {};
  for (const id of manifest.output.itemModelsToUpdate) {
    const png = generateItemTexture(manifest.items[id], paletteMap);
    itemTextures[id] = png;
  }

  // Generate block textures
  const blockTextures = {};
  for (const id of manifest.output.blockModelsToUpdate) {
    const blockDef = manifest.blocks[id];
    if (id === 'crystal_puller' || id === 'wafer_slicer' || id === 'test_package' || id === 'pattern_generator') {
      blockTextures[id] = generateBlockTexture(blockDef.textures.all, paletteMap);
    } else if (id.startsWith('process_cell_controller')) {
      blockTextures[`${id}_side`] = generateBlockTexture(blockDef.textures.side, paletteMap);
      blockTextures[`${id}_top`] = generateBlockTexture(blockDef.textures.top, paletteMap);
      blockTextures[`${id}_front`] = generateBlockTexture(blockDef.textures.front, paletteMap);
      blockTextures[`${id}_stripe`] = generateBlockTexture(blockDef.textures.stripe, paletteMap);
    } else if (id.startsWith('process_cell_casing') || id.startsWith('process_cell_power_port')) {
      blockTextures[id] = generateBlockTexture(blockDef.textures.all, paletteMap);
    }
  }

  if (checkOnly) {
    // Verify existing files match expected
    let stale = false;
    let missing = false;

    // Check item textures
    for (const id of manifest.output.itemModelsToUpdate) {
      const path = join(ROOT, manifest.output.texturesItemsDir, `${id}.png`);
      const expected = itemTextures[id];
      const actualHash = fileHash(path);
      const expectedHash = createHash('sha256').update(expected).digest('hex');
      if (!actualHash) {
        console.log(`MISSING: ${path}`);
        missing = true;
      } else if (actualHash !== expectedHash) {
        console.log(`STALE: ${path}`);
        stale = true;
      }
    }

    // Check block textures
    for (const id of manifest.output.blockModelsToUpdate) {
      const blockDef = manifest.blocks[id];
      const checkTextures = (id === 'crystal_puller' || id === 'wafer_slicer' || id === 'test_package' || id === 'pattern_generator')
        ? { all: blockTextures[id] }
        : (id.startsWith('process_cell_controller')
          ? { side: blockTextures[`${id}_side`], top: blockTextures[`${id}_top`], front: blockTextures[`${id}_front`], stripe: blockTextures[`${id}_stripe`] }
          : { all: blockTextures[id] });

      for (const [face, expected] of Object.entries(checkTextures)) {
        const faceName = face === 'all' ? id : `${id}_${face}`;
        const path = join(ROOT, manifest.output.texturesBlocksDir, `${faceName}.png`);
        const actualHash = fileHash(path);
        const expectedHash = createHash('sha256').update(expected).digest('hex');
        if (!actualHash) {
          console.log(`MISSING: ${path}`);
          missing = true;
        } else if (actualHash !== expectedHash) {
          console.log(`STALE: ${path}`);
          stale = true;
        }
      }
    }

    // Check item models
    for (const id of manifest.output.itemModelsToUpdate) {
      const path = join(ROOT, manifest.output.itemsDir, `${id}.json`);
      if (!existsSync(path)) {
        console.log(`MISSING MODEL: ${path}`);
        missing = true;
      } else {
        const content = readFileSync(path, 'utf8');
        const expected = `{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "lithmod:item/${id}"
  }
}`;
        if (content !== expected) {
          console.log(`STALE MODEL: ${path}`);
          stale = true;
        }
      }
    }

    // Check block models
    for (const id of manifest.output.blockModelsToUpdate) {
      const path = join(ROOT, manifest.output.blocksDir, `${id}.json`);
      if (!existsSync(path)) {
        console.log(`MISSING MODEL: ${path}`);
        missing = true;
      } else {
        // Generate expected and compare
        let expected;
        if (id === 'crystal_puller' || id === 'wafer_slicer' || id === 'test_package' || id === 'pattern_generator') {
          expected = `{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "lithmod:block/${id}"
  }
}`;
        } else if (id.startsWith('process_cell_controller')) {
          expected = `{
  "parent": "minecraft:block/orientable",
  "textures": {
    "top": "lithmod:block/${id}_top",
    "bottom": "lithmod:block/${id}_top",
    "side": "lithmod:block/${id}_side",
    "front": "lithmod:block/${id}_front"
  }
}`;
        } else if (id.startsWith('process_cell_casing') || id.startsWith('process_cell_power_port')) {
          expected = `{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "lithmod:block/${id}"
  }
}`;
        }
        const content = readFileSync(path, 'utf8');
        if (content !== expected) {
          console.log(`STALE MODEL: ${path}`);
          stale = true;
        }
      }
    }

    if (missing || stale) {
      console.log('Check FAILED: missing or stale assets detected');
      process.exit(1);
    } else {
      console.log('Check PASSED: all assets up to date');
    }
    return;
  }

  // Write item textures
  ensureDir(join(ROOT, manifest.output.texturesItemsDir));
  for (const [id, png] of Object.entries(itemTextures)) {
    writeFileSync(join(ROOT, manifest.output.texturesItemsDir, `${id}.png`), png);
  }

  // Write block textures
  ensureDir(join(ROOT, manifest.output.texturesBlocksDir));
  for (const id of manifest.output.blockModelsToUpdate) {
    const blockDef = manifest.blocks[id];
    if (id === 'crystal_puller' || id === 'wafer_slicer' || id === 'test_package' || id === 'pattern_generator') {
      writeFileSync(join(ROOT, manifest.output.texturesBlocksDir, `${id}.png`), blockTextures[id]);
    } else if (id.startsWith('process_cell_controller')) {
      writeFileSync(join(ROOT, manifest.output.texturesBlocksDir, `${id}_side.png`), blockTextures[`${id}_side`]);
      writeFileSync(join(ROOT, manifest.output.texturesBlocksDir, `${id}_top.png`), blockTextures[`${id}_top`]);
      writeFileSync(join(ROOT, manifest.output.texturesBlocksDir, `${id}_front.png`), blockTextures[`${id}_front`]);
      writeFileSync(join(ROOT, manifest.output.texturesBlocksDir, `${id}_stripe.png`), blockTextures[`${id}_stripe`]);
    } else if (id.startsWith('process_cell_casing') || id.startsWith('process_cell_power_port')) {
      writeFileSync(join(ROOT, manifest.output.texturesBlocksDir, `${id}.png`), blockTextures[id]);
    }
  }

  // Write item models
  ensureDir(join(ROOT, manifest.output.itemsDir));
  for (const id of manifest.output.itemModelsToUpdate) {
    writeItemModel(join(ROOT, manifest.output.itemsDir), id);
  }

  // Write block models
  ensureDir(join(ROOT, manifest.output.blocksDir));
  for (const id of manifest.output.blockModelsToUpdate) {
    writeBlockModel(join(ROOT, manifest.output.blocksDir), id, manifest);
  }

  // Write preview atlas
  const previewPng = generatePreviewAtlas(manifest, itemTextures, blockTextures);
  ensureDir(dirname(join(ROOT, manifest.output.previewPath)));
  writeFileSync(join(ROOT, manifest.output.previewPath), previewPng);

  console.log('Generation complete.');
  console.log(`  Items: ${manifest.output.itemModelsToUpdate.length} textures + models`);
  console.log(`  Blocks: ${manifest.output.blockModelsToUpdate.length} textures + models`);
  console.log(`  Preview: ${manifest.output.previewPath}`);
}

const args = process.argv.slice(2);
const checkOnly = args.includes('--check');
generate(checkOnly).catch(err => {
  console.error(err);
  process.exit(1);
});