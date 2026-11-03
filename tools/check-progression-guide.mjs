import fs from 'node:fs';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),guide='docs/playtest-progression.md';
const read=p=>fs.readFileSync(join(root,p),'utf8');
const text=read(guide),required=['basics','build-reference',...Array.from({length:7},(_,i)=>'tier-'+(i+1)),'calibration','cleanrooms','automation','assembly','recycling','jei','troubleshooting','playtest-checklist','known-issues'];
const anchors=[...text.matchAll(/<a id="([^"]+)"><\/a>/g)].map(m=>m[1]);
for(const id of required)if(anchors.filter(a=>a===id).length!==1)throw Error('Missing or duplicate current section '+id);
if(/\]\((manual-walkthrough|progression-m1|jei\.md)/.test(text))throw Error('Current guide must not depend on a historical walkthrough');
const redirects=fs.readdirSync(join(root,'docs')).filter(p=>p.startsWith('manual-walkthrough')&&p.endsWith('.md')).concat('progression-m1.md','jei.md');
for(const name of redirects){const old=read('docs/'+name);if(old.length>650||!old.includes('playtest-progression.md#'))throw Error('Unconsolidated player instructions: '+name);}
const issueFiles=['docs/issues/README.md',...fs.readdirSync(join(root,'docs/issues')).filter(p=>p.endsWith('.md')&&p!=='README.md').map(p=>'docs/issues/'+p),...fs.readdirSync(join(root,'docs/issues/completed')).filter(p=>p.endsWith('.md')).map(p=>'docs/issues/completed/'+p)];
for(const source of ['README.md',guide,'docs/releases/0.8.0.md',...redirects.map(p=>'docs/'+p),...issueFiles]){
    const content=read(source);
    for(const [,target]of content.matchAll(/\]\(([^)]+)\)/g)){
        if(/^(https?:|mailto:)/.test(target))continue;
        const [path,anchor]=target.split('#');const file=path?resolve(root,dirname(source),path):join(root,source);
        if(!fs.existsSync(file))throw Error('Broken local link '+source+' -> '+target);
        if(anchor&&file===join(root,guide)&&!anchors.includes(anchor))throw Error('Broken guide anchor '+source+' -> '+target);
    }
}
const machine=JSON.parse(read('src/main/resources/data/lithmod/machines/scrap_recycler.json'));
const recipe=JSON.parse(read('src/main/resources/data/lithmod/machine_recipes/recycle_wafer_scrap.json'));
if(recipe.ticks*machine.energy_per_tick!==16000||!text.includes('16,000 FE'))throw Error('Recycler work/guide mismatch');
const current=read('README.md');if(/manual-walkthrough|docs\/jei\.md|T1–T5 progression/.test(current))throw Error('README must point to the current complete guide');
if(!text.includes('117 pages across 26 controller stations'))throw Error('Refresh recipe catalog inventory');
if(!text.includes('70 T6 resist')||!text.includes('80 T7 resist')||!text.includes('Coating Track')||!text.includes('Wet-Clean'))throw Error('Current later-tier routes missing');
console.log(`Checked ${required.length} guide anchors, ${redirects.length} redirects and current README/issue/release links`);
