import{execFileSync}from'node:child_process';
import{readFileSync}from'node:fs';import{resolve}from'node:path';import{fileURLToPath,pathToFileURL}from'node:url';
export const policy=JSON.parse(readFileSync(new URL('./github-attribution.json',import.meta.url),'utf8'));
function git(cwd,args){return execFileSync('git',['-c','safe.directory='+resolve(cwd).replaceAll('\\','/'),...args],{cwd,encoding:'utf8',stdio:['ignore','pipe','pipe'],windowsHide:true});}
export function verifyUpdates(cwd,input){
 const commits=new Map();let updates=0;
 for(const line of input.split(/\r?\n/).filter(Boolean)){
  const fields=line.trim().split(/\s+/);if(fields.length!==4||!fields[2].startsWith('refs/')||![fields[1],fields[3]].every(x=>/^[0-9a-f]{40}$/.test(x)))throw Error('Malformed pre-push update');
  const[,local,,remote]=fields;updates++;if(/^0+$/.test(local))continue;
  const revisions=[local];if(!/^0+$/.test(remote)){try{git(cwd,['cat-file','-e',remote+'^{commit}']);revisions.push('^'+remote);}catch{/* Unknown remote history: inspect the whole proposed history. */}}
  const raw=git(cwd,['log','--no-use-mailmap','-z','--format=%H%x00%an%x00%ae%x00%cn%x00%ce',...revisions]);
  if(!raw)continue;const rows=raw.split('\0');if(rows.at(-1)==='')rows.pop();if(rows.length%5)throw Error('Malformed Git author inventory');
  for(let i=0;i<rows.length;i+=5)commits.set(rows[i],rows.slice(i+1,i+5));
 }
 const failures=[];for(const[sha,[an,ae,cn,ce]]of commits){
  const author=an===policy.owner&&policy.allowedAuthorEmails.includes(ae);
  const committer=cn===policy.owner&&policy.allowedAuthorEmails.includes(ce)||cn===policy.serviceCommitter.name&&ce===policy.serviceCommitter.email;
  if(!author||!committer)failures.push(sha.slice(0,12)+' ('+(!author?'author '+an:'committer '+cn)+')');
 }
 if(failures.length)throw Error('Push blocked: '+failures.length+' commit(s) have an unapproved identity: '+failures.slice(0,5).join(', ')+'. Configure '+policy.owner+' and correct the commits. Do not merge preserved pre-correction branches.');
 return{updates,checkedCommits:commits.size};
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){try{verifyUpdates(process.cwd(),readFileSync(0,'utf8'));}catch(error){console.error('[owner-attribution] '+error.message);process.exitCode=1;}}
