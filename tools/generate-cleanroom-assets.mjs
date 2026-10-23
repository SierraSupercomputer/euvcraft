#!/usr/bin/env node
import{readFileSync,writeFileSync,mkdirSync,existsSync}from'node:fs';
import{dirname,resolve,join}from'node:path';import{fileURLToPath}from'node:url';import{deflateSync,crc32}from'node:zlib';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/';
export const BLOCKS=['cleanroom_floor','cleanroom_wall','cleanroom_window','cleanroom_door','cleanroom_controller','cleanroom_filter','cleanroom_power_port','cleanroom_fluid_port','cleanroom_item_port'];
export const ITEMS=['cleanroom_filter_medium','cleanroom_hood','cleanroom_suit','cleanroom_trousers','cleanroom_boots'];
const names=['Cleanroom Floor','Cleanroom Wall','Cleanroom Window','Cleanroom Door','Cleanroom Controller','Cleanroom Filter','Cleanroom Power Port','Cleanroom Fluid Port','Cleanroom Item Port','Cleanroom Filter Medium','Cleanroom Hood','Cleanroom Suit','Cleanroom Trousers','Cleanroom Boots'];
export const translations=Object.fromEntries([...BLOCKS,...ITEMS].flatMap((id,i)=>[['item.lithmod.'+id,names[i]],...(i<BLOCKS.length?[['block.lithmod.'+id,names[i]]]:[])]));
const doorStates={
  "variants": {
    "facing=east,half=lower,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_left"
    },
    "facing=east,half=lower,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_left_open",
      "y": 90
    },
    "facing=east,half=lower,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_right"
    },
    "facing=east,half=lower,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_right_open",
      "y": 270
    },
    "facing=east,half=upper,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_top_left"
    },
    "facing=east,half=upper,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_top_left_open",
      "y": 90
    },
    "facing=east,half=upper,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_top_right"
    },
    "facing=east,half=upper,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_top_right_open",
      "y": 270
    },
    "facing=north,half=lower,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_left",
      "y": 270
    },
    "facing=north,half=lower,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_left_open"
    },
    "facing=north,half=lower,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_right",
      "y": 270
    },
    "facing=north,half=lower,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_right_open",
      "y": 180
    },
    "facing=north,half=upper,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_top_left",
      "y": 270
    },
    "facing=north,half=upper,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_top_left_open"
    },
    "facing=north,half=upper,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_top_right",
      "y": 270
    },
    "facing=north,half=upper,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_top_right_open",
      "y": 180
    },
    "facing=south,half=lower,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_left",
      "y": 90
    },
    "facing=south,half=lower,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_left_open",
      "y": 180
    },
    "facing=south,half=lower,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_right",
      "y": 90
    },
    "facing=south,half=lower,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_right_open"
    },
    "facing=south,half=upper,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_top_left",
      "y": 90
    },
    "facing=south,half=upper,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_top_left_open",
      "y": 180
    },
    "facing=south,half=upper,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_top_right",
      "y": 90
    },
    "facing=south,half=upper,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_top_right_open"
    },
    "facing=west,half=lower,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_left",
      "y": 180
    },
    "facing=west,half=lower,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_left_open",
      "y": 270
    },
    "facing=west,half=lower,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_bottom_right",
      "y": 180
    },
    "facing=west,half=lower,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_bottom_right_open",
      "y": 90
    },
    "facing=west,half=upper,hinge=left,open=false": {
      "model": "lithmod:block/cleanroom_door_top_left",
      "y": 180
    },
    "facing=west,half=upper,hinge=left,open=true": {
      "model": "lithmod:block/cleanroom_door_top_left_open",
      "y": 270
    },
    "facing=west,half=upper,hinge=right,open=false": {
      "model": "lithmod:block/cleanroom_door_top_right",
      "y": 180
    },
    "facing=west,half=upper,hinge=right,open=true": {
      "model": "lithmod:block/cleanroom_door_top_right_open",
      "y": 90
    }
  }
}
;
function chunk(type,data){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc32(Buffer.concat([t,data]))>>>0);return Buffer.concat([n,t,data,c]);}
function png(w,h,pixels){const header=Buffer.alloc(13);header.writeUInt32BE(w);header.writeUInt32BE(h,4);header[8]=8;header[9]=6;const rows=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++)pixels.copy(rows,y*(w*4+1)+1,y*w*4,(y+1)*w*4);return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(rows,{level:9})),chunk('IEND',Buffer.alloc(0))]);}
function tile(id,item=false,w=16,h=16){const p=Buffer.alloc(w*h*4),pixel=(x,y,c)=>{if(x<0||y<0||x>=w||y>=h)return;for(let k=0;k<4;k++)p[(y*w+x)*4+k]=c[k]??255;},rect=(x,y,rw,rh,c)=>{for(let j=y;j<y+rh;j++)for(let i=x;i<x+rw;i++)pixel(i,j,c);};
 if(w===64){rect(0,0,w,h,[232,240,242,255]);rect(20,9,8,3,[71,117,128,255]);return png(w,h,p);}
 if(item){rect(4,2,8,12,[225,235,238,255]);rect(5,3,6,2,[91,140,149,255]);if(id.includes('trousers')||id.includes('boots'))rect(7,8,2,6,[0,0,0,0]);if(id.includes('medium'))for(let x=5;x<12;x+=2)rect(x,4,1,8,[82,110,116,255]);return png(w,h,p);}
 rect(0,0,16,16,[80,100,107,255]);rect(1,1,14,14,[217,228,231,255]);rect(2,2,12,12,[237,243,244,255]);
 if(id.includes('window'))rect(2,2,12,12,[117,202,220,70]);
 if(id.includes('floor'))for(let i=4;i<16;i+=4){rect(i,1,1,14,[183,200,204,255]);rect(1,i,14,1,[183,200,204,255]);}
 if(id.includes('controller')){rect(3,3,10,8,[25,48,53,255]);rect(4,4,8,2,[62,197,155,255]);rect(5,12,2,1,[229,174,57,255]);}
 if(id.includes('filter'))for(let y=3;y<14;y+=2)rect(3,y,10,1,[78,103,112,255]);
 if(id.includes('port')){const c=id.includes('power')?[224,156,51,255]:id.includes('fluid')?[62,151,211,255]:[110,174,97,255];rect(3,3,10,10,[60,80,87,255]);rect(5,5,6,6,c);rect(7,4,2,8,[229,240,239,255]);}
 if(id.includes('door')){rect(4,3,8,7,[87,145,160,255]);rect(12,11,1,2,[64,86,93,255]);}return png(w,h,p);}
