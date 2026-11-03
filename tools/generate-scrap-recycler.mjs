import fs from'node:fs';import{resolve,dirname,join}from'node:path';import{fileURLToPath}from'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/',out=new Map();
out.set(A+'blockstates/scrap_recycler.json',{variants:{'':{model:'lithmod:block/scrap_recycler'}}});
out.set(A+'models/block/scrap_recycler.json',{parent:'minecraft:block/orientable',textures:{top:'lithmod:block/crystal_puller',side:'lithmod:block/crystal_puller',front:'lithmod:block/t4_unformed_controller'}});
out.set(A+'models/item/scrap_recycler.json',{parent:'lithmod:block/scrap_recycler'});out.set(A+'items/scrap_recycler.json',{model:{type:'minecraft:model',model:'lithmod:item/scrap_recycler'}});
out.set(D+'loot_table/blocks/scrap_recycler.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'lithmod:scrap_recycler'}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
out.set(D+'machines/scrap_recycler.json',{id:'scrap_recycler',energy_capacity:40000,max_receive:2000,energy_per_tick:40});
out.set(D+'recipe/crafting/scrap_recycler.json',{type:'minecraft:crafting_shaped',category:'equipment',pattern:['IRI','IFI','IRI'],key:{I:'minecraft:iron_ingot',R:'minecraft:redstone',F:'minecraft:furnace'},result:{id:'lithmod:scrap_recycler',count:1}});
for(const input of['wafer_scrap','chip_scrap','assembly_scrap'])out.set(D+'machine_recipes/recycle_'+input+'.json',{machine:'scrap_recycler',ingredients:[{item:'lithmod:'+input,count:64}],result:{item:'lithmod:silicon',count:1},ticks:400});
for(const[path,json]of out){const target=join(root,path),bytes=Buffer.from(JSON.stringify(json,null,2)+'\n');if(process.argv.includes('--check')){if(!fs.existsSync(target)||!fs.readFileSync(target).equals(bytes))throw Error('Stale recycler resource '+path);}else{fs.mkdirSync(dirname(target),{recursive:true});fs.writeFileSync(target,bytes);}}
console.log((process.argv.includes('--check')?'Checked ':'Generated ')+out.size+' recycler resources');
