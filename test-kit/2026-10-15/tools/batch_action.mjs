// Explicit lifecycle operations on isolated port only; records each response.
import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath} from 'node:url';
const [action,id]=process.argv.slice(2);if(!['cancel','publish'].includes(action)||!/^\d+$/.test(id||''))throw Error('Usage: batch_action.mjs cancel|publish ID');
if(!process.env.UAT_PASSWORD)throw Error('Set UAT_PASSWORD');
const base='http://127.0.0.1:8180/api/v1';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.UAT_USERNAME||'admin',password:process.env.UAT_PASSWORD})});const token=(await login.json()).data?.token;if(!token)throw Error('Login failed');
const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'};let body;
if(action==='publish'){const r=await fetch(base+'/mrp/batches/'+id+'/capacity-review',{headers});const j=await r.json();if(!r.ok||!j.success)throw Error(JSON.stringify(j));body=JSON.stringify({capacityFingerprint:j.data.fingerprint});}
const r=await fetch(base+'/mrp/batches/'+id+'/'+action,{method:'POST',headers,body});const j=await r.json();
const out=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../evidence/lifecycle-'+id+'-'+action+'-'+Date.now()+'.json');await fs.writeFile(out,JSON.stringify(j,null,2));console.log(r.status,JSON.stringify(j));if(!r.ok||!j.success)process.exitCode=1;
