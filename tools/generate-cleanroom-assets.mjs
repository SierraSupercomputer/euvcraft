#!/usr/bin/env node
/**
 * EUVCRAFT M1 Cleanroom Asset Generator (Node >= 22, built-ins only: fs/path/zlib/crypto).
 *
 * Reads tools/cleanroom-manifest.json (schema: palette, shapes, items, blocks, recipes, armor)
 * and generates deterministic 16x16 PNGs + JSON assets for cleanroom blocks/items/armor.
 *
 * Writes are limited to validated output paths under:
 *   src/main/resources/assets/lithmod/
 *   src/main/resources/data/lithmod/recipe/crafting/cleanroom_
 *   src/main/resources/data/lithmod/loot_table/blocks/cleanroom_
 *
 * Supports --check mode (no writes; stale/missing fails).
 * Exports translation map to stdout (coordinator merges into en_us.json).
 * Generates report at docs/reports/Cleanroom_assets.md.
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { resolve, dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { deflateSync, crc32 } from 'zlib';
import { createHash } from 'crypto';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Fixed NEW IDs (no others) - 9 block items + 1 item + 4 armor items
const CLEANROOM_BLOCK_IDS = [
  'cleanroom_floor',
  'cleanroom_wall',
  'cleanroom_window',
  'cleanroom_door',
  'cleanroom_controller',
  'cleanroom_filter',
  'cleanroom_power_port',
  'cleanroom_fluid_port',
  'cleanroom_item_port',
];

const CLEANROOM_ITEM_IDS = [
  'cleanroom_filter_medium',
];

const CLEANROOM_ARMOR_IDS = [
  'cleanroom_hood',
  'cleanroom_suit',
  'cleanroom_trousers',
  'cleanroom_boots',
];

const ALL_ITEM_IDS = [...CLEANROOM_BLOCK_IDS, ...CLEANROOM_ITEM_IDS, ...CLEANROOM_ARMOR_IDS];

// Door block needs special handling - uses minecraft:block/door_* templates
const DOOR_ID = 'cleanroom_door';
const DOOR_VARIANTS = [
  'bottom_left', 'bottom_right', 'top_left', 'top_right',
  'bottom_left_open', 'bottom_right_open', 'top_left_open', 'top_right_open',
];

// Expected output paths (validated)
const EXPECTED_OUTPUT = {
  itemsDir: 'src/main/resources/assets/lithmod/models/item',
  blocksDir: 'src/main/resources/assets/lithmod/models/block',
  blockstatesDir: 'src/main/resources/assets/lithmod/blockstates',
  texturesItemsDir: 'src/main/resources/assets/lithmod/textures/item',
  texturesBlocksDir: 'src/main/resources/assets/lithmod/textures/block',
  texturesEntityDir: 'src/main/resources/assets/lithmod/textures/entity',
  lootTablesDir: 'src/main/resources/data/lithmod/loot_table/blocks',
  recipesDir: 'src/main/resources/data/lithmod/recipe/crafting',
  reportPath: 'docs/reports/Cleanroom_assets.md',
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

function validateManifest(m) {
  const errors = [];
  if (m.version !== 1) errors.push(`Manifest version must be 1, got ${m.version}`);

  // Validate palette
  if (!m.palette || typeof m.palette !== 'object') errors.push('palette missing');
  else {
    for (const [name, slots] of Object.entries(m.palette)) {
      if (typeof slots !== 'object' || slots === null || Array.isArray(slots)) {
        errors.push(`Palette ${name} must be an object of named slots`);
        continue;
      }
      for (const [slot, hex] of Object.entries(slots)) {
        try { hexToRgba(hex); } catch (e) { errors.push(`Palette ${name}.${slot}: ${e.message}`); }
      }
    }
  }

  // Validate shapes
  if (!m.shapes || typeof m.shapes !== 'object') errors.push('shapes missing');
  else {
    for (const [name, ops] of Object.entries(m.shapes)) {
      if (!Array.isArray(ops)) errors.push(`Shape ${name} must be an array of ops`);
    }
  }

  // Validate items
  if (!m.items || typeof m.items !== 'object') errors.push('items missing');
  else {
    for (const [id, def] of Object.entries(m.items)) {
      if (!m.palette[def.palette]) errors.push(`Item ${id}: unknown palette ${def.palette}`);
      if (!m.shapes[def.shape]) errors.push(`Item ${id}: unknown shape ${def.shape}`);
    }
  }

  // Validate blocks
  if (!m.blocks || typeof m.blocks !== 'object') errors.push('blocks missing');
  else {
    for (const [id, def] of Object.entries(m.blocks)) {
      if (!def.faces) errors.push(`Block ${id} missing faces`);
      else {
        for (const [face, faceDef] of Object.entries(def.faces)) {
          if (!m.palette[faceDef.palette]) errors.push(`Block ${id}.${face}: unknown palette ${faceDef.palette}`);
          if (!m.shapes[faceDef.shape]) errors.push(`Block ${id}.${face}: unknown shape ${faceDef.shape}`);
        }
      }
    }
  }

  // Validate recipes
  if (!m.recipes || typeof m.recipes !== 'object') errors.push('recipes missing');
  else {
    for (const [id, recipe] of Object.entries(m.recipes)) {
      if (!recipe.type || !recipe.pattern || !recipe.key || !recipe.result) {
        errors.push(`Recipe ${id} missing required fields`);
      }
    }
  }

  // Validate armor
  if (!m.armor || typeof m.armor !== 'object') errors.push('armor missing');
  else {
    for (const [id, def] of Object.entries(m.armor)) {
      if (!def.material || !def.slots) {
        errors.push(`Armor ${id} missing material or slots`);
      }
      if (!m.palette[def.palette]) errors.push(`Armor ${id}: unknown palette ${def.palette}`);
      if (!m.shapes[def.shape]) errors.push(`Armor ${id}: unknown shape ${def.shape}`);
    }
  }

  // Validate output paths
  for (const [key, expected] of Object.entries(EXPECTED_OUTPUT)) {
    const value = m.output?.[key];
    if (value !== expected) {
      errors.push(`output.${key} must be exactly "${expected}", got ${JSON.stringify(value)}`);
    }
  }

  if (errors.length) fail(errors);
}

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

function rasterizeItem(id, def, palettes, shapes) {
  const img = new Uint8Array(16 * 16 * 4);
  for (const op of shapes[def.shape]) drawOp(img, op, palettes[def.palette], `item ${id}`);
  // Item canvas must have at least one transparent pixel
  for (let i = 3; i < img.length; i += 4) {
    if (img[i] === 0) return img;
  }
  fail([`Item ${id} would fully cover the canvas; at least one transparent pixel is required`]);
}

function rasterizeBlockFace(id, face, def, palettes, shapes) {
  const img = new Uint8Array(16 * 16 * 4);
  for (const op of shapes[def.shape]) drawOp(img, op, palettes[def.palette], `block ${id}.${face}`);
  // Block faces must be fully opaque
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
  crcBuf.writeUInt32BE(crc32(body) >>> 0, 0);
  return Buffer.concat([head, body, crcBuf]);
}

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

function itemModelObject(id) {
  return { parent: 'minecraft:item/generated', textures: { layer0: `lithmod:item/${id}` } };
}

function blockModelCubeAll(id) {
  return {
    parent: 'minecraft:block/cube_all',
    textures: { all: `lithmod:block/${id}` },
  };
}

function blockModelOrientable(id) {
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

// Door block models using vanilla door templates
function doorBlockModels(id) {
  const models = {};
  // Bottom left (hinge left, bottom half)
  models[`${id}_bottom_left`] = {
    parent: 'minecraft:block/door_bottom_left',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_bottom_right`] = {
    parent: 'minecraft:block/door_bottom_right',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_top_left`] = {
    parent: 'minecraft:block/door_top_left',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_top_right`] = {
    parent: 'minecraft:block/door_top_right',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_bottom_left_open`] = {
    parent: 'minecraft:block/door_bottom_left_open',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_bottom_right_open`] = {
    parent: 'minecraft:block/door_bottom_right_open',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_top_left_open`] = {
    parent: 'minecraft:block/door_top_left_open',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  models[`${id}_top_right_open`] = {
    parent: 'minecraft:block/door_top_right_open',
    textures: {
      top: `lithmod:block/${id}_top`,
      bottom: `lithmod:block/${id}_bottom`,
    },
  };
  return models;
}

function doorBlockstates(id) {
  const variants = {};
  const facings = ['north', 'east', 'south', 'west'];
  const hinges = ['left', 'right'];
  const halves = ['lower', 'upper'];
  const opens = [false, true];
  
  for (const facing of facings) {
    for (const hinge of hinges) {
      for (const half of halves) {
        for (const open of opens) {
          const key = `facing=${facing},half=${half},hinge=${hinge},open=${open}`;
          let model = `${id}_`;
          model += half === 'lower' ? 'bottom_' : 'top_';
          model += hinge;
          if (open) model += '_open';
          
          let y = 0;
          if (facing === 'east') y = 90;
          else if (facing === 'south') y = 180;
          else if (facing === 'west') y = 270;
          
          variants[key] = y ? { model: `lithmod:block/${model}`, y } : { model: `lithmod:block/${model}` };
        }
      }
    }
  }
  return { variants };
}

function blockstatesOrientable(id) {
  const variants = {};
  const facings = ['north', 'east', 'south', 'west'];
  for (const facing of facings) {
    let y = 0;
    if (facing === 'east') y = 90;
    else if (facing === 'south') y = 180;
    else if (facing === 'west') y = 270;
    variants[`facing=${facing}`] = y ? { model: `lithmod:block/${id}`, y } : { model: `lithmod:block/${id}` };
  }
  return { variants };
}

function blockstatesSimple(id) {
  return { variants: { '': { model: `lithmod:block/${id}` } } };
}

function lootTableBlock(id) {
  return {
    type: 'minecraft:block',
    pools: [{
      rolls: 1,
      entries: [{ type: 'minecraft:item', name: `lithmod:${id}` }],
      conditions: [{ condition: 'minecraft:survives_explosion' }],
    }],
    random_sequence: `lithmod:blocks/${id}`,
  };
}

function lootTableDoor(id) {
  return {
    type: 'minecraft:block',
    pools: [{
      rolls: 1,
      entries: [{ type: 'minecraft:item', name: `lithmod:${id}` }],
      conditions: [
        { condition: 'minecraft:survives_explosion' },
        { condition: 'minecraft:block_state_property', block: `lithmod:${id}`, properties: { half: 'lower' } },
      ],
    }],
    random_sequence: `lithmod:blocks/${id}`,
  };
}

function armorTrimModel(id, layer, type) {
  // Modern equipment assets reference vanilla leather armor layers
  // We generate simple references to native vanilla leather equipment
  return {
    parent: `minecraft:item/armor/${type}_layer_${layer}`,
    textures: {
      layer0: `minecraft:item/leather_layer_${layer}`,
      layer1: `lithmod:item/${id}_overlay`,
    },
  };
}

function armorItemModel(id, slot) {
  // Use vanilla leather armor as base with overlay reference
  const typeMap = { helmet: 'helmet', chestplate: 'chestplate', leggings: 'leggings', boots: 'boots' };
  const type = typeMap[slot] || slot;
  return {
    parent: 'minecraft:item/generated',
    textures: { layer0: `lithmod:item/${id}` },
    overrides: [
      { predicate: { damaged: 0, damage: 0 }, model: `lithmod:item/${id}` },
    ],
  };
}

function equipmentAsset(id, slot) {
  // Modern equipment system: humanoid / humanoid_leggings layers
  // Reference vanilla leather equipment textures
  const layer = slot === 'leggings' ? 'humanoid_leggings' : 'humanoid';
  return {
    asset_key: `lithmod:${id}`,
    type: slot,
    texture: `minecraft:textures/entity/armor/leather_layer_${layer === 'humanoid_leggings' ? 2 : 1}.png`,
    overlay: `lithmod:textures/entity/${id}_overlay.png`,
  };
}

function craftingRecipe(id, recipe) {
  return {
    type: recipe.type,
    category: recipe.category || 'building',
    key: recipe.key,
    pattern: recipe.pattern,
    result: { id: `lithmod:${recipe.result.id}`, count: recipe.result.count || 1 },
  };
}

function buildTranslations(manifest) {
  const translations = {};
  for (const id of ALL_ITEM_IDS) {
    const name = id.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    translations[`item.lithmod.${id}`] = name;
    translations[`block.lithmod.${id}`] = name;
  }
  return translations;
}

function computeAll(manifest) {
  const palettes = {};
  for (const [name, slots] of Object.entries(manifest.palette)) {
    palettes[name] = {};
    for (const [slot, hex] of Object.entries(slots)) palettes[name][slot] = hexToRgba(hex);
  }
  const raw = new Map();
  const pngs = new Map();
  const models = new Map();
  const blockstates = new Map();
  const lootTables = new Map();
  const recipes = new Map();
  const equipment = new Map();

  // Items (blocks + items)
  for (const id of [...CLEANROOM_BLOCK_IDS, ...CLEANROOM_ITEM_IDS]) {
    const def = manifest.items[id];
    const rel = `${manifest.output.texturesItemsDir}/${id}.png`;
    raw.set(rel, rasterizeItem(id, def, palettes, manifest.shapes));
    pngs.set(rel, encodePng(raw.get(rel), 16, 16));
    models.set(`${manifest.output.itemsDir}/${id}.json`, jsonText(itemModelObject(id)));
  }

  // Armor items
  for (const id of CLEANROOM_ARMOR_IDS) {
    const def = manifest.armor[id];
    const rel = `${manifest.output.texturesItemsDir}/${id}.png`;
    raw.set(rel, rasterizeItem(id, { palette: def.palette, shape: def.shape }, palettes, manifest.shapes));
    pngs.set(rel, encodePng(raw.get(rel), 16, 16));
    models.set(`${manifest.output.itemsDir}/${id}.json`, jsonText(armorItemModel(id, def.slots[0])));
  }

  // Block textures and models
  for (const id of CLEANROOM_BLOCK_IDS) {
    const def = manifest.blocks[id];
    if (id === DOOR_ID) {
      // Door: generate top/bottom textures + 8 model variants
      const topRel = `${manifest.output.texturesBlocksDir}/${id}_top.png`;
      const bottomRel = `${manifest.output.texturesBlocksDir}/${id}_bottom.png`;
      raw.set(topRel, rasterizeBlockFace(id, 'top', def.faces.top, palettes, manifest.shapes));
      pngs.set(topRel, encodePng(raw.get(topRel), 16, 16));
      raw.set(bottomRel, rasterizeBlockFace(id, 'bottom', def.faces.bottom, palettes, manifest.shapes));
      pngs.set(bottomRel, encodePng(raw.get(bottomRel), 16, 16));
      
      // Door model variants
      const doorModels = doorBlockModels(id);
      for (const [modelId, modelObj] of Object.entries(doorModels)) {
        models.set(`${manifest.output.blocksDir}/${modelId}.json`, jsonText(modelObj));
      }
      
      // Door blockstates
      blockstates.set(`${manifest.output.blockstatesDir}/${id}.json`, jsonText(doorBlockstates(id)));
      
      // Door loot table (lower half only)
      lootTables.set(`${manifest.output.lootTablesDir}/${id}.json`, jsonText(lootTableDoor(id)));
    } else if (CLEANROOM_BLOCK_IDS.includes(id) && (id === 'cleanroom_controller' || id === 'cleanroom_filter')) {
      // Orientable blocks (controller, filter) - side, top, front
      for (const face of ['side', 'top', 'front']) {
        const file = `${id}_${face}`;
        const rel = `${manifest.output.texturesBlocksDir}/${file}.png`;
        raw.set(rel, rasterizeBlockFace(id, face, def.faces[face], palettes, manifest.shapes));
        pngs.set(rel, encodePng(raw.get(rel), 16, 16));
      }
      models.set(`${manifest.output.blocksDir}/${id}.json`, jsonText(blockModelOrientable(id)));
      blockstates.set(`${manifest.output.blockstatesDir}/${id}.json`, jsonText(blockstatesOrientable(id)));
      lootTables.set(`${manifest.output.lootTablesDir}/${id}.json`, jsonText(lootTableBlock(id)));
    } else {
      // Simple cube_all blocks (floor, wall, window, power_port, fluid_port, item_port)
      const rel = `${manifest.output.texturesBlocksDir}/${id}.png`;
      raw.set(rel, rasterizeBlockFace(id, 'all', def.faces.all, palettes, manifest.shapes));
      pngs.set(rel, encodePng(raw.get(rel), 16, 16));
      models.set(`${manifest.output.blocksDir}/${id}.json`, jsonText(blockModelCubeAll(id)));
      blockstates.set(`${manifest.output.blockstatesDir}/${id}.json`, jsonText(blockstatesSimple(id)));
      lootTables.set(`${manifest.output.lootTablesDir}/${id}.json`, jsonText(lootTableBlock(id)));
    }
  }

  // Armor equipment overlays (64x32 for humanoid, humanoid_leggings)
  for (const id of CLEANROOM_ARMOR_IDS) {
    const def = manifest.armor[id];
    // Generate simple 64x32 overlay texture
    const overlayRel = `${manifest.output.texturesEntityDir}/${id}_overlay.png`;
    const overlayImg = new Uint8Array(64 * 32 * 4);
    // Simple overlay: draw a light gray panel pattern
    for (let y = 0; y < 32; y++) {
      for (let x = 0; x < 64; x++) {
        const i = (y * 64 + x) * 4;
        // Light gray with subtle pattern
        overlayImg[i] = 200;
        overlayImg[i + 1] = 200;
        overlayImg[i + 2] = 200;
        overlayImg[i + 3] = (x % 8 === 0 || y % 8 === 0) ? 180 : 120;
      }
    }
    pngs.set(overlayRel, encodePng(overlayRel, 64, 32));
    
    // Equipment asset reference
    const slot = def.slots[0];
    equipment.set(`lithmod:${id}`, equipmentAsset(id, slot));
  }

  // Crafting recipes
  for (const [id, recipe] of Object.entries(manifest.recipes)) {
    const recipeId = `cleanroom_${id}`;
    recipes.set(`${manifest.output.recipesDir}/${recipeId}.json`, jsonText(craftingRecipe(recipeId, recipe)));
  }

  return { raw, pngs, models, blockstates, lootTables, recipes, equipment, translations: buildTranslations(manifest) };
}

function writeAll(manifest, computed) {
  const dirs = new Set([
    manifest.output.itemsDir, manifest.output.blocksDir, manifest.output.blockstatesDir,
    manifest.output.texturesItemsDir, manifest.output.texturesBlocksDir,
    manifest.output.texturesEntityDir, manifest.output.lootTablesDir,
    manifest.output.recipesDir, dirname(manifest.output.reportPath),
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
  
  console.log(`Wrote ${computed.pngs.size} PNGs, ${computed.models.size} models, ${computed.blockstates.size} blockstates, ${computed.lootTables.size} loot tables, ${computed.recipes.size} recipes.`);
  
  // Write report
  const report = generateReport(manifest, computed);
  writeFileSync(join(ROOT, manifest.output.reportPath), report, 'utf8');
  console.log(`Report written to ${manifest.output.reportPath}`);
  
  // Print translations for coordinator to merge
  console.log('\n=== TRANSLATIONS (coordinator merges into en_us.json) ===');
  console.log(JSON.stringify(computed.translations, null, 2));
}

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
    console.log(`Check FAILED: ${bad} asset(s) missing or stale.`);
    process.exit(1);
  }
  console.log(`Check PASSED: ${computed.pngs.size} PNGs, ${computed.models.size} models, ${computed.blockstates.size} blockstates, ${computed.lootTables.size} loot tables, ${computed.recipes.size} recipes match.`);
}

function generateReport(manifest, computed) {
  const lines = [
    '# Cleanroom Assets Generation Report',
    '',
    `Generated: ${new Date().toISOString()}`,
    `Generator: tools/generate-cleanroom-assets.mjs`,
    '',
    '## Summary',
    `- Block items: ${CLEANROOM_BLOCK_IDS.length}`,
    `- Items: ${CLEANROOM_ITEM_IDS.length}`,
    `- Armor items: ${CLEANROOM_ARMOR_IDS.length}`,
    `- Total items: ${ALL_ITEM_IDS.length}`,
    `- PNG textures: ${computed.pngs.size}`,
    `- Item/Block models: ${computed.models.size}`,
    `- Blockstates: ${computed.blockstates.size}`,
    `- Loot tables: ${computed.lootTables.size}`,
    `- Crafting recipes: ${computed.recipes.size}`,
    `- Equipment assets: ${computed.equipment.size}`,
    '',
    '## Generated IDs',
    '### Block Items (9)',
  ];
  for (const id of CLEANROOM_BLOCK_IDS) lines.push(`- ${id}`);
  lines.push('', '### Items (1)');
  for (const id of CLEANROOM_ITEM_IDS) lines.push(`- ${id}`);
  lines.push('', '### Armor (4)');
  for (const id of CLEANROOM_ARMOR_IDS) lines.push(`- ${id}`);
  
  lines.push('', '## Asset Details');
  lines.push('### Textures');
  lines.push('- All textures are 16x16 PNGs (RGBA8, filter 0, level-9 deflate)');
  lines.push('- Block textures: fully opaque');
  lines.push('- Item textures: transparent background');
  lines.push('- Door: separate top/bottom textures + 8 model variants');
  lines.push('- Armor overlays: 64x32 PNGs (humanoid/humanoid_leggings layers)');
  
  lines.push('', '### Blockstates');
  lines.push('- Simple blocks (floor, wall, window, ports): single variant');
  lines.push('- Orientable blocks (controller, filter): 4 facing variants');
  lines.push('- Door (cleanroom_door): 32 variants (facing × half × hinge × open)');
  
  lines.push('', '### Loot Tables');
  lines.push('- All blocks: survives_explosion condition');
  lines.push('- Door: additional lower-half condition (only drops from bottom half)');
  
  lines.push('', '### Crafting Recipes');
  for (const [id, recipe] of Object.entries(manifest.recipes)) {
    lines.push(`- cleanroom_${id}: ${recipe.type}, output ${recipe.result.count}x ${recipe.result.id}`);
  }
  
  lines.push('', '### Armor Equipment');
  lines.push('- References vanilla leather armor textures (no custom 3D models)');
  lines.push('- Overlay textures generated at 64x32 for humanoid/humanoid_leggings');
  lines.push('- Coordinator binds equipment asset key');
  
  lines.push('', '### Limitations');
  lines.push('- Door uses vanilla minecraft:block/door_* templates; requires those templates exist in game');
  lines.push('- Armor uses vanilla leather equipment as base; custom 3D models not generated');
  lines.push('- Window noOcclusion render layer is native concern (not in assets)');
  lines.push('- Translations printed to stdout; coordinator must merge into en_us.json');
  lines.push('- No T5/T6 dependencies; recipes use ordinary materials + logic_chip_t4 where specified');
  
  return lines.join('\n') + '\n';
}

function loadManifest() {
  return JSON.parse(readFileSync(join(ROOT, 'tools', 'cleanroom-manifest.json'), 'utf8'));
}

export function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== '--check')) {
    console.error('Usage: node tools/generate-cleanroom-assets.mjs [--check]');
    process.exit(2);
  }
  const manifest = loadManifest();
  validateManifest(manifest);
  const computed = computeAll(manifest);
  if (args[0] === '--check') checkAll(manifest, computed);
  else writeAll(manifest, computed);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();