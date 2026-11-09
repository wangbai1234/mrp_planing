// Separate bulk HTTP check; never substitutes for rolling-details pagination acceptance.
import fs from 'node:fs/promises';import path from 'node:path';
const folder=path.resolve(process.argv[2]);const m=JSON.parse(await fs.readFile(path.join(folder,'manifest.json'),'utf8'));
if(m.baseURL!=='http://127.0.0.1:8080'||!Number.isInteger(m.batchId))throw Error('Invalid main manifest');
if(!process.env.UAT_PASSWORD)throw Error('Set UAT_PASSWORD');
const base=m.baseURL+'/api/v1';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.UAT_USERNAME||'admin',password:process.env.UAT_PASSWORD}),signal:AbortSignal.timeout(10000)});
const token=(await login.json()).data?.token;if(!token)throw Error('Login failed');
const timings=[];
async function req(p){const begin=performance.now();const r=await fetch(base+p,{headers:{Authorization:'Bearer '+token},signal:AbortSignal.timeout(180000)});const bytes=await r.text();const j=JSON.parse(bytes);timings.push({path:p,status:r.status,ms:Math.round(performance.now()-begin),bytes:Buffer.byteLength(bytes)});if(!r.ok||!j.success)throw Error('HTTP '+r.status);return j.data;}
const batch=await req('/mrp/batches/'+m.batchId);if(!['READY','PUBLISHED'].includes(batch.status)||!batch.activeRunId||(m.runId&&m.runId!==batch.activeRunId))throw Error('No matching ready run');
const facts=await req('/mrp/batches/'+m.batchId+'/daily-plans?startDate=2026-10-01&endDate=2027-04-30');
if(!Array.isArray(facts))throw Error('Unexpected facts');
const after=await req('/mrp/batches/'+m.batchId);if(after.activeRunId!==batch.activeRunId||after.version!==batch.version)throw Error('Run changed during collection');
await fs.writeFile(path.join(folder,'facts-bulk-http.json'),JSON.stringify(facts));
await fs.writeFile(path.join(folder,'bulk-http-collection.json'),JSON.stringify({status:'COLLECTED_AWAITING_ORACLE',method:'GET daily-plans bulk HTTP; not rolling-details pagination',batchId:m.batchId,runId:batch.activeRunId,rows:facts.length,timings,finishedAt:new Date().toISOString()},null,2));
console.log('Bulk HTTP collected '+facts.length+' facts; '+JSON.stringify(timings));
