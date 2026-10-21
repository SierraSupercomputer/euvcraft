// Pure deterministic aliases: support cells reuse the approved fixed 2x2x3 panel art.
const A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/';
const blocks={boule_purifier:'controller',ebeam_mask_writer:'controller',support_casing:'casing',support_power_port:'power_port',support_water_inlet:'casing'};
const items=["pure_silicon_boule","logic_chip_t3","memory_chip_t3","compute_chip_t3","nand_chip_t3","krf_excimer_laser","duv_projection_lens","precision_stepper_stage","hepa_filter","laser_interferometer","resist_t3"];
export function computeSupportAssets(){
  const out=new Map(),put=(path,value)=>{if(out.has(path))throw Error('Duplicate support output '+path);out.set(path,JSON.stringify(value,null,2)+'\n');};
  for(const[id,role]of Object.entries(blocks)){
    const variants={};for(const[facing,y]of Object.entries({north:0,east:90,south:180,west:270})){
      const model=name=>y?{model:name,y}:{model:name};
      variants['facing='+facing+',formed=false']=model('lithmod:block/process_cell_'+role+'_part0_unformed');
      for(let part=0;part<12;part++)variants['facing='+facing+',formed=true,part='+part]=model('lithmod:block/process_cell_'+role+'_part'+part+'_formed');
    }
    put(A+'blockstates/'+id+'.json',{variants});
    put(A+'models/item/'+id+'.json',{parent:'lithmod:block/process_cell_'+role+'_unformed'});
    put(A+'items/'+id+'.json',{model:{type:'minecraft:model',model:'lithmod:item/'+id}});
    put(D+'loot_table/blocks/'+id+'.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'lithmod:'+id}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
  }
  for(const id of items)put(A+'items/'+id+'.json',{model:{type:'minecraft:model',model:'lithmod:item/'+id}});
  const recipe=(id,pattern,key,count=1)=>put(D+'recipe/crafting/'+id+'.json',{type:'minecraft:crafting_shaped',category:'equipment',pattern,key,result:{id:'lithmod:'+id,count}});
  recipe('boule_purifier',['IGI','CBC','ILI'],{I:'minecraft:iron_ingot',G:'minecraft:glass',C:'minecraft:copper_ingot',B:'lithmod:silicon_boule',L:'lithmod:logic_chip_t2'});
  recipe('ebeam_mask_writer',['DGD','RBR','ILI'],{D:'minecraft:diamond',G:'minecraft:glass',R:'minecraft:redstone',B:'lithmod:blank_mask',I:'minecraft:iron_ingot',L:'lithmod:logic_chip_t2'});
  recipe('support_casing',['IQI','QLQ','IQI'],{I:'minecraft:iron_ingot',Q:'minecraft:quartz',L:'lithmod:logic_chip_t2'},4);
  recipe('support_power_port',['CRC','RLR','CRC'],{C:'minecraft:copper_ingot',R:'minecraft:redstone',L:'lithmod:logic_chip_t2'});
  recipe('support_water_inlet',['CBC','LWL','CBC'],{C:'minecraft:copper_ingot',B:'lithmod:support_casing',L:'lithmod:logic_chip_t2',W:'minecraft:water_bucket'});
  return out;
}
