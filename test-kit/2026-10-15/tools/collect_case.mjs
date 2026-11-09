// Recollect already calculated results; retains original failure manifest.
import fs from 'node:fs/promises';import path from 'node:path';
const folder=path.resolve(process.argv[2]);const m=JSON.parse(await fs.readFile(path.join(folder,'manifest.json'),'utf8'));if(m.baseURL!=='http://127.0.0.1:8180'||!Number.isInteger(m.batchId))throw Error('Invalid isolated manifest');
const base=m.baseURL+'/api/v1';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.UAT_USERNAME||'admin',password:process.env.UAT_PASSWORD})});const token=(await login.json()).data?.token;if(!token)throw Error('Login failed');
async function req(p){const r=await fetch(base+p,{headers:{Authorization:'Bearer '+token},signal:AbortSignal.timeout(180000)});const j=await r.json();if(!r.ok||!j.success)throw Error(JSON.stringify(j));return j.data;}
const batch=await req('/mrp/batches/'+m.batchId);if(!['READY','PUBLISHED'].includes(batch.status)||batch.activeRunId!==m.runId)throw Error('No matching ready run');
const deadline=Date.now()+Number(process.env.UAT_COLLECTION_SECONDS||60)*1000;
const facts=[];for(const view of ['TERMINAL','MACHINE_OUTPUT']){let page=1,total,count=0;
do{if(Date.now()>deadline)throw Error('Pagination budget exceeded; retain calculated batch, use separate bulk collector');const v=await req('/mrp/batches/'+m.batchId+'/rolling-details?'+new URLSearchParams({page:String(page++),pageSize:'100',view,startDate:'2026-10-01',endDate:'2027-04-30'}));total=v.total;if(!v.items.length&&count<total)throw Error('Truncated');count+=v.items.length;facts.push(...v.items);}while(count<total);}
await fs.writeFile(path.join(folder,'facts.json'),JSON.stringify(facts));await fs.writeFile(path.join(folder,'collection-result.json'),JSON.stringify({status:'COLLECTED_AWAITING_ORACLE',batchId:m.batchId,runId:m.runId,rows:facts.length,finishedAt:new Date().toISOString()},null,2));console.log('Collected',facts.length);
