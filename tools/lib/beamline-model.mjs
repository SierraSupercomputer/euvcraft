/**
 * Generate beamline model assets from source Blockbench model and texture.
 * @param {Object} sourceModel - Parsed Blockbench model JSON (beamline_pipe.json)
 * @param {Buffer} texturePng - Raw PNG bytes for euv_beam_line texture
 * @returns {Map<string, Buffer>} Repository-relative paths to asset buffers
 */
export function beamlineAssets(sourceModel, texturePng) {
  if (!sourceModel || typeof sourceModel !== 'object') {
    throw new Error('sourceModel must be an object');
  }
  if (!Buffer.isBuffer(texturePng)) {
    throw new Error('texturePng must be a Buffer');
  }

  // Deep clone to avoid mutating input
  const model = JSON.parse(JSON.stringify(sourceModel));

  // Validate and remap textures
  const definedTextures = model.textures || {};
  if (!definedTextures['1']) {
    throw new Error('Source model must define texture key "1"');
  }

  // Check all visible faces reference only #1 or null
  for (const element of model.elements || []) {
    if (!element.faces) continue;
    for (const [faceName, face] of Object.entries(element.faces)) {
      if (face === null || face === undefined || face.texture === null) { delete element.faces[faceName]; continue; }
      if (!face.texture) {
        throw new Error(`Face ${faceName} in element ${element.name || 'unnamed'} missing texture`);
      }
      if (face.texture !== '#1') {
        throw new Error(`Face ${faceName} in element ${element.name || 'unnamed'} references undefined texture "${face.texture}"; only #1 allowed`);
      }
      // Keep source UVs and #1 references; namespace belongs in the texture dictionary.
    }
  }

  // Build final block model
  const blockModel = {
    ...model, parent: 'minecraft:block/block', ambientocclusion: false,
    textures: {'1': 'lithmod:block/euv_beam_line', particle: 'lithmod:block/euv_beam_line'}
  };
  delete blockModel.groups;

  // Blockstate with axis variants (pipe runs Z / north-south)
  const blockstate = {
    variants: {
      'axis=z': { model: 'lithmod:block/euv_beam_line' },
      'axis=x': { model: 'lithmod:block/euv_beam_line', y: 90 },
      'axis=y': { model: 'lithmod:block/euv_beam_line', x: 90 }
    }
  };

  // Item model
  const itemModel = {
    parent: 'lithmod:block/euv_beam_line'
  };

  // Deterministic JSON serialization (2-space indent, trailing newline)
  function toBuffer(obj) {
    return Buffer.from(JSON.stringify(obj, null, 2) + '\n');
  }

  const out = new Map();
  out.set('src/main/resources/assets/lithmod/models/block/euv_beam_line.json', toBuffer(blockModel));
  out.set('src/main/resources/assets/lithmod/textures/block/euv_beam_line.png', texturePng);
  out.set('src/main/resources/assets/lithmod/blockstates/euv_beam_line.json', toBuffer(blockstate));
  out.set('src/main/resources/assets/lithmod/models/item/euv_beam_line.json', toBuffer(itemModel));

  return out;
}