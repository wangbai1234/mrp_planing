import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const project = 'mrp-acceptance-20261104';
const mysql = `${project}-mysql-1`;
const api = `${project}-api-1`;
const actualProject = execFileSync('docker', ['inspect', '-f', '{{index .Config.Labels "com.docker.compose.project"}}', mysql], { encoding: 'utf8' }).trim();
if (actualProject !== project) throw Error(`Unexpected Docker project: ${actualProject}`);

const suffix = randomUUID().replaceAll('-', '').slice(0, 16);
const output = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../evidence/runs', `life010-formula-${suffix}`);
await mkdir(output, { recursive: true });
const result = { project, startedAt: new Date().toISOString(), checks: {} };
let forecastId;
let versionId;
let detailId;
let taskId;
let exportPath;

function sql(query) {
  return execFileSync('docker', ['exec', '-i', mysql, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B mrp_acceptance'],
  { input: query, encoding: 'utf8' }).trim();
}

function xmlNumber(xml, xpath) {
  return Number(execFileSync('xmllint', ['--xpath', xpath, '-'], { input: xml, encoding: 'utf8' }).trim());
}

try {
  const health = await fetch('http://127.0.0.1:8183/actuator/health', { signal: AbortSignal.timeout(10000) });
  if (health.status !== 200) throw Error(`Isolated API health: ${health.status}`);
  forecastId = Number(sql(`INSERT INTO forecast_version(version_no,file_name,file_checksum,status,created_by)
    VALUES(1,'life010.xlsx','life010_${suffix}','IMPORTED',1); SELECT LAST_INSERT_ID();`));
  versionId = Number(sql(`INSERT INTO plan_version(version_no,factory_code,forecast_version_id,rule_version,current_week_start,status,created_by)
    VALUES(1,'爱培科',${forecastId},'v1','2026-11-02','PUBLISHED',1); SELECT LAST_INSERT_ID();`));
  detailId = Number(sql(`INSERT INTO plan_detail(plan_version_id,factory_code,material_id,material_name,model,me_material_id,
    week_start_date,physical_month,source_month,slot,system_quantity,effective_quantity)
    VALUES(${versionId},'爱培科','@SUM(1,2)','Life010','=1+2','+SUM(1,2)',
      '2026-11-02','2026-11-01','2026-11-01',1,3,3); SELECT LAST_INSERT_ID();`));
  result.checks.sqlInput = sql(`SELECT material_id,model,me_material_id FROM plan_detail WHERE id=${detailId};`).split('\t');

  const base = 'http://127.0.0.1:8183/api/v1';
  const login = await fetch(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }), signal: AbortSignal.timeout(10000) });
  if (login.status !== 200) throw Error(`Fixture login: ${login.status}`);
  const token = (await login.json()).data.token;
  const create = await fetch(`${base}/plans/${versionId}/exports`, { method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: ['整机料号', '机型', '机头料号', '=1+2', '-1+2', '合计'] }), signal: AbortSignal.timeout(15000) });
  result.checks.createHttp = create.status;
  if (create.status !== 202) throw Error(`Export create: ${create.status}`);
  taskId = Number((await create.json()).data.id);
  exportPath = sql(`SELECT file_path FROM export_task WHERE id=${taskId};`);
  if (!/^\/data\/mrp\/uploads\/exports\/plan_[a-f0-9-]+\.xlsx$/.test(exportPath))
    throw Error('Export path is outside isolated directory');

  const download = await fetch(`${base}/export-tasks/${taskId}/download`, {
    headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15000) });
  result.checks.downloadHttp = download.status;
  if (download.status !== 200) throw Error(`Export download: ${download.status}`);
  const file = path.join(output, 'formula-prefix.xlsx');
  const bytes = Buffer.from(await download.arrayBuffer());
  await writeFile(file, bytes);
  result.checks.bytes = bytes.length;

  const sheet = execFileSync('unzip', ['-p', file, 'xl/worksheets/sheet1.xml']);
  const strings = execFileSync('unzip', ['-p', file, 'xl/sharedStrings.xml']);
  result.checks.formulaNodes = xmlNumber(sheet, 'count(//*[local-name()="f"])');
  result.checks.stringCells = xmlNumber(sheet, 'count(//*[local-name()="c" and @t="s"])');
  result.checks.prefixStrings = Object.fromEntries(['=1+2', '+SUM(1,2)', '-1+2', '@SUM(1,2)'].map(value => [value,
    xmlNumber(strings, `count(//*[local-name()="si" and string(.)="${value}"])`)]));
  // OOXML sharedStrings.xml interns repeated text; one entry can back multiple string cells.
  result.status = result.checks.formulaNodes === 0 && result.checks.stringCells >= 8
    && result.checks.prefixStrings['=1+2'] >= 1
    && result.checks.prefixStrings['+SUM(1,2)'] >= 1
    && result.checks.prefixStrings['-1+2'] >= 1
    && result.checks.prefixStrings['@SUM(1,2)'] >= 1 ? 'PASS' : 'FAIL';
} catch (error) {
  result.status = 'FAIL';
  result.error = String(error);
} finally {
  try {
    if (exportPath && /^\/data\/mrp\/uploads\/exports\/plan_[a-f0-9-]+\.xlsx$/.test(exportPath))
      execFileSync('docker', ['exec', api, 'rm', '-f', '--', exportPath]);
    if (taskId) sql(`DELETE FROM export_task WHERE id=${taskId};`);
    if (detailId) sql(`DELETE FROM plan_detail WHERE id=${detailId};`);
    if (versionId) sql(`DELETE FROM plan_version WHERE id=${versionId};`);
    if (forecastId) sql(`DELETE FROM forecast_version WHERE id=${forecastId};`);
    result.residualRows = Number(sql(`SELECT
      (SELECT COUNT(*) FROM forecast_version WHERE file_checksum='life010_${suffix}') +
      (SELECT COUNT(*) FROM plan_version WHERE id=${versionId || 0}) +
      (SELECT COUNT(*) FROM plan_detail WHERE id=${detailId || 0}) +
      (SELECT COUNT(*) FROM export_task WHERE id=${taskId || 0});`));
    result.publishedBatchStatus = sql('SELECT status FROM mrp_calc_batch WHERE id=2;');
    if (result.residualRows !== 0 || result.publishedBatchStatus !== 'PUBLISHED') result.status = 'FAIL';
  } catch (error) {
    result.status = 'FAIL';
    result.cleanupError = String(error);
  }
  result.finishedAt = new Date().toISOString();
  await writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ status: result.status, checks: result.checks, residualRows: result.residualRows,
    publishedBatchStatus: result.publishedBatchStatus, evidence: path.join(output, 'result.json') }));
  if (result.status !== 'PASS') process.exitCode = 1;
}