export function outputs(){const out=new Map(),put=(path,value)=>{if(out.has(path))throw Error('Duplicate '+path);out.set(path,Buffer.isBuffer(value)?value:Buffer.from(JSON.stringify(value,null,2)+'\n'));};
 for(const id of BLOCKS){
  if(id==='cleanroom_door')continue;
  put(A+'textures/block/'+id+'.png',tile(id));put(A+'models/block/'+id+'.json',id.endsWith('_port')?{parent:'minecraft:block/cube',textures:{particle:'lithmod:block/'+id,north:'lithmod:block/'+id,south:'lithmod:block/'+id,east:'lithmod:block/cleanroom_wall',west:'lithmod:block/cleanroom_wall',up:'lithmod:block/cleanroom_wall',down:'lithmod:block/cleanroom_wall'}}:{parent:'minecraft:block/cube_all',...(id==='cleanroom_window'?{render_type:'minecraft:translucent'}:{}),textures:{all:'lithmod:block/'+id}});
  const variants={};if(id.endsWith('_port'))for(const[f,y]of Object.entries({north:0,east:90,south:180,west:270}))variants['facing='+f]={model:'lithmod:block/'+id,...(y?{y}:{})};else variants['']={model:'lithmod:block/'+id};if(id.endsWith('_port'))for(const f of['up','down'])variants['facing='+f]={model:'lithmod:block/'+id,x:90};put(A+'blockstates/'+id+'.json',{variants});put(A+'models/item/'+id+'.json',{parent:'lithmod:block/'+id});
 }
 put(A+'blockstates/cleanroom_door.json',doorStates);for(const half of['bottom','top']){put(A+'textures/block/cleanroom_door_'+half+'.png',tile('cleanroom_door'));for(const hinge of['left','right'])for(const open of['','_open'])put(A+'models/block/cleanroom_door_'+half+'_'+hinge+open+'.json',{parent:'minecraft:block/door_'+half+'_'+hinge+open,textures:{bottom:'lithmod:block/cleanroom_door_bottom',top:'lithmod:block/cleanroom_door_top'}});}
 for(const id of[...ITEMS,'cleanroom_door']){put(A+'textures/item/'+id+'.png',tile(id,true));put(A+'models/item/'+id+'.json',{parent:'minecraft:item/generated',textures:{layer0:'lithmod:item/'+id}});}
 for(const id of[...BLOCKS,...ITEMS])put(A+'items/'+id+'.json',{model:{type:'minecraft:model',model:'lithmod:item/'+id}});
 for(const id of BLOCKS)put(D+'loot_table/blocks/'+id+'.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'lithmod:'+id}],conditions:[{condition:'minecraft:survives_explosion'},...(id==='cleanroom_door'?[{condition:'minecraft:block_state_property',block:'lithmod:cleanroom_door',properties:{half:'lower'}}]:[])]}]});
 const craft=(id,pattern,key,count=1)=>put(D+'recipe/crafting/'+id+'.json',{type:'minecraft:crafting_shaped',category:'equipment',pattern,key,result:{id:'lithmod:'+id,count}});
 craft('cleanroom_wall',['III','IWI','III'],{I:'minecraft:iron_ingot',W:'minecraft:white_wool'},4);craft('cleanroom_floor',['SSS','IWI','SSS'],{S:'minecraft:smooth_stone',I:'minecraft:iron_ingot',W:'minecraft:white_wool'},4);
 craft('cleanroom_window',['IGI','GWG','IGI'],{I:'minecraft:iron_ingot',G:'minecraft:glass',W:'lithmod:cleanroom_wall'},4);craft('cleanroom_door',['IW','IW','IW'],{I:'minecraft:iron_ingot',W:'lithmod:cleanroom_wall'});
 craft('cleanroom_controller',['IRI','WLW','IRI'],{I:'minecraft:iron_ingot',R:'minecraft:redstone',W:'lithmod:cleanroom_wall',L:'lithmod:logic_chip_t4'});craft('cleanroom_filter',['IWI','FMF','ILI'],{I:'minecraft:iron_ingot',W:'lithmod:cleanroom_wall',F:'lithmod:fine_air_filter',M:'lithmod:cleanroom_filter_medium',L:'lithmod:logic_chip_t4'});
 for(const[role,material]of Object.entries({power:'minecraft:redstone',fluid:'minecraft:bucket',item:'minecraft:hopper'}))craft('cleanroom_'+role+'_port',['III','WMW','III'],{I:'minecraft:iron_ingot',W:'lithmod:cleanroom_wall',M:material});
 craft('cleanroom_filter_medium',['PPP','CFC','PPP'],{P:'minecraft:paper',C:'minecraft:charcoal',F:'lithmod:fine_air_filter'},4);
 for(const[id,pattern]of Object.entries({hood:['WWW','WLW','   '],suit:['W W','WLW','WWW'],trousers:['WWW','WLW','W W'],boots:['   ','W W','L L']}))craft('cleanroom_'+id,pattern,{W:'minecraft:white_wool',L:'minecraft:leather'});
 put(A+'equipment/cleanroom_gown.json',{layers:{humanoid:[{texture:'lithmod:cleanroom_gown'}],humanoid_leggings:[{texture:'lithmod:cleanroom_gown'}]}});for(const layer of['humanoid','humanoid_leggings'])put(A+'textures/entity/equipment/'+layer+'/cleanroom_gown.png',tile('gown',false,64,32));return out;
}
export function main(){const args=process.argv.slice(2);if(args.length>1||args.length===1&&args[0]!=='--check')throw Error('Usage: node tools/generate-cleanroom-assets.mjs [--check]');const map=outputs();let stale=0;for(const[path,bytes]of map){if(!(path.startsWith(A)||path.startsWith(D+'recipe/crafting/cleanroom_')||path.startsWith(D+'loot_table/blocks/cleanroom_')))throw Error('Output escapes scope '+path);const to=join(root,path);if(args[0]==='--check'){if(!existsSync(to)||!readFileSync(to).equals(bytes)){console.error('Missing/stale '+path);stale++;}}else{mkdirSync(dirname(to),{recursive:true});writeFileSync(to,bytes);}}if(stale)throw Error('Stale cleanroom outputs: '+stale);console.log((args[0]==='--check'?'Checked ':'Generated ')+map.size+' deterministic cleanroom assets');}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main();
