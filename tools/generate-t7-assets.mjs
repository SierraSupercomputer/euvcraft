#!/usr/bin/env node
/** Simple starter panels using the established deterministic 16px asset style. */
import {readFileSync,writeFileSync,mkdirSync,existsSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {deflateSync,crc32} from 'node:zlib';
import {beamlineAssets} from './lib/beamline-model.mjs';
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),'..');
function chunk(type,data){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc32(Buffer.concat([t,data]))>>>0);return Buffer.concat([n,t,data,c]);}
function png(pixels){const h=Buffer.alloc(13);h.writeUInt32BE(16);h.writeUInt32BE(16,4);h[8]=8;h[9]=6;const rows=Buffer.alloc(16*65);for(let y=0;y<16;y++)pixels.copy(rows,y*65+1,y*64,(y+1)*64);return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',h),chunk('IDAT',deflateSync(rows,{level:9})),chunk('IEND',Buffer.alloc(0))]);}
function tile(block,formed){const p=Buffer.alloc(1024),rect=(x,y,w,h,c)=>{for(let j=y;j<y+h;j++)for(let i=x;i<x+w;i++){let n=(j*16+i)*4;p[n]=c[0];p[n+1]=c[1];p[n+2]=c[2];p[n+3]=255;}};
 const accent=block.tone??[80,160,185];rect(0,0,16,16,[48,57,66]);rect(1,1,14,14,[103,117,128]);rect(2,2,12,12,[70,81,91]);
 if(block.role==='casing'){rect(2,3,12,1,accent);rect(2,12,12,1,accent);rect(3,5,10,6,formed?[94,120,126]:[75,89,100]);}
 else if(block.role==='controller'||block.role==='panel'){rect(3,3,10,7,[22,31,41]);for(let y=4;y<9;y+=2)rect(4,y,6,1,accent);rect(4,11,2,2,[29,39,50]);rect(8,11,4,1,[156,165,171]);}
 else if(block.role==='port'||block.role==='conduit'){rect(4,4,8,8,[25,35,45]);rect(5,5,6,6,accent);rect(6,6,4,4,[31,44,52]);}
 else{rect(3,3,10,10,[30,42,51]);let hash=0;for(const c of block.id)hash=(hash*31+c.charCodeAt(0))>>>0;for(let i=0;i<12;i++)if(hash&(1<<i))rect(4+(i%4)*2,4+Math.floor(i/4)*3,1,2,accent);}
 rect(2,14,12,1,formed?[100,237,168]:[156,112,65]);return png(p);
}
// Crafting mirrors count as one recipe; preserve ingredient costs while assigning distinct arrangements.
function recipeKey(recipe){let rows=recipe.pattern.map(row=>[...row].map(c=>c===' '?null:recipe.key[c]));while(rows.length&&rows[0].every(x=>!x))rows.shift();while(rows.length&&rows.at(-1).every(x=>!x))rows.pop();while(rows[0]?.length&&rows.every(row=>!row[0]))rows=rows.map(row=>row.slice(1));while(rows[0]?.length&&rows.every(row=>!row.at(-1)))rows=rows.map(row=>row.slice(0,-1));return [JSON.stringify(rows),JSON.stringify(rows.map(row=>[...row].reverse()))].sort()[0];}
function uniqueRecipe(recipe,used){const copy=structuredClone(recipe);let chars=copy.pattern.map(row=>row.padEnd(3,' ')).join('').padEnd(9,' ').split('');const accept=()=>{copy.pattern=[chars.slice(0,3).join(''),chars.slice(3,6).join(''),chars.slice(6,9).join('')];const key=recipeKey(copy);if(used.has(key))return false;used.add(key);return true;};if(accept())return copy;chars.sort();do{if(accept())return copy;let i=chars.length-2;while(i>=0&&chars[i]>=chars[i+1])i--;if(i<0)break;let j=chars.length-1;while(chars[j]<=chars[i])j--;[chars[i],chars[j]]=[chars[j],chars[i]];chars=[...chars.slice(0,i+1),...chars.slice(i+1).reverse()];}while(true);throw Error('No distinct crafting pattern for '+recipe.result.id);}
export function outputs(manifest){const usedRecipes=new Set();const out=new Map(),A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/';const put=(path,value)=>{if(out.has(path))throw Error('Duplicate '+path);out.set(path,Buffer.isBuffer(value)?value:Buffer.from(JSON.stringify(value,null,2)+'\n'));};
 for(const block of manifest.blocks){if(!/^[a-z0-9_]+$/.test(block.id))throw Error('Invalid block ID');const id=block.id;
  for(const formed of block.formed?[false,true]:[false]){
   const suffix=formed?'_formed':'',face='lithmod:block/'+id+suffix;
   const side=['controller','panel'].includes(block.role)?'lithmod:block/t4_unformed_casing':face;
   put(A+'textures/block/'+id+suffix+'.png',tile(block,formed));
   put(A+'models/block/'+id+suffix+'.json',{parent:'minecraft:block/cube',textures:{particle:face,north:face,south:side,east:side,west:side,up:'lithmod:block/t4_unformed_casing',down:'lithmod:block/t4_unformed_casing'}});
  }
  const variants={};for(const formed of block.formed?[false,true]:[null])for(const [facing,rotation]of block.facing?[['north',0],['east',90],['south',180],['west',270]]:[['',0]]){const parts=[];if(facing)parts.push('facing='+facing);if(formed!==null)parts.push('formed='+formed);variants[parts.join(',')]={model:'lithmod:block/'+id+(formed?'_formed':''),...(rotation?{y:rotation}:{})};}
  put(A+'blockstates/'+id+'.json',{variants});put(A+'models/item/'+id+'.json',{parent:'lithmod:block/'+id});put(A+'items/'+id+'.json',{model:{type:'minecraft:model',model:'lithmod:item/'+id}});put(D+'loot_table/blocks/'+id+'.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'lithmod:'+id,...(['g3_deposition_etcher','euv_scanner_controller'].includes(id)?{modifier:{type:'minecraft:copy_components',source:'block_entity',include:['lithmod:contaminated_equipment']}}:{})}],condition:{type:'minecraft:survives_explosion'}}]});
  const casing=block.casing??'g3_casing';let pattern,key,count=1;
  if(block.role==='casing'){pattern=['III','GPG','III'];key={I:'minecraft:iron_ingot',G:'minecraft:gold_ingot',P:'lithmod:compute_chip_t6'};count=8;}
  else if(block.role==='controller'){pattern=['CDC','PMP','CDC'];key={C:'lithmod:'+casing,D:'minecraft:diamond',P:block.optionalT7?'lithmod:cpu_t7':'lithmod:cpu_t6',M:'lithmod:memory_chip_t6'};}
  else if(block.role==='panel'){pattern=['CGC','RPR','CGC'];key={C:'lithmod:'+casing,G:'minecraft:glass',R:'minecraft:redstone',P:'lithmod:compute_chip_t6'};}
  else if(block.role==='conduit'){pattern=['CGC','G G','CGC'];key={C:'lithmod:'+casing,G:'minecraft:glass'};count=8;}
  else if(block.role==='port'){pattern=['CRC','RPR','CRC'];key={C:'lithmod:'+casing,R:'minecraft:redstone',P:id.includes('power')?'minecraft:copper_ingot':'minecraft:bucket'};}
  else{pattern=['CGC','PMP','CGC'];key={C:'lithmod:'+casing,G:'minecraft:gold_ingot',P:'lithmod:compute_chip_t6',M:'lithmod:memory_chip_t6'};}
  put(D+'recipe/crafting/'+id+'.json',uniqueRecipe(block.recipe??{type:'minecraft:crafting_shaped',category:'misc',pattern,key,result:{id:'lithmod:'+id,count}},usedRecipes));
 }
 for(const [id,recipe]of Object.entries(manifest.componentRecipes??{}))put(D+'recipe/crafting/'+id+'.json',uniqueRecipe(recipe,usedRecipes));
 for(const[path,bytes]of beamlineAssets(JSON.parse(readFileSync(join(ROOT,'tools/models/beamline_pipe.json'))),readFileSync(join(ROOT,'tools/models/beamline_pipe.png'))))out.set(path,bytes);
 return out;
}
export function main(args=process.argv.slice(2)){const manifest=JSON.parse(readFileSync(join(ROOT,'tools/t7-assets.json'))),check=args.includes('--check');let failures=0;for(const[path,value]of outputs(manifest)){const target=join(ROOT,path);if(check){if(!existsSync(target)||!readFileSync(target).equals(value)){console.error('Out of date: '+path);failures++;}}else{mkdirSync(dirname(target),{recursive:true});writeFileSync(target,value);}}if(failures)throw Error(failures+' T7 asset differences');console.log((check?'Checked ':'Generated ')+outputs(manifest).size+' T7 assets/recipes');}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main();
