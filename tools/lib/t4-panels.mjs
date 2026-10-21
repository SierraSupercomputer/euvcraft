// T4 Panel Generator - Pure computation, no filesystem writes or import-time execution.
// Returns maps of PNG buffers and JSON strings for all T4 panel assets.
// Uses only Node built-ins (fs/path/url/zlib/crypto not used here; only zlib for PNG).

import { deflateSync, crc32 } from 'node:zlib';

const A = 'src/main/resources/assets/lithmod/';
const D = 'src/main/resources/data/lithmod/';

// Block IDs and roles
const BLOCK_IDS = [
  'deposition_etcher',
  'advanced_test_package',
  'advanced_casing',
  'advanced_power_port',
  'advanced_operating_panel'
];

// Role mapping: each block ID maps to its functional role(s)
const BLOCK_ROLES = {
  'deposition_etcher': ['controller'],
  'advanced_test_package': ['controller'],
  'advanced_casing': ['casing'],
  'advanced_power_port': ['power_port'],
  'advanced_operating_panel': ['operating_panel']
};

// Large shapes: 3x3x5 = 45 parts (u=0..2, v=0..2, d=0..4)
// Small shapes: 2x2x3 = 12 parts (u=0..1, v=0..1, d=0..2)
const SHAPE_DIMS = {
  large: { width: 3, height: 3, depth: 5, parts: 45 },
  small: { width: 2, height: 2, depth: 3, parts: 12 }
};

// Face dimensions for large and small (in pixels before 16px tiling)
const FACE_PIXELS = {
  large: {
    north: [48, 48], south: [48, 48],
    west: [80, 48], east: [80, 48],
    up: [48, 80], down: [48, 80]
  },
  small: {
    north: [32, 32], south: [32, 32],
    west: [48, 32], east: [48, 32],
    up: [32, 48], down: [32, 48]
  }
};

// UV orientation mapping per face
function uv(face, u, v, d, dims) {
  const { width, height, depth } = dims;
  switch (face) {
    case 'north': return [width - 1 - u, height - 1 - v];
    case 'south': return [u, height - 1 - v];
    case 'west': return [d, height - 1 - v];
    case 'east': return [depth - 1 - d, height - 1 - v];
    case 'up': return [u, d];
    case 'down': return [u, depth - 1 - d];
    default: throw new Error('Unknown face: ' + face);
  }
}

// Canonical part index: u * (height * depth) + v * depth + d
function partIndex(u, v, d, dims) {
  return u * (dims.height * dims.depth) + v * dims.depth + d;
}

// Decode part index back to u,v,d
function decodePart(index, dims) {
  const { height, depth } = dims;
  const u = Math.floor(index / (height * depth));
  const v = Math.floor((index % (height * depth)) / depth);
  const d = index % depth;
  return { u, v, d };
}

// Faces in canonical order
const FACES = ['north', 'south', 'west', 'east', 'up', 'down'];
const ROTATIONS = { north: 0, east: 90, south: 180, west: 270 };

// JSON stringifier with LF line endings
const json = (x) => JSON.stringify(x, null, 2) + '\n';

// PNG encoding (same pattern as joined-panels.mjs)
function chunk(type, data) {
  const t = Buffer.from(type);
  const n = Buffer.alloc(4);
  const c = Buffer.alloc(4);
  n.writeUInt32BE(data.length);
  c.writeUInt32BE(crc32(Buffer.concat([t, data])) >>> 0);
  return Buffer.concat([n, t, data, c]);
}

