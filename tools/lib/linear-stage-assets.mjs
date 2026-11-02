/**
 * Generates linear stage asset definitions for EUVCRAFT.
 * @returns {Map<string, object>} Map of resource paths to JSON definitions.
 */
export function linearStageAssets() {
  const assets = new Map();

  const ids = [
    {
      id: 'coating_track',
      frontTexture: 't4_unformed_controller',
      frontTextureFormed: 't4_unformed_controller',
    },
    {
      id: 'wet_cleaner',
      frontTexture: 't4_unformed_controller',
      frontTextureFormed: 't4_unformed_controller',
    },
    {
      id: 'fab_upw_inlet',
      frontTexture: 'euv_scanner_water_inlet',
      frontTextureFormed: 'euv_scanner_water_inlet_formed',
    },
  ];

  const facings = ['north', 'east', 'south', 'west'];
  const rotations = [0, 90, 180, 270];

  for (const { id, frontTexture, frontTextureFormed } of ids) {
    const basePath = `src/main/resources/assets/lithmod`;
    const dataPath = `src/main/resources/data/lithmod`;

    // Blockstate file
    const variants = {};
    for (const facing of facings) {
      for (const formed of [false, true]) {
        const model = `lithmod:block/${id}${formed ? '_formed' : ''}`;
        const key = `facing=${facing},formed=${formed}`;
        variants[key] = {model,y:rotations[facings.indexOf(facing)]};
      }
    }
    assets.set(`${basePath}/blockstates/${id}.json`, { variants });

    // Unformed block model
    assets.set(`${basePath}/models/block/${id}.json`, {
      parent: 'minecraft:block/cube',
      textures: {
        particle: `lithmod:block/t4_unformed_casing`,
        down: `lithmod:block/t4_unformed_casing`,
        up: `lithmod:block/t4_unformed_casing`,
        north: `lithmod:block/${frontTexture}`, 
        south: `lithmod:block/t4_unformed_casing`,
        west: `lithmod:block/t4_unformed_casing`,
        east: `lithmod:block/t4_unformed_casing`,
      },
    });

    // Formed block model
    assets.set(`${basePath}/models/block/${id}_formed.json`, {
      parent: 'minecraft:block/cube',
      textures: {
        particle: `lithmod:block/vacuum_casing_formed`,
        down: `lithmod:block/vacuum_casing_formed`,
        up: `lithmod:block/vacuum_casing_formed`,
        north: `lithmod:block/${frontTextureFormed}`, 
        south: `lithmod:block/vacuum_casing_formed`,
        west: `lithmod:block/vacuum_casing_formed`,
        east: `lithmod:block/vacuum_casing_formed`,
      },
    });

    // Item model (parents to block model)
    assets.set(`${basePath}/models/item/${id}.json`, {
      parent: `lithmod:block/${id}`,
    });

    // Item definition
    assets.set(`${basePath}/items/${id}.json`, {
      model: {
        type: 'minecraft:model',
        model: `lithmod:item/${id}`,
      },
    });

    // Loot table
    assets.set(`${dataPath}/loot_table/blocks/${id}.json`, {
      type: 'minecraft:block',
      pools: [
        {
          rolls: 1,
          entries: [
            {
              type: 'minecraft:item',
              name: `lithmod:${id}`,
            },
          ],
          conditions: [
            {
              condition: 'minecraft:survives_explosion',
            },
          ],
        },
      ],
    });
  }

  return assets;
}