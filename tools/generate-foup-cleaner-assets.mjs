#!/usr/bin/env node
import{readFileSync,writeFileSync,mkdirSync,existsSync}from'node:fs';
import{dirname,resolve,join}from'node:path';import{fileURLToPath}from'node:url';import{deflateSync,crc32}from'node:zlib';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/';
function chunk(type,data){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc32(Buffer.concat([t,data]))>>>0);return Buffer.concat([n,t,data,c]);}
function png(w,h,pixels){const header=Buffer.alloc(13);header.writeUInt32BE(w);header.writeUInt32BE(h,4);header[8]=8;header[9]=6;const rows=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++)pixels.copy(rows,y*(w*4+1)+1,y*w*4,(y+1)*w*4);return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(rows,{level:9})),chunk('IEND',Buffer.alloc(0))]);}
function tileFoupCleanerBlock(w=16,h=16){const p=Buffer.alloc(w*h*4),px=(x,y,c)=>{if(x<0||y<0||x>=w||y>=h)return;for(let k=0;k<4;k++)p[(y*w+x)*4+k]=c[k]??255;},rect=(x,y,rw,rh,c)=>{for(let j=y;j<y+rh;j++)for(let i=x;i<x+rw;i++)px(i,j,c);};
rect(0,0,16,16,[55,62,71,255]);
rect(1,1,14,14,[75,85,95,255]);
rect(2,2,12,12,[100,115,130,255]);
rect(3,3,10,10,[45,52,60,255]);
rect(5,5,6,6,[30,35,42,255]);
rect(6,6,4,4,[20,25,30,255]);
rect(7,7,2,2,[120,140,160,255]);
rect(3,1,10,2,[80,120,140,255]);
rect(3,13,10,2,[80,100,120,255]);
rect(1,3,2,10,[70,90,100,255]);
rect(13,3,2,10,[70,90,100,255]);
return png(w,h,p);}
function tileWaferScrapItem(w=16,h=16){const p=Buffer.alloc(w*h*4),px=(x,y,c)=>{if(x<0||y<0||x>=w||y>=h)return;for(let k=0;k<4;k++)p[(y*w+x)*4+k]=c[k]??255;},rect=(x,y,rw,rh,c)=>{for(let j=y;j<y+rh;j++)for(let i=x;i<x+rw;i++)px(i,j,c);};
rect(3,2,10,12,[140,150,160,255]);
rect(4,3,8,10,[160,170,180,255]);
rect(5,4,6,8,[180,190,200,255]);
for(let i=0;i<3;i++)rect(6+i*2,5,1,6,[100,110,120,255]);
rect(4,10,8,2,[130,140,150,255]);
return png(w,h,p);}
export function outputs(){const out=new Map(),put=(path,value)=>{if(out.has(path))throw Error('Duplicate '+path);out.set(path,Buffer.isBuffer(value)?value:Buffer.from(JSON.stringify(value,null,2)+'\n'));};
put(A+'blockstates/foup_cleaner.json',{variants:{"":{model:"lithmod:block/foup_cleaner"}}});
put(A+'models/block/foup_cleaner.json',{parent:"minecraft:block/cube_all",textures:{all:"lithmod:block/foup_cleaner"}});
put(A+'models/item/foup_cleaner.json',{parent:"lithmod:block/foup_cleaner"});
put(A+'models/item/wafer_scrap.json',{parent:"minecraft:item/generated",textures:{layer0:"lithmod:item/wafer_scrap"}});
put(A+'items/foup_cleaner.json',{model:{type:"minecraft:model",model:"lithmod:item/foup_cleaner"}});
put(A+'items/wafer_scrap.json',{model:{type:"minecraft:model",model:"lithmod:item/wafer_scrap"}});
put(A+'textures/block/foup_cleaner.png',tileFoupCleanerBlock());
put(A+'textures/item/wafer_scrap.png',tileWaferScrapItem());
put(D+'loot_table/blocks/foup_cleaner.json',{type:"minecraft:block",pools:[{rolls:1,entries:[{type:"minecraft:item",name:"lithmod:foup_cleaner"}],conditions:[{condition:"minecraft:survives_explosion"}]}]});
put(D+'recipe/crafting/foup_cleaner.json',{type:"minecraft:crafting_shaped",category:"equipment",pattern:["IBI","BRB","IGI"],key:{I:"minecraft:iron_ingot",B:"minecraft:bucket",R:"minecraft:redstone",G:"minecraft:glass"},result:{id:"lithmod:foup_cleaner",count:1}});
put(D+'machines/foup_cleaner.json',{id:"foup_cleaner",energy_capacity:40000,max_receive:2000,energy_per_tick:20,params:{duration:200,water_mb:1000,water_capacity_mb:16000,scrap_per_wafer:1}});
return out;}
export function main(){const args=process.argv.slice(2);if(args.length>1||args.length===1&&args[0]!=='--check')throw Error('Usage: node tools/generate-foup-cleaner-assets.mjs [--check]');const map=outputs();let stale=0;for(const[path,bytes]of map){const scopePrefixes=[A+'blockstates/foup_cleaner.json',A+'models/block/foup_cleaner.json',A+'models/item/foup_cleaner.json',A+'models/item/wafer_scrap.json',A+'items/foup_cleaner.json',A+'items/wafer_scrap.json',A+'textures/block/foup_cleaner.png',A+'textures/item/wafer_scrap.png',D+'loot_table/blocks/foup_cleaner.json',D+'recipe/crafting/foup_cleaner.json',D+'machines/foup_cleaner.json'];if(!scopePrefixes.includes(path))throw Error('Output escapes scope '+path);const to=join(root,path);if(args[0]==='--check'){if(!existsSync(to)||!readFileSync(to).equals(bytes)){console.error('Missing/stale '+path);stale++;}}else{mkdirSync(dirname(to),{recursive:true});writeFileSync(to,bytes);}}if(stale)throw Error('Stale foup-cleaner outputs: '+stale);console.log((args[0]==='--check'?'Checked ':'Generated ')+map.size+' deterministic foup-cleaner assets');}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main();