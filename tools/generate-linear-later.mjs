import fs from'node:fs';import{resolve,dirname,join}from'node:path';import{fileURLToPath}from'node:url';import{linearStageAssets}from'./lib/linear-stage-assets.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/';
export function outputs(){const out=linearStageAssets();
    for(const[id,casing]of[['g3_fab_item_port','g3_casing'],['euv_fab_item_port','euv_scanner_casing']]){
        const variants={};for(const[facing,y]of[['north',0],['east',90],['south',180],['west',270]])for(const formed of[false,true])variants['facing='+facing+',formed='+formed]={model:'lithmod:block/'+id+(formed?'_formed':''),y};
        out.set(A+'blockstates/'+id+'.json',{variants});for(const suffix of['','_formed'])out.set(A+'models/block/'+id+suffix+'.json',{parent:'lithmod:block/fab_item_port'+suffix});
        out.set(A+'models/item/'+id+'.json',{parent:'lithmod:block/'+id});out.set(A+'items/'+id+'.json',{model:{type:'minecraft:model',model:'lithmod:item/'+id}});
        out.set(D+'loot_table/blocks/'+id+'.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'lithmod:'+id}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
        out.set(D+'recipe/crafting/'+id+'.json',{type:'minecraft:crafting_shaped',category:'equipment',pattern:[' C ','HPH',' C '],key:{C:'lithmod:'+casing,H:'minecraft:hopper',P:'lithmod:compute_chip_t6'},result:{id:'lithmod:'+id,count:2}});
    }
    for(const id of['coating_track','wet_cleaner'])out.get(D+'loot_table/blocks/'+id+'.json').pools[0].entries[0].modifier={type:'minecraft:copy_components',source:'block_entity',include:['lithmod:contaminated_equipment']};
    const source=id=>JSON.parse(fs.readFileSync(join(root,A+id+'.json')));
    out.set(A+'blockstates/g3_etch_cell.json',source('blockstates/g3_deposition_etcher'));
    out.set(A+'models/block/g3_etch_cell.json',{parent:'lithmod:block/g3_deposition_etcher'});
    out.set(A+'models/item/g3_etch_cell.json',{parent:'lithmod:block/g3_etch_cell'});
    out.set(A+'items/g3_etch_cell.json',{model:{type:'minecraft:model',model:'lithmod:item/g3_etch_cell'}});
    const loot=source('../..'+'/data/lithmod/loot_table/blocks/g3_deposition_etcher');
    loot.pools[0].entries[0].name='lithmod:g3_etch_cell';out.set(D+'loot_table/blocks/g3_etch_cell.json',loot);
    const g3=JSON.parse(fs.readFileSync(join(root,D+'machines/g3_deposition_etcher.json')));g3.id='g3_etch_cell';Object.assign(g3.params,{linear_vacuum_duration:200,linear_rinse_moved:1,linear_dry_duration:100,etchant_per_lot:1});out.set(D+'machines/g3_etch_cell.json',g3);
    out.set(D+'recipe/crafting/g3_etch_cell.json',{type:'minecraft:crafting_shaped',category:'equipment',pattern:['CCC','RPR','CCC'],key:{C:'lithmod:g3_casing',R:'minecraft:redstone',P:'lithmod:compute_chip_t6'},result:{id:'lithmod:g3_etch_cell',count:1}});
    for(const id of['coating_track','wet_cleaner'])out.set(D+'machines/'+id+'.json',{id,energy_capacity:1000000,max_receive:20000,energy_per_tick:240,params:{...(id==='coating_track'?{coat_ticks_per_layer:5}:{clean_ticks_per_layer:5,t7_rinse_duration:200,t7_rinse_water_mb:4000,t7_rinse_energy_per_tick:480}),water_capacity:32000,repair_filter_medium:2,repair_redstone:16,repair_upw_mb:8000,repair_fe:200000}});
    for(const[id,pattern,key,count]of[
        ['coating_track',['CRC','PLP','CRC'],{C:'lithmod:advanced_casing',R:'minecraft:redstone',P:'lithmod:precision_overlay_scope',L:'lithmod:logic_chip_t4'},1],
        ['wet_cleaner',['CWC','PLP','CWC'],{C:'lithmod:advanced_casing',W:'minecraft:bucket',P:'minecraft:piston',L:'lithmod:logic_chip_t4'},1],
        ['fab_upw_inlet',[' C ','BPB',' C '],{C:'lithmod:advanced_casing',B:'minecraft:bucket',P:'lithmod:logic_chip_t4'},2]
    ])out.set(D+'recipe/crafting/'+id+'.json',{type:'minecraft:crafting_shaped',category:'equipment',pattern,key,result:{id:'lithmod:'+id,count}});
    return out;
}
let stale=[];for(const[p,j]of outputs()){const file=join(root,p),bytes=Buffer.from(JSON.stringify(j,null,2)+'\n');if(process.argv.includes('--check')){if(!fs.existsSync(file)||!fs.readFileSync(file).equals(bytes))stale.push(p);}else{fs.mkdirSync(dirname(file),{recursive:true});fs.writeFileSync(file,bytes);}}if(stale.length)throw Error('Stale resources: '+stale.join(','));console.log((process.argv.includes('--check')?'Checked ':'Generated ')+outputs().size+' later-route resources');
