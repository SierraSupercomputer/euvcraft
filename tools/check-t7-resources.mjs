#!/usr/bin/env node
import {readFileSync as read,existsSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
import {outputs} from './generate-t7-assets.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const json=p=>JSON.parse(read(join(root,p)));
const manifest=json('tools/t7-assets.json'),ids=new Set(manifest.blocks.map(b=>b.id));
assert.equal(ids.size,47,'Each registered T7 block needs one unique asset entry');
const lang=json('src/main/resources/assets/lithmod/lang/en_us.json');
for(const [path,bytes]of outputs(manifest))assert.ok(existsSync(join(root,path))&&read(join(root,path)).equals(bytes),'Stale generated resource '+path);
for(const b of manifest.blocks){
 assert.ok(lang['block.lithmod.'+b.id],'Missing name '+b.id);
 const recipe=json('src/main/resources/data/lithmod/recipe/crafting/'+b.id+'.json');
 assert.equal(recipe.result.id,'lithmod:'+b.id);
 for(const ingredient of Object.values(recipe.key)){
  assert.equal(typeof ingredient,'string');
  if(!ingredient.startsWith('lithmod:'))continue;
  const id=ingredient.slice(8);
  assert.ok(existsSync(join(root,'src/main/resources/assets/lithmod/items/'+id+'.json')),'Unregistered recipe ingredient '+ingredient);
  if(b.id!=='central_vacuum_plant')assert.ok(!/_t[78]$/.test(id),'Essential machine needs a future product: '+b.id);
 }
 const variants=json('src/main/resources/assets/lithmod/blockstates/'+b.id+'.json').variants;
 assert.equal(Object.keys(variants).length,(b.formed?2:1)*(b.facing?4:1),'Incomplete visual states '+b.id);
 for(const model of Object.values(variants))assert.ok(existsSync(join(root,'src/main/resources/assets/lithmod/models/'+model.model.slice(8)+'.json')),'Missing block model '+model.model);
}
for(const id of ['g3_reticle_writer','g3_deposition_etcher','euv_scanner','euv_light_source','hydrogen_purifier','central_vacuum_plant']){
 const d=json('src/main/resources/data/lithmod/machines/'+id+'.json');assert.equal(d.id,id);assert.ok(d.energy_capacity>=d.max_receive&&d.max_receive>0);assert.ok(d.energy_per_tick>0);for(const [key,value]of Object.entries(d.params))assert.ok(Number.isSafeInteger(value)&&value>0,id+': '+key);
}
const shell=json('src/main/resources/data/lithmod/tags/block/cleanroom_shell_extensions.json').values;
assert.ok(shell.includes('lithmod:euv_beam_line')&&shell.includes('lithmod:cleanroom_vacuum_port'));
assert.ok(!shell.includes('lithmod:vacuum_service_conduit'),'Ordinary vacuum conduit cannot seal a cleanroom');
for(const family of ['compute','memory','nand']){const r=json('src/main/resources/data/lithmod/support_recipes/g3_'+family+'_t7.json');assert.equal(r.machine,'g3_reticle_writer');assert.equal(r.output,'lithmod:mask');assert.ok(r.input.count===1&&r.secondary.count>0&&r.energy_per_tick>0);}
console.log('T7 resources: 47 blocks, editable bootstrap recipes/definitions, sealed penetrations and three reticle families pass');
