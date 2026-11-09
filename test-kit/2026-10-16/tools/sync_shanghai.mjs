// Targeted sync acceptance, isolated database only. Never emits credentials or tokens.
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const base='http://127.0.0.1:8180/api/v1';
const evidence=new URL('../evidence/repair/bom-sync-live.json',import.meta.url);
let token;
async function request(route,method='GET',body) {
 const headers=token?{Authorization:'Bearer '+token}:{};
 if(body)headers['Content-Type']='application/json';
 const response=await fetch(base+route,{method,headers,body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(240000)});
 const value=await response.json();
 assert(response.ok&&value.success!==false,JSON.stringify({route,status:response.status,error:value.error}));
 return value.data;
}
token=(await request('/auth/login','POST',{username:'admin',password:process.env.UAT_PASSWORD})).token;
const started=Date.now();
const sync=await request('/bom/sync','POST');
assert(sync.success);
const snapshot=await request('/mrp/bom-snapshots','POST');
assert.equal(snapshot.status,'READY');
const result={sync,snapshot,elapsedMs:Date.now()-started};
await fs.writeFile(evidence,JSON.stringify(result,null,2));
console.log(JSON.stringify(result));
