// Fixed-cell joined asset generation. Pure computation: no filesystem writes or import-time execution.
import {deflateSync,crc32} from 'node:zlib';
const A='src/main/resources/assets/lithmod/',D='src/main/resources/data/lithmod/';
const roles=['controller','casing','power_port','operating_panel','water_inlet'];
const sizes={north:[32,32],south:[32,32],west:[48,32],east:[48,32],up:[32,48],down:[32,48]};
const rotations={north:0,east:90,south:180,west:270};
const json=x=>JSON.stringify(x,null,2)+'\n';
const chunk=(type,data)=>{const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc32(Buffer.concat([t,data]))>>>0);return Buffer.concat([n,t,data,c]);};
function png(raw,w,h){const header=Buffer.alloc(13);header.writeUInt32BE(w,0);header.writeUInt32BE(h,4);header[8]=8;header[9]=6;const rows=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++)Buffer.from(raw.buffer,raw.byteOffset+y*w*4,w*4).copy(rows,y*(w*4+1)+1);return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(rows,{level:9})),chunk('IEND',Buffer.alloc(0))]);}
function color(text){if(typeof text!=='string'||!/^#[0-9a-f]{6}$/i.test(text))throw Error('Joined palette colors must be opaque #RRGGBB');return [1,3,5].map(i=>parseInt(text.slice(i,i+2),16)).concat(255);}
function fill(w,h,c){const raw=new Uint8Array(w*h*4);for(let i=0;i<w*h;i++)raw.set(c,i*4);return raw;}
function draw(raw,w,h,ops,palette){if(!Array.isArray(ops))throw Error('Joined recipe must be an operation array');for(const op of ops){if(!Array.isArray(op)||!['rect','frame','dot'].includes(op[0]))throw Error('Unknown joined drawing operation');const dot=op[0]==='dot',len=dot?4:6;if(op.length!==len)throw Error('Joined operation arity');const [x,y]=[op[1],op[2]],rw=dot?1:op[3],rh=dot?1:op[4],c=palette[op[len-1]];if(![x,y,rw,rh].every(Number.isInteger)||x<0||y<0||rw<1||rh<1||x+rw>w||y+rh>h||!Object.hasOwn(palette,op[len-1])||!Array.isArray(c)||c.length!==4)throw Error('Joined operation bounds/color');for(let py=y;py<y+rh;py++)for(let px=x;px<x+rw;px++){if(op[0]==='frame'&&px>x&&px<x+rw-1&&py>y&&py<y+rh-1)continue;raw.set(c,(py*w+px)*4);}}return raw;}
function crop(raw,w,tx,ty){const tile=new Uint8Array(16*16*4);for(let y=0;y<16;y++)tile.set(raw.subarray(((ty*16+y)*w+tx*16)*4,((ty*16+y)*w+tx*16+16)*4),y*16*4);return tile;}
function uv(face,u,v,d){switch(face){case'north':return[1-u,1-v];case'south':return[u,1-v];case'west':return[d,1-v];case'east':return[2-d,1-v];case'up':return[u,d];case'down':return[u,2-d];default:throw Error('Unknown canonical face');}}
function cellAt(face,x,y){switch(face){case'north':return[1-x,1-y,0];case'south':return[x,1-y,2];case'west':return[0,1-y,x];case'east':return[1,1-y,2-x];case'up':return[x,1,y];case'down':return[x,0,2-y];}}
function exampleRole(u,v,d){if(d===0&&u===0)return v===0?'controller':'operating_panel';if(d===2&&(u===0&&v===0||u===1&&v===1))return'power_port';return'casing';}
export function computeJoinedPanels(cfg){
  if(!cfg||cfg.version!==1||Object.keys(cfg).some(k=>!['version','palette','faces','markings','unformedPanel'].includes(k)))throw Error('Invalid joinedPanels schema');
  if(!cfg.palette||typeof cfg.palette!=='object')throw Error('Joined palette missing');const palette=Object.fromEntries(Object.entries(cfg.palette).map(([k,v])=>[k,color(v)]));if(!palette.steel)throw Error('Joined steel base color missing');
  if(!cfg.faces||Object.keys(cfg.faces).sort().join(',')!==Object.keys(sizes).sort().join(','))throw Error('Exactly six joined face recipes required');
  if(!cfg.markings||Object.keys(cfg.markings).sort().join(',')!=='controller,operating_panel,power_port,water_inlet')throw Error('Exactly four functional markings required');
  const canvases={};for(const [face,[w,h]]of Object.entries(sizes))canvases[face]=draw(fill(w,h,palette.steel),w,h,cfg.faces[face],palette);
  // Validate ALL role recipes before creating outputs, even a currently hidden/internal role tile.
  for(const role of ['controller','operating_panel','power_port','water_inlet'])draw(fill(16,16,palette.steel),16,16,cfg.markings[role],palette);
  const unformed=draw(fill(16,16,palette.steel),16,16,cfg.unformedPanel,palette),pngs=new Map(),models=new Map(),tiles=new Map();
  const getTile=(role,face,x,y)=>{const special=role==='power_port'||role==='water_inlet'||face==='north'&&(role==='controller'||role==='operating_panel'),key=(special?role:'shell')+'_'+face+'_u'+x+'v'+y;if(!tiles.has(key)){const[w,h]=sizes[face];if(x<0||y<0||x>=w/16||y>=h/16)throw Error('Joined tile outside canonical face');const raw=crop(canvases[face],w,x,y);if(special)draw(raw,16,16,cfg.markings[role],palette);tiles.set(key,raw);pngs.set(A+'textures/block/joined_'+key+'.png',png(raw,16,16));}return{ref:'lithmod:block/joined_'+key,raw:tiles.get(key)};};
  for(const role of roles){
    for(let part=0;part<12;part++){const u=Math.floor(part/6),v=Math.floor(part%6/3),d=part%3,textures={};for(const face of Object.keys(sizes)){const[x,y]=uv(face,u,v,d);textures[face]=getTile(role,face,x,y).ref;}textures.particle=textures.north;
      models.set(A+'models/block/process_cell_'+role+'_part'+part+'_formed.json',json({parent:'minecraft:block/cube',textures}));
      models.set(A+'models/block/process_cell_'+role+'_part'+part+'_unformed.json',json({parent:'lithmod:block/process_cell_'+role+'_unformed'}));
    }
    const variants={};for(const[facing,rotation]of Object.entries(rotations)){const rotated=model=>rotation?{model,y:rotation}:{model};variants['facing='+facing+',formed=false']=rotated('lithmod:block/process_cell_'+role+'_part0_unformed');for(let part=0;part<12;part++)variants['facing='+facing+',formed=true,part='+part]=rotated('lithmod:block/process_cell_'+role+'_part'+part+'_formed');}
    if(role!=='water_inlet')models.set(A+'blockstates/process_cell_'+role+'.json',json({variants}));
  }
  const waterUnformed=draw(unformed.slice(),16,16,cfg.markings.water_inlet,palette);
  pngs.set(A+'textures/block/joined_water_inlet_unformed.png',png(waterUnformed,16,16));
  models.set(A+'models/block/process_cell_water_inlet_unformed.json',json({parent:'minecraft:block/cube_all',textures:{all:'lithmod:block/joined_water_inlet_unformed'}}));
  pngs.set(A+'textures/block/joined_operating_panel_unformed_front.png',png(unformed,16,16));
  models.set(A+'models/block/process_cell_operating_panel_unformed.json',json({parent:'minecraft:block/orientable',textures:{top:'lithmod:block/process_cell_casing_unformed',bottom:'lithmod:block/process_cell_casing_unformed',side:'lithmod:block/process_cell_casing_unformed',front:'lithmod:block/joined_operating_panel_unformed_front',particle:'lithmod:block/process_cell_casing_unformed'}}));
  models.set(A+'models/item/process_cell_operating_panel.json',json({parent:'lithmod:block/process_cell_operating_panel_unformed'}));
  models.set(A+'items/process_cell_operating_panel.json',json({model:{type:'minecraft:model',model:'lithmod:item/process_cell_operating_panel'}}));
  models.set(D+'loot_table/blocks/process_cell_operating_panel.json',json({type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'lithmod:process_cell_operating_panel'}],conditions:[{condition:'minecraft:survives_explosion'}]}]}));
  models.set(D+'recipe/crafting/process_cell_operating_panel.json',json({type:'minecraft:crafting_shaped',category:'equipment',pattern:['IRI','GCG','IRI'],key:{I:'minecraft:iron_ingot',R:'minecraft:redstone',G:'minecraft:glass',C:'minecraft:gold_ingot'},result:{id:'lithmod:process_cell_operating_panel',count:1}}));
  const preview=fill(3*56,2*64,[25,31,38,255]);let index=0;
  for(const[face,[w,h]]of Object.entries(sizes)){const ox=index%3*56+4,oy=Math.floor(index/3)*64+4;for(let y=0;y<h/16;y++)for(let x=0;x<w/16;x++){const[u,v,d]=cellAt(face,x,y),tile=getTile(exampleRole(u,v,d),face,x,y).raw;for(let py=0;py<16;py++)for(let px=0;px<16;px++)preview.set(tile.subarray((py*16+px)*4,(py*16+px+1)*4),((oy+y*16+py)*168+ox+x*16+px)*4);}index++;}
  const scale=4,zoom=new Uint8Array(168*128*scale*scale*4);for(let y=0;y<128*scale;y++)for(let x=0;x<168*scale;x++){const src=(Math.floor(y/scale)*168+Math.floor(x/scale))*4;zoom.set(preview.subarray(src,src+4),(y*168*scale+x)*4);}
  return{pngs,models,preview:png(zoom,168*scale,128*scale)};
}
