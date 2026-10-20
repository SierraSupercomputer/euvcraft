#!/usr/bin/env node
/**
 * EUVCRAFT M1 Joined Panel Texture Generator (Node >= 22, built-ins only).
 *
 * Reads tools/starter-textures.json (schema v2 extended with joinedPanels section) and:
 *  - rasterizes 48 joined panel block face tiles (4 roles × 12 parts × 1 face each, shared where appropriate)
 *  - emits deterministic 16×16 RGBA8 PNGs (filter 0, level-9 deflate, unsigned CRC32)
 *  - writes 48 block models (cube parent with 6 faces + particle) + 1 item model
 *  - writes blockstates for all 4 roles × 12 parts × 4 facings × 2 formed states
 *  - --check mode compares shipped assets without writing; preview ignored
 *
 * Writes are limited to validated output paths; nothing deleted outside them.
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { resolve, dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { deflateSync, crc32 } from 'zlib';
import { createHash } from 'crypto';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Joined panel geometry: fixed 2×2×3, u=0..1, v=0..1, d=0..2, part = u*6 + v*3 + d (0..11)
const ROLES = ['controller', 'casing', 'power_port', 'operating_panel'];
const U_MAX = 2, V_MAX = 2, D_MAX = 3;
const PART_COUNT = U_MAX * V_MAX * D_MAX; // 12
const FACINGS = ['north', 'south', 'east', 'west'];
const FACING_ROT = { north: 0, east: 90, south: 180, west: 270 };

// Exact expected production asset sets for joined panels.
const EXPECTED_JOINED_BLOCK_IDS = (() => {
  const ids = [];
  for (const role of ROLES) {
    for (let part = 0; part < PART_COUNT; part++) {
      ids.push(`process_cell_${role}_part${part}_formed`);
      ids.push(`process_cell_${role}_part${part}_unformed`);
    }
  }
  return ids;
})();
// 4 roles × 12 parts × 2 states = 96 block model IDs

// Only writable destinations. Hardcoded; manifest values must match exactly.
const EXPECTED_OUTPUT = {
  blocksDir: 'src/main/resources/assets/lithmod/models/block',
  blockstatesDir: 'src/main/resources/assets/lithmod/blockstates',
  texturesBlocksDir: 'src/main/resources/assets/lithmod/textures/block',
  itemsDir: 'src/main/resources/assets/lithmod/models/item',
  itemsDefinitionsDir: 'src/main/resources/assets/lithmod/items',
  texturesItemsDir: 'src/main/resources/assets/lithmod/textures/item',
  previewPath: 'build/joined-panels-preview.png',
  lootTablesDir: 'src/main/resources/data/lithmod/loot_table/blocks',
  recipesDir: 'src/main/resources/data/lithmod/recipe/crafting',
};

function fail(errors) {
  throw new Error('Validation failed:\n' + errors.join('\n'));
}

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

function validateJoinedManifest(m) {
  const errors = [];
  if (m.version !== 2) errors.push(`Manifest version must be 2, got ${m.version}`);
  if (!m.joinedPanels) errors.push('joinedPanels section missing');

  const jp = m.joinedPanels;

  // Validate palettes
  for (const [name, slots] of Object.entries(jp.palette || {})) {
    if (typeof slots !== 'object' || slots === null || Array.isArray(slots)) {
      errors.push(`Joined palette ${name} must be an object of named slots`);
      continue;
    }
    for (const [slot, hex] of Object.entries(slots)) {
      try { hexToRgba(hex); } catch (e) { errors.push(`Joined palette ${name}.${slot}: ${e.message}`); }
    }
  }

  // Validate shapes
  if (!jp.shapes || typeof jp.shapes !== 'object') errors.push('joinedPanels.shapes missing');
  else {
    for (const [name, ops] of Object.entries(jp.shapes)) {
      if (!Array.isArray(ops)) errors.push(`Joined shape ${name} must be an array of ops`);
    }
  }

  // Validate tile definitions
  if (!jp.tiles || typeof jp.tiles !== 'object') errors.push('joinedPanels.tiles missing');
  else {
    for (const [tileId, def] of Object.entries(jp.tiles)) {
      if (!def.palette || !jp.palette[def.palette]) errors.push(`Tile ${tileId}: unknown palette ${def.palette}`);
      if (!def.shape || !jp.shapes[def.shape]) errors.push(`Tile ${tileId}: unknown shape ${def.shape}`);
    }
  }

  // Validate face mappings
  if (!jp.faceMap || typeof jp.faceMap !== 'object') errors.push('joinedPanels.faceMap missing');
  else {
    for (const [role, faces] of Object.entries(jp.faceMap)) {
      if (!ROLES.includes(role)) errors.push(`faceMap unknown role: ${role}`);
      for (const [face, tileId] of Object.entries(faces)) {
        if (!['north', 'south', 'east', 'west', 'up', 'down'].includes(face)) {
          errors.push(`faceMap ${role}.${face}: invalid face name`);
        }
        if (!jp.tiles[tileId]) errors.push(`faceMap ${role}.${face}: unknown tile ${tileId}`);
      }
    }
  }

  // Validate role assignments per part
  if (!jp.roleAssignments || typeof jp.roleAssignments !== 'object') errors.push('joinedPanels.roleAssignments missing');
  else {
    for (const [partStr, role] of Object.entries(jp.roleAssignments)) {
      const part = parseInt(partStr, 10);
      if (!Number.isInteger(part) || part < 0 || part >= PART_COUNT) {
        errors.push(`roleAssignments invalid part: ${partStr}`);
      }
      if (!ROLES.includes(role)) errors.push(`roleAssignments part ${part}: unknown role ${role}`);
    }
    // Must have all 12 parts assigned
    if (Object.keys(jp.roleAssignments).length !== PART_COUNT) {
      errors.push(`roleAssignments must have exactly ${PART_COUNT} entries`);
    }
  }

  // Validate unformed tile overrides
  if (!jp.unformedTiles || typeof jp.unformedTiles !== 'object') errors.push('joinedPanels.unformedTiles missing');
  else {
    for (const [role, tileId] of Object.entries(jp.unformedTiles)) {
      if (!ROLES.includes(role)) errors.push(`unformedTiles unknown role: ${role}`);
      if (!jp.tiles[tileId]) errors.push(`unformedTiles ${role}: unknown tile ${tileId}`);
    }
  }

  // Validate output paths
  for (const [key, expected] of Object.entries(EXPECTED_OUTPUT)) {
    const value = jp.output?.[key];
    if (value !== expected) {
      errors.push(`joinedPanels.output.${key} must be exactly "${expected}", got ${JSON.stringify(value)}`);
      continue;
    }
    if (/^[A-Za-z]:|^[/\\]|\.\.|[\\]/.test(value) && key !== 'previewPath') {
      errors.push(`joinedPanels.output.${key} is not a safe relative path: ${value}`);
    }
  }

  // Validate blockModelsToUpdate matches expected
  errors.push(...validateLists(jp.blocks, jp.output?.blockModelsToUpdate, 'joinedPanels.blockModelsToUpdate', EXPECTED_JOINED_BLOCK_IDS));

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
  if ([...minXY, ...maxXY].some((c) => c < 0 || c > 15)) {
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

/** Block canvas must end fully opaque; transparency in block faces is a validation error. */
function rasterizeBlockFace(tileId, def, palettes, shapes) {
  const img = new Uint8Array(16 * 16 * 4);
  for (const op of shapes[def.shape]) drawOp(img, op, palettes[def.palette], `joined tile ${tileId}`);
  for (let i = 3; i < img.length; i += 4) {
    if (img[i] !== 255) {
      fail([`Joined tile ${tileId} must be fully opaque; transparent pixels are not allowed`]);
    }
  }
  return img;
}

function chunk(type, data) {
  const head = Buffer.alloc(4);
  head.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(body) >>> 0, 0);
  return Buffer.concat([head, body, crcBuf]);
}

/** Encode raw RGBA to PNG: signature + IHDR + filter-0 IDAT + IEND. Level-9 deflate. */
function encodePng(rgba, width, height) {
  const stride = width * 4 + 1;
  const raw = Buffer.alloc(stride * height);
  const src = Buffer.from(rgba);
  for (let y = 0; y < height; y++) {
    raw[y * stride] = 0;
    src.copy(raw, y * stride + 1, y * width * 4, (y + 1) * width * 4);
  }
  const compressed = deflateSync(raw, { level: 9 });
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
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

/** Compute UV tile coordinate for a given face and part (u,v,d) per FaceBakery canonical north. */
function computeTileUV(face, u, v, d) {
  // FaceBakery defaultUV (canonical north-facing local coords):
  // north: x=1-u, y=1-v (32x32 face, so u,v in 0..1 map to 0..15)
  // south: x=u, y=1-v
  // west:  x=d, y=1-v (48x32, so d in 0..2 maps to 0..15)
  // east:  x=2-d, y=1-v
  // up:    x=u, y=d (32x48)
  // down:  x=u, y=2-d
  // Our tiles are 16x16, so the 32x32/48x32/32x48 regions are composed of multiple tiles.
  // Each tile covers a 16x16 region of the face.
  //
  // For a 2×2×3 structure:
  // - North/South faces: 32×32 = 2×2 tiles (u=0..1, v=0..1 each 16×16)
  // - East/West faces: 48×32 = 3×2 tiles (d=0..2, v=0..1 each 16×16)
  // - Up/Down faces: 32×48 = 2×3 tiles (u=0..1, d=0..2 each 16×16)
  //
  // The tile at local (u,v,d) contributes to:
  // - North face: tile at (u, v) on the north face
  // - South face: tile at (u, v) on the south face
  // - West face: tile at (d, v) on the west face
  // - East face: tile at (d, v) on the east face
  // - Up face: tile at (u, d) on the up face
  // - Down face: tile at (u, d) on the down face

  switch (face) {
    case 'north': return { tileU: u, tileV: v };           // x=1-u -> tileU=u, y=1-v -> tileV=v
    case 'south': return { tileU: 1 - u, tileV: v };       // x=u -> tileU=1-u (flipped), y=1-v -> tileV=v
    case 'west':  return { tileU: d, tileV: v };           // x=d -> tileU=d, y=1-v -> tileV=v
    case 'east':  return { tileU: 2 - d, tileV: v };       // x=2-d -> tileU=2-d, y=1-v -> tileV=v
    case 'up':    return { tileU: u, tileV: d };           // x=u -> tileU=u, y=d -> tileV=d
    case 'down':  return { tileU: u, tileV: 2 - d };       // x=u -> tileU=u, y=2-d -> tileV=2-d
  }
}

/** Build the 6-face texture mapping for a specific part (u,v,d) and role. */
function buildFaceTextures(jp, role, part, formed) {
  const u = Math.floor(part / 6);
  const v = Math.floor((part % 6) / 3);
  const d = part % 3;

  const tileId = formed ? jp.faceMap[role] : jp.unformedTiles[role];
  // faceMap gives the tile for each face for formed state
  // unformedTiles gives a single tile used for all faces in unformed state

  const faces = {};
  for (const face of ['north', 'south', 'east', 'west', 'up', 'down']) {
    const tile = formed ? jp.faceMap[role][face] : jp.unformedTiles[role];
    const uv = computeTileUV(face, u, v, d);
    // The texture name encodes the tile and its position on the face
    faces[face] = `lithmod:block/joined_${tile}_${face}_u${uv.tileU}v${uv.tileV}`;
  }
  return faces;
}

/** Compute all output buffers before any write/check. */
function computeAll(jp) {
  const palettes = {};
  for (const [name, slots] of Object.entries(jp.palette)) {
    palettes[name] = {};
    for (const [slot, hex] of Object.entries(slots)) palettes[name][slot] = hexToRgba(hex);
  }
  const joinedPalettes = {};
  for (const [name, slots] of Object.entries(jp.joinedPanels.palette)) {
    joinedPalettes[name] = {};
    for (const [slot, hex] of Object.entries(slots)) joinedPalettes[name][slot] = hexToRgba(hex);
  }
  const allPalettes = { ...palettes, ...joinedPalettes };

  const shapes = { ...jp.shapes, ...jp.joinedPanels.shapes };

  const raw = new Map();   // relpath -> 16x16 RGBA array
  const pngs = new Map();  // relpath -> Buffer
  const models = new Map(); // relpath -> string (LF-only JSON text)
  const blockstates = new Map(); // relpath -> string
  const lootTables = new Map();  // relpath -> string
  const recipes = new Map();     // relpath -> string

  // 1. Rasterize all joined tiles (each tile used on specific face positions)
  // Tiles are shared across parts - we generate one texture per tile per face position (u/v/d combo)
  // Actually, we need a texture for each tile × face × tile-position-on-face
  // But tiles are reused. Let's generate textures per tile (16x16) and the models reference them.

  for (const [tileId, def] of Object.entries(jp.joinedPanels.tiles)) {
    const rel = `${jp.joinedPanels.output.texturesBlocksDir}/joined_${tileId}.png`;
    raw.set(rel, rasterizeBlockFace(tileId, def, allPalettes, shapes));
    pngs.set(rel, encodePng(raw.get(rel), 16, 16));
  }

  // 2. Generate block models for each role×part×state
  for (const role of ROLES) {
    for (let part = 0; part < PART_COUNT; part++) {
      for (const state of ['formed', 'unformed']) {
        const blockId = `process_cell_${role}_part${part}_${state}`;
        const formed = state === 'formed';
        const faceTextures = buildFaceTextures(jp, role, part, formed);

        const model = {
          parent: 'minecraft:block/cube',
          textures: {
            down: faceTextures.down,
            up: faceTextures.up,
            north: faceTextures.north,
            south: faceTextures.south,
            west: faceTextures.west,
            east: faceTextures.east,
            particle: faceTextures.north,
          },
        };
        models.set(`${jp.joinedPanels.output.blocksDir}/${blockId}.json`, jsonText(model));
      }
    }
  }

  // 3. Generate blockstates for each role
  for (const role of ROLES) {
    const variants = {};
    for (let part = 0; part < PART_COUNT; part++) {
      for (const facing of FACINGS) {
        // formed=false ignores part and facing, uses single unformed model
        variants[`facing=${facing},formed=false,part=${part}`] = {
          model: `lithmod:block/process_cell_${role}_part${part}_unformed`,
        };
        // formed=true uses formed model with rotation
        const yRot = FACING_ROT[facing];
        variants[`facing=${facing},formed=true,part=${part}`] = {
          model: `lithmod:block/process_cell_${role}_part${part}_formed`,
          y: yRot,
        };
      }
    }
    blockstates.set(
      `${jp.joinedPanels.output.blockstatesDir}/process_cell_${role}.json`,
      jsonText({ variants })
    );
  }

  // 4. Item model for operating_panel (the only one with an item form)
  const itemId = 'process_cell_operating_panel';
  models.set(`${jp.joinedPanels.output.itemsDir}/${itemId}.json`, jsonText({
    parent: 'minecraft:item/generated',
    textures: { layer0: `lithmod:item/${itemId}` },
  }));

  // 4b. Item definition (client-side) for operating_panel
  models.set(`${jp.joinedPanels.output.itemsDefinitionsDir}/${itemId}.json`, jsonText({
    model: {
      type: 'minecraft:model',
      model: `lithmod:item/${itemId}`,
    },
  }));
  // Item texture - use the operating_panel unformed front tile as item icon
  // We'll create a dedicated item texture tile
  const itemTextureRel = `${jp.joinedPanels.output.texturesItemsDir}/${itemId}.png`;
  // For now, rasterize a simple item texture using the operating_panel unformed palette
  // This should be a distinct sprite, not a block face
  const itemImg = new Uint8Array(16 * 16 * 4);
  const itemPalette = allPalettes[jp.joinedPanels.tiles[jp.joinedPanels.unformedTiles.operating_panel].palette];
  const itemShape = jp.joinedPanels.shapes[jp.joinedPanels.tiles[jp.joinedPanels.unformedTiles.operating_panel].shape];
  // Draw item with transparent background
  for (const op of itemShape) drawOp(itemImg, op, itemPalette, `item ${itemId}`);
  // Ensure transparent background
  let hasTransparent = false;
  for (let i = 3; i < itemImg.length; i += 4) {
    if (itemImg[i] === 0) { hasTransparent = true; break; }
  }
  if (!hasTransparent) fail([`Item ${itemId} would fully cover the canvas`]);
  raw.set(itemTextureRel, itemImg);
  pngs.set(itemTextureRel, encodePng(itemImg, 16, 16));

  // 5. Loot table for operating_panel (drops itself)
  const lootTable = {
    type: 'minecraft:block',
    pools: [{
      rolls: 1,
      entries: [{
        type: 'minecraft:item',
        name: `lithmod:${itemId}`,
      }],
      conditions: [{ condition: 'minecraft:survives_explosion' }],
    }],
    random_sequence: `lithmod:blocks/${itemId}`,
  };
  lootTables.set(`${jp.joinedPanels.output.lootTablesDir}/${itemId}.json`, jsonText(lootTable));

  // 6. Crafting recipe for operating_panel (using vanilla M1 materials)
  // Recipe: 3 iron ingots + 2 redstone + 1 copper ingot + 1 glass + 1 quartz
  const recipe = {
    type: 'minecraft:crafting_shaped',
    category: 'equipment',
    key: {
      I: 'minecraft:iron_ingot',
      R: 'minecraft:redstone',
      C: 'minecraft:copper_ingot',
      G: 'minecraft:glass',
      Q: 'minecraft:quartz',
    },
    pattern: [
      'IRC',
      'RGR',
      'CRI',
    ],
    result: { id: `lithmod:${itemId}`, count: 1 },
  };
  recipes.set(`${jp.joinedPanels.output.recipesDir}/${itemId}.json`, jsonText(recipe));

  // 7. Preview atlas (6x scale, 8 columns)
  const previewOrder = [];
  // Add all tile textures in deterministic order
  for (const tileId of Object.keys(jp.joinedPanels.tiles).sort()) {
    previewOrder.push(`${jp.joinedPanels.output.texturesBlocksDir}/joined_${tileId}.png`);
  }
  // Add item texture
  previewOrder.push(itemTextureRel);
  const preview = buildPreview(previewOrder.map(p => raw.get(p)));
  pngs.set(jp.joinedPanels.output.previewPath, preview.png);

  return { raw, pngs, models, blockstates, lootTables, recipes, preview };
}

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

function writeAll(jp, computed) {
  const dirs = new Set([
    jp.joinedPanels.output.blocksDir,
    jp.joinedPanels.output.blockstatesDir,
    jp.joinedPanels.output.texturesBlocksDir,
    jp.joinedPanels.output.itemsDir,
    jp.joinedPanels.output.texturesItemsDir,
    jp.joinedPanels.output.lootTablesDir,
    jp.joinedPanels.output.recipesDir,
    dirname(jp.joinedPanels.output.previewPath),
  ]);
  for (const dir of dirs) {
    const abs = join(ROOT, dir);
    if (!existsSync(abs)) mkdirSync(abs, { recursive: true });
  }
  for (const [rel, buf] of computed.pngs) writeFileSync(join(ROOT, rel), buf);
  for (const [rel, text] of computed.models) writeFileSync(join(ROOT, rel), text, 'utf8');
  for (const [rel, text] of computed.blockstates) writeFileSync(join(ROOT, rel), text, 'utf8');
  for (const [rel, text] of computed.lootTables) writeFileSync(join(ROOT, rel), text, 'utf8');
  for (const [rel, text] of computed.recipes) writeFileSync(join(ROOT, rel), text, 'utf8');
  writeFileSync(join(ROOT, jp.joinedPanels.output.previewPath), computed.preview.png);
  console.log(`Wrote ${computed.pngs.size} PNGs, ${computed.models.size} models, ${computed.blockstates.size} blockstates, ${computed.lootTables.size} loot tables, ${computed.recipes.size} recipes, preview ${jp.joinedPanels.output.previewPath} (${computed.preview.width}x${computed.preview.height}).`);
}

function checkAll(jp, computed) {
  let bad = 0;
  for (const [rel, buf] of computed.pngs) {
    if (rel === jp.joinedPanels.output.previewPath) continue; // preview not checked
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
  for (const [rel, text] of computed.blockstates) {
    const path = join(ROOT, rel);
    if (!existsSync(path)) { console.log(`MISSING: ${rel}`); bad++; continue; }
    if (normalize(readFileSync(path, 'utf8')) !== normalize(text)) {
      console.log(`STALE: ${rel}`); bad++;
    }
  }
  for (const [rel, text] of computed.lootTables) {
    const path = join(ROOT, rel);
    if (!existsSync(path)) { console.log(`MISSING: ${rel}`); bad++; continue; }
    if (normalize(readFileSync(path, 'utf8')) !== normalize(text)) {
      console.log(`STALE: ${rel}`); bad++;
    }
  }
  for (const [rel, text] of computed.recipes) {
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
  console.log(`Check PASSED: ${computed.pngs.size} PNGs, ${computed.models.size} models, ${computed.blockstates.size} blockstates, ${computed.lootTables.size} loot tables, ${computed.recipes.size} recipes match (preview not checked).`);
}

function loadManifest() {
  return JSON.parse(readFileSync(join(ROOT, 'tools', 'starter-textures.json'), 'utf8'));
}

function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== '--check')) {
    console.error('Usage: node tools/generate-joined-panels.mjs [--check]');
    process.exit(2);
  }
  const manifest = loadManifest();
  validateManifest(manifest);
  validateJoinedManifest(manifest);
  const computed = computeAll(manifest.joinedPanels);
  if (args[0] === '--check') checkAll(manifest.joinedPanels, computed);
  else writeAll(manifest.joinedPanels, computed);
}

main();