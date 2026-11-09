// Guarded live HTTP fixture runner. Does not visit or write master material/category/BOM APIs.
import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const kind=process.argv[2]||'accuracy';
if(!['accuracy','accuracy-stock','all-leaf','load-frontier','accuracy-distinct','load-distinct'].includes(kind))throw Error('Unknown case');
const origin=process.env.UAT_BASE_URL||'http://127.0.0.1:8180';
if(!['http://127.0.0.1:8180','http://localhost:8180'].includes(origin))throw Error('Runner only permits isolated port 8180');
if(!process.env.UAT_PASSWORD)throw Error('Set UAT_PASSWORD (test user)');
const fixture=kind;
const cfg=JSON.parse(await fs.readFile(path.join(root,'data',kind+'.json'),'utf8'));
const stamp=new Date().toISOString().replace(/[:.]/g,'-');const out=path.join(root,'evidence/runs',kind+'-'+stamp);await fs.mkdir(out,{recursive:true});
let token;const timings=[];let manifest={case:kind,baseURL:origin,startedAt:new Date().toISOString(),status:'RUNNING',evidence:out};
async function save(n,v){await fs.writeFile(path.join(out,n),JSON.stringify(v,null,2)+'\n');}
async function req(p,{method='GET',body,file}={}){
 if(/^\/(materials?|material-categor|bom)(\/|$)/.test(p))throw Error('Protected API');
 const headers=token?{Authorization:'Bearer '+token}:{};let payload;
 if(file){const data=new FormData();data.append('file',new Blob([await fs.readFile(path.join(root,'data/xlsx',file+'.xlsx'))]),file+'.xlsx');payload=data;}
 else if(body!==undefined){headers['Content-Type']='application/json';payload=JSON.stringify(body);}
 const begin=performance.now();const response=await fetch(origin+'/api/v1'+p,{method,headers,body:payload,signal:AbortSignal.timeout(180000)});
 const value=await response.json();timings.push({path:p.split('?')[0],method,status:response.status,ms:Math.round(performance.now()-begin)});
 if(!response.ok||value.success===false)throw Error(response.status+' '+p+' '+JSON.stringify(value));
 return value.data;
}
async function configImport(route,file){const p=await req('/mrp/'+route+'/import/preview',{method:'POST',file});if(Number(p.errorRows)>0||(p.errors||[]).length)throw Error('Config preview errors: '+JSON.stringify(p));return req('/mrp/'+route+'/import',{method:'POST',file});}
try{
 await save('fixture.json',cfg);
 const login=await req('/auth/login',{method:'POST',body:{username:process.env.UAT_USERNAME||'admin',password:process.env.UAT_PASSWORD}});token=login.token;if(!token)throw Error('No token');
 // Empty isolated environment only: prevents accidentally mixing fixture configs or cases.
 const mappings=await req('/mrp/factory-warehouse-mappings');if(mappings.length && !(process.env.UAT_REUSE_FIXTURE==='1' && mappings.every(m=>['UAT_APK','UAT_YH'].includes(m.warehouseCode))))throw Error('Fresh isolated DB required: mappings already exist. Preserve evidence; use another isolated run instead of blind cleanup.');
 await configImport('factory-warehouse-mappings','config-factory');
 await configImport('business-weeks','config-business-weeks');
 await configImport('production-calendars','config-calendar');
 await configImport('stock-policies','config-stock-'+fixture);
 await configImport('material-policies','config-material-'+(fixture==='accuracy-stock'?'accuracy':fixture));
 const role=await req('/mrp/category-role-versions',{method:'POST'});
 await req('/mrp/category-role-versions/'+role.id+'/details',{method:'POST',body:JSON.parse(await fs.readFile(path.join(root,'data/category-roles.json'),'utf8'))});
 await req('/mrp/category-role-versions/'+role.id+'/publish',{method:'POST'});
 const forecast=await req('/forecast-imports',{method:'POST',file:'forecast-'+(kind==='accuracy-stock'?'accuracy':kind)});
 await save('forecast-preview.json',forecast);if(forecast.errorRows||forecast.errors?.length)throw Error('Forecast import validation failed');
 const fv=await req('/forecast-imports/'+forecast.taskId+'/confirm',{method:'POST'});
 const inventory=await req('/inventory-imports/items',{method:'POST',file:'inventory-'+fixture});
 await save('inventory-preview.json',inventory);if(inventory.errorRows||inventory.errors?.length)throw Error('Inventory import validation failed');
 const iv=await req('/inventory-imports/items/'+inventory.taskId+'/confirm',{method:'POST'});
 const source=JSON.parse(await fs.readFile(path.join(root,'data/source-manifest.json'),'utf8'));
 const selectedSnapshotId=Number(process.env.UAT_BOM_SNAPSHOT_ID || source.snapshot.id);
 const params=new URLSearchParams({factoryCode:'ALL',forecastVersionId:String(fv.id),inventorySnapshotId:String(iv.snapshotId),bomSnapshotId:String(selectedSnapshotId),categoryRoleVersionId:String(role.id)});
 const batch=await req('/mrp/batches?'+params,{method:'POST'});
 manifest={...manifest,batchId:batch.id,bomSnapshotId:selectedSnapshotId,forecastVersionId:fv.id,inventorySnapshotId:iv.snapshotId,categoryRoleVersionId:role.id};await save('manifest.json',manifest);
 const check=await req('/mrp/batches/'+batch.id+'/precheck',{method:'POST'});await save('precheck.json',check);
 if(check.status!=='READY_TO_CALCULATE'){await save('issues.json',await req('/mrp/batches/'+batch.id+'/issues'));throw Error('Precheck not ready: '+check.status);}
 const start=performance.now();const task=await req('/mrp/batches/'+batch.id+'/calculate',{method:'POST'});await save('calc-task.json',task);
 const deadline=Date.now()+Number(process.env.UAT_TIMEOUT_SECONDS||900)*1000;let latest;
 do{latest=await req('/mrp/batches/'+batch.id);await save('last-batch.json',latest);if(latest.status==='READY_TO_CALCULATE' && performance.now()-start>30000)throw Error('Worker returned batch to READY_TO_CALCULATE: inspect calc_task and backend.log; failed computation is not success');if(['READY','PUBLISHED','FAILED','CANCELLED'].includes(latest.status))break;await new Promise(r=>setTimeout(r,3000));}while(Date.now()<deadline);
 manifest.calculationMs=Math.round(performance.now()-start);manifest.runId=latest?.activeRunId;
 if(latest?.status!=='READY')throw Error('Calculation failed or timed out: '+latest?.status);
 await save('summary.json',await req('/mrp/batches/'+batch.id+'/summary'));
 const grid=await req('/mrp/batches/'+batch.id+'/rolling-grid');await save('grid.json',grid);
 if(grid.calendarDays[0]!=='2026-10-29'||grid.calendarDays.at(-1)!=='2027-04-30')throw Error('Incomplete horizon');
 const collectionDeadline=Date.now()+Number(process.env.UAT_COLLECTION_SECONDS||60)*1000;
 manifest.status='CALCULATED_COLLECTION_INCOMPLETE';await save('manifest.json',manifest);
 const facts=[];for(const view of ['TERMINAL','MACHINE_OUTPUT']){let page=1;let total;let count=0;
 do{if(Date.now()>collectionDeadline)throw Error('HTTP pagination collection budget exceeded; calculation succeeded. Use collect_bulk_case.mjs for separate full API verification.');const v=await req('/mrp/batches/'+batch.id+'/rolling-details?'+new URLSearchParams({page:String(page++),pageSize:'100',view,startDate:'2026-10-01',endDate:'2027-04-30'}));total=v.total;count+=v.items.length;facts.push(...v.items);await save('collection-progress.json',{view,page:page-1,viewCollected:count,viewTotal:total,allCollected:facts.length});if(!v.items.length&&count<total)throw Error('Pagination stopped early');}while(count<total);}
 await save('facts.json',facts);manifest.factRows=facts.length;manifest.status='CALCULATED_AWAITING_ORACLE';
 console.log('Calculated batch '+batch.id+'; compare independently with compare_results.py. Evidence: '+out);
}catch(error){if(manifest.status!=='CALCULATED_COLLECTION_INCOMPLETE')manifest.status='FAILED';manifest.error=String(error);console.error(String(error));process.exitCode=1;}
finally{manifest.finishedAt=new Date().toISOString();await save('manifest.json',manifest);await save('http-timings.json',timings);}
