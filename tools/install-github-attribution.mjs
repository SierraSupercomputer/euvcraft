import{execFileSync}from'node:child_process';import{existsSync,readFileSync,writeFileSync,mkdirSync,chmodSync}from'node:fs';import{resolve,join}from'node:path';import{pathToFileURL}from'node:url';
const policy=JSON.parse(readFileSync(new URL('./github-attribution.json',import.meta.url),'utf8'));
function git(cwd,args){return execFileSync('git',['-c','safe.directory='+resolve(cwd).replaceAll('\\','/'),...args],{cwd,encoding:'utf8',stdio:['ignore','pipe','pipe'],windowsHide:true}).trim();}
export function install(cwd){
 const root=git(cwd,['rev-parse','--show-toplevel']);const custom=git(root,['config','--default','','--get','core.hooksPath']);if(custom)throw Error('Custom core.hooksPath exists; preserve it and arrange the owner check explicitly.');
 const hooks=join(resolve(root,git(root,['rev-parse','--git-common-dir'])),'hooks'),target=join(hooks,'pre-push');
 const marker='# euvcraft-owner-attribution-v1';if(existsSync(target)&&!readFileSync(target,'utf8').includes(marker))throw Error('Existing pre-push hook preserved; arrange an explicit chained owner check.');
 const script='#!/bin/sh\n'+marker+'\nexec node "$(git rev-parse --show-toplevel)/tools/check-github-attribution.mjs" "$@"\n';
 mkdirSync(hooks,{recursive:true});writeFileSync(target,script,{mode:0o755});chmodSync(target,0o755);
 git(root,['config','--local','user.name',policy.owner]);git(root,['config','--local','user.email',policy.email]);git(root,['config','--local','user.useConfigOnly','true']);
 return{owner:policy.owner,hook:target};
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){try{console.log(JSON.stringify(install(process.cwd())));}catch(error){console.error(error.message);process.exitCode=1;}}
