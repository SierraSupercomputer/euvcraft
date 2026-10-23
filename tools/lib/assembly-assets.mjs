// The 2x2x2 assembler uses a simple formed indicator, independent of part/large.
// Reuse the approved starter art; item/unformed models retain their existing art.
const A='src/main/resources/assets/lithmod/';
export function computeAssemblyAssets(){
  const out=new Map(),put=(path,value)=>out.set(A+path,JSON.stringify(value,null,2)+'\n');
  for(const [id,role] of Object.entries({pick_and_place:'controller',assembly_casing:'casing',assembly_power_port:'power_port'})){
    const formed='lithmod:block/'+id+'_formed',variants={};
    for(const [facing,y] of Object.entries({north:0,east:90,south:180,west:270})){
      for(const state of [false,true])variants['facing='+facing+',formed='+state]={model:state?formed:'lithmod:block/'+id,...(y?{y}:{})};
    }
    put('blockstates/'+id+'.json',{variants});
    const stem='lithmod:block/process_cell_'+role+'_formed';
    put('models/block/'+id+'_formed.json',role==='controller'?{
      parent:'minecraft:block/orientable',textures:{top:stem+'_top',bottom:stem+'_top',side:stem+'_side',front:stem+'_front',particle:stem+'_side'}
    }:{parent:'minecraft:block/cube_all',textures:{all:stem}});
  }
  return out;
}