function png(raw, w, h) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w, 0);
  header.writeUInt32BE(h, 4);
  header[8] = 8;  // bit depth
  header[9] = 6;  // color type RGBA8
  const rows = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    Buffer.from(raw.buffer, raw.byteOffset + y * w * 4, w * 4).copy(rows, y * (w * 4 + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

// Hex color (#RRGGBB) to [r,g,b,255]
function color(hex) {
  if (typeof hex !== 'string' || !/^#[0-9a-f]{6}$/i.test(hex)) {
    throw new Error('T4 palette colors must be opaque #RRGGBB');
  }
  return [
    parseInt(hex.slice(1, 3), 16),
    parseInt(hex.slice(3, 5), 16),
    parseInt(hex.slice(5, 7), 16),
    255
  ];
}

// Fill a canvas with a solid color
function fill(w, h, c) {
  const raw = new Uint8Array(w * h * 4);
  for (let i = 0; i < w * h; i++) raw.set(c, i * 4);
  return raw;
}

// Drawing operations: rect, frame, dot, line
function draw(raw, w, h, ops, palette) {
  if (!Array.isArray(ops)) throw new Error('T4 recipe must be an operation array');
  for (const op of ops) {
    if (!Array.isArray(op) || !['rect', 'frame', 'dot', 'line'].includes(op[0])) {
      throw new Error('Unknown T4 drawing operation: ' + op[0]);
    }
    const kind = op[0];
    const colorKey = op[1];
    const nums = op.slice(2);
    const rgba = palette[colorKey];
    if (!rgba) throw new Error('T4 operation uses unknown palette color: ' + colorKey);
    if (nums.some(n => typeof n !== 'number' || !Number.isInteger(n))) {
      throw new Error('T4 operation coordinates must be integers');
    }
    let minX, minY, maxX, maxY;
    if (kind === 'rect' || kind === 'frame') {
      if (nums.length !== 4) throw new Error(kind + ' needs x,y,w,h');
      const [x, y, w_, h_] = nums;
      if (w_ <= 0 || h_ <= 0) throw new Error(kind + ' has invalid w/h');
      minX = x; minY = y; maxX = x + w_ - 1; maxY = y + h_ - 1;
    } else if (kind === 'dot') {
      if (nums.length !== 2) throw new Error('dot needs x,y');
      minX = maxX = nums[0]; minY = maxY = nums[1];
    } else { // line
      if (nums.length !== 4) throw new Error('line needs x0,y0,x1,y1');
      minX = Math.min(nums[0], nums[2]);
      minY = Math.min(nums[1], nums[3]);
      maxX = Math.max(nums[0], nums[2]);
      maxY = Math.max(nums[1], nums[3]);
    }
    if (minX < 0 || minY < 0 || maxX >= w || maxY >= h) {
      throw new Error('T4 operation exceeds canvas bounds');
    }
    const put = (px, py) => {
      const i = (py * w + px) * 4;
      raw[i] = rgba[0]; raw[i + 1] = rgba[1]; raw[i + 2] = rgba[2]; raw[i + 3] = rgba[3];
    };
    if (kind === 'rect') {
      for (let py = minY; py <= maxY; py++) {
        for (let px = minX; px <= maxX; px++) put(px, py);
      }
    } else if (kind === 'frame') {
      for (let px = minX; px <= maxX; px++) { put(px, minY); put(px, maxY); }
      for (let py = minY; py <= maxY; py++) { put(minX, py); put(maxX, py); }
    } else if (kind === 'dot') {
      put(minX, minY);
    } else { // line - Bresenham
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
  return raw;
}

// Crop a 16x16 tile from a face canvas
function crop(raw, w, tx, ty) {
  const tile = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) {
    const srcStart = ((ty * 16 + y) * w + tx * 16) * 4;
    const srcEnd = srcStart + 16 * 4;
    tile.set(raw.subarray(srcStart, srcEnd), y * 16 * 4);
  }
  return tile;
}

// Main export
export function computeT4Panels(cfg) {
  // Validate config schema
  if (!cfg || cfg.version !== 1 ||
      Object.keys(cfg).some(k => !['$comment', 'version', 'palette', 'faceRecipes', 'markings', 'unformedRecipes'].includes(k))) {
    throw new Error('Invalid t4-panels schema');
  }
  if (!cfg.palette || typeof cfg.palette !== 'object') throw new Error('T4 palette missing');
  const palette = Object.fromEntries(Object.entries(cfg.palette).map(([k, v]) => [k, color(v)]));
  if (!palette.steel) throw new Error('T4 steel base color missing');

  if (!cfg.faceRecipes || typeof cfg.faceRecipes !== 'object') throw new Error('T4 faceRecipes missing');
  if (!cfg.markings || typeof cfg.markings !== 'object') throw new Error('T4 markings missing');
  if (!cfg.unformedRecipes || typeof cfg.unformedRecipes !== 'object') throw new Error('T4 unformedRecipes missing');

  // Validate required face recipes exist for both sizes
  for (const size of ['large', 'small']) {
    for (const face of FACES) {
      const key = size + '_' + face;
      if (!cfg.faceRecipes[key]) throw new Error('Missing T4 face recipe: ' + key);
    }
  }

  // Validate required markings
  const requiredMarkings = ['controller_front', 'operating_panel_front', 'power_port', 'casing'];
  for (const m of requiredMarkings) {
    if (!cfg.markings[m]) throw new Error('Missing T4 marking: ' + m);
  }

  // Validate unformed recipes
  for (const role of ['controller', 'casing', 'power_port', 'operating_panel']) {
    if (!cfg.unformedRecipes[role]) throw new Error('Missing T4 unformed recipe for ' + role);
  }

  // Draw face canvases for both sizes
  const canvases = { large: {}, small: {} };
  for (const [size, dims] of Object.entries(SHAPE_DIMS)) {
    for (const face of FACES) {
      const [w, h] = FACE_PIXELS[size][face];
      const base = fill(w, h, palette.steel);
      canvases[size][face] = draw(base, w, h, cfg.faceRecipes[size + '_' + face], palette);
    }
  }

  // Validate all marking recipes
  for (const [name, ops] of Object.entries(cfg.markings)) {
    const test = fill(16, 16, palette.steel);
    draw(test, 16, 16, ops, palette);
  }

  // Draw unformed panel base textures per role
  const unformedBase = {};
  for (const role of ['controller', 'casing', 'power_port', 'operating_panel']) {
    const base = fill(16, 16, palette.steel);
    unformedBase[role] = draw(base, 16, 16, cfg.unformedRecipes[role], palette);
  }

  // Tile caches: key -> raw 16x16 RGBA
  const tileCache = new Map();
  const pngCache = new Map(); // path -> Buffer
  const modelCache = new Map(); // path -> JSON string

  // Get or create a tile for a given face, size, tile coordinates
  function getTile(size, face, tx, ty) {
    const [w, h] = FACE_PIXELS[size][face];
    if (tx < 0 || ty < 0 || tx >= w / 16 || ty >= h / 16) {
      throw new Error('T4 tile outside canonical face: ' + size + ' ' + face + ' ' + tx + ',' + ty);
    }
    const key = size + '_' + face + '_tx' + tx + '_ty' + ty;
    if (!tileCache.has(key)) {
      const raw = crop(canvases[size][face], w, tx, ty);
      tileCache.set(key, raw);
      pngCache.set(A + 'textures/block/t4_' + key + '.png', png(raw, 16, 16));
    }
    return { ref: 'lithmod:block/t4_' + key, raw: tileCache.get(key) };
  }

  // Get a marking tile (16x16 with marking drawn on steel base)
  function getMarkingTile(markingName) {
    const key = 'marking_' + markingName;
    if (!tileCache.has(key)) {
      const base = fill(16, 16, palette.steel);
      const raw = draw(base, 16, 16, cfg.markings[markingName], palette);
      tileCache.set(key, raw);
      pngCache.set(A + 'textures/block/t4_' + key + '.png', png(raw, 16, 16));
    }
    return { ref: 'lithmod:block/t4_' + key, raw: tileCache.get(key) };
  }

  // Get unformed base for a role
  function getUnformed(role) {
    const key = 'unformed_' + role;
    if (!tileCache.has(key)) {
      const raw = unformedBase[role].slice();
      tileCache.set(key, raw);
      pngCache.set(A + 'textures/block/t4_' + key + '.png', png(raw, 16, 16));
    }
    return { ref: 'lithmod:block/t4_' + key, raw: tileCache.get(key) };
  }

  // Build models for a block ID
  for (const blockId of BLOCK_IDS) {
    const roles = BLOCK_ROLES[blockId];
    // Each block can have multiple roles (e.g., controller for both dep_etcher and test_package)
    // But for model generation, we generate per-role models that are shared
  }

  // Generate per-role models (shared across block IDs with same role)
  const allRoles = ['controller', 'casing', 'power_port', 'operating_panel'];
  for (const role of allRoles) {
    for (const large of [true, false]) {
      const size = large ? 'large' : 'small';
      const dims = SHAPE_DIMS[size];
      const maxPart = large ? 44 : 11;

      for (let part = 0; part <= maxPart; part++) {
        const { u, v, d } = decodePart(part, dims);
        const textures = {};

        for (const face of FACES) {
          const [tu, tv] = uv(face, u, v, d, dims);
          // Determine if this part gets a special marking on this face
          let tile;
          const isNorth = face === 'north';
          const isController = role === 'controller';
          const isOpPanel = role === 'operating_panel';
          const isPowerPort = role === 'power_port';

          if (isPowerPort) {
            // Power port: marking on ALL faces
            tile = getMarkingTile('power_port');
          } else if (isController && isNorth) {
            // Controller: marking on north (front) face
            tile = getMarkingTile('controller_front');
          } else if (isOpPanel && isNorth) {
            // Operating panel: marking on north (front) face
            tile = getMarkingTile('operating_panel_front');
          } else {
            // Regular casing tile
            tile = getTile(size, face, tu, tv);
          }
          textures[face] = tile.ref;
        }
        textures.particle = textures.north;

        const modelName = 't4_' + role + '_' + size + '_part' + part + '_formed';
        modelCache.set(
          A + 'models/block/' + modelName + '.json',
          json({ parent: 'minecraft:block/cube', textures })
        );
      }
    }

    // Unformed model (generic for both sizes)
    const unformed = getUnformed(role);
    const unformedModelName = 't4_' + role + '_unformed';
    modelCache.set(
      A + 'models/block/' + unformedModelName + '.json',
      json({ parent: 'minecraft:block/cube_all', textures: { all: unformed.ref } })
    );
  }

  // Generate blockstate files for each block ID
  for (const blockId of BLOCK_IDS) {
    const roles = BLOCK_ROLES[blockId];
    // For blocks with multiple roles (shouldn't happen in T4), we'd need to handle differently
    // But T4 has 1 role per block ID, so:
    const role = roles[0];
    const variants = {};

    for (const [facing, rotation] of Object.entries(ROTATIONS)) {
      const rotated = (model) => rotation ? { model, y: rotation } : { model };

      // formed=false: ignore part/large, use unformed model
      variants['facing=' + facing + ',formed=false'] = rotated('lithmod:block/t4_' + role + '_unformed');

      // formed=true: exact match for large+part
      for (const large of [true, false]) {
        const size = large ? 'large' : 'small';
        const maxPart = large ? 44 : 11;
        for (let part = 0; part <= maxPart; part++) {
          const key = 'facing=' + facing + ',formed=true,large=' + (large ? 'true' : 'false') + ',part=' + part;
          const modelName = 'lithmod:block/t4_' + role + '_' + size + '_part' + part + '_formed';
          variants[key] = rotated(modelName);
        }
      }

      // For large=false, parts 12..44 must also resolve (map to unformed)
      // This is handled by the variant selector above since we generate all 45 parts for large
      // and only 12 for small. For small (large=false), parts 12-44 don't exist in the model files
      // but the variant selector won't match them, so they'd fall through.
      // We need to add fallback variants for large=false parts 12-44 mapping to unformed.
      for (let part = 12; part <= 44; part++) {
        const key = 'facing=' + facing + ',formed=true,large=false,part=' + part;
        variants[key] = rotated('lithmod:block/t4_' + role + '_unformed');
      }
    }

    modelCache.set(
      A + 'blockstates/t4_' + blockId + '.json',
      json({ variants })
    );
  }

  // Generate block item models and client item refs for each block ID
  for (const blockId of BLOCK_IDS) {
    const role = BLOCK_ROLES[blockId][0];
    // Block item model (referenced by item)
    modelCache.set(
      A + 'models/item/t4_' + blockId + '.json',
      json({ parent: 'lithmod:block/t4_' + role + '_unformed' })
    );
    // Client item reference
    modelCache.set(
      A + 'items/t4_' + blockId + '.json',
      json({ model: { type: 'minecraft:model', model: 'lithmod:item/t4_' + blockId } })
    );
  }

  // Preview generation (optional, for visual verification)
  const preview = generatePreview(canvases, tileCache, getMarkingTile, getUnformed, palette);

  return { pngs: pngCache, models: modelCache, preview };
}

// Generate a preview atlas showing all face canvases
function generatePreview(canvases, tileCache, getMarkingTile, getUnformed, palette) {
  const scale = 4;
  const pad = 4;
  const faces = FACES;
  const sizes = ['large', 'small'];

  // Calculate preview layout: 2 rows (large, small) x 6 cols (faces)
  const maxFaceW = 80, maxFaceH = 80;
  const cellW = maxFaceW * scale + pad * 2;
  const cellH = maxFaceH * scale + pad * 2;
  const atlasW = 6 * cellW + pad;
  const atlasH = 2 * cellH + pad;
  const atlas = new Uint8Array(atlasW * atlasH * 4);

  // Neutral background
  const bg = [0x20, 0x28, 0x30, 255];
  for (let i = 0; i < atlas.length; i += 4) {
    atlas.set(bg, i);
  }

  for (let si = 0; si < sizes.length; si++) {
    const size = sizes[si];
    for (let fi = 0; fi < faces.length; fi++) {
      const face = faces[fi];
      const [w, h] = FACE_PIXELS[size][face];
      const raw = canvases[size][face];
      const ox = pad + fi * cellW;
      const oy = pad + si * cellH;

      // Draw the face canvas scaled
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
          const src = (y * w + x) * 4;
          const r = raw[src], g = raw[src + 1], b = raw[src + 2], a = raw[src + 3];
          for (let dy = 0; dy < scale; dy++) {
            for (let dx = 0; dx < scale; dx++) {
              const dst = ((oy + y * scale + dy) * atlasW + (ox + x * scale + dx)) * 4;
              atlas[dst] = r;
              atlas[dst + 1] = g;
              atlas[dst + 2] = b;
              atlas[dst + 3] = a;
            }
          }
        }
      }

      // Draw grid lines for tile boundaries (16px)
      const gridColor = [255, 255, 255, 128];
      for (let tx = 0; tx <= w / 16; tx++) {
        const gx = ox + tx * 16 * scale;
        for (let y = 0; y < h * scale; y++) {
          const dst = ((oy + y) * atlasW + gx) * 4;
          if (dst + 3 < atlas.length) {
            atlas[dst] = gridColor[0];
            atlas[dst + 1] = gridColor[1];
            atlas[dst + 2] = gridColor[2];
            atlas[dst + 3] = gridColor[3];
          }
        }
      }
      for (let ty = 0; ty <= h / 16; ty++) {
        const gy = oy + ty * 16 * scale;
        for (let x = 0; x < w * scale; x++) {
          const dst = (gy * atlasW + ox + x) * 4;
          if (dst + 3 < atlas.length) {
            atlas[dst] = gridColor[0];
            atlas[dst + 1] = gridColor[1];
            atlas[dst + 2] = gridColor[2];
            atlas[dst + 3] = gridColor[3];
          }
        }
      }
    }
  }

  return png(atlas, atlasW, atlasH);
}