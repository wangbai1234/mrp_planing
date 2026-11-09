import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const container = 'mrp-acceptance-20261104-mysql-1';
const project = execFileSync('docker', ['inspect', '-f', '{{index .Config.Labels "com.docker.compose.project"}}', container], { encoding: 'utf8' }).trim();
if (project !== 'mrp-acceptance-20261104') throw Error(`Unexpected Docker project: ${project}`);

const base = 'http://127.0.0.1:8183/api/v1';
const adminPassword = process.env.MRP_ACCEPTANCE_ADMIN_PASSWORD;
if (!adminPassword) throw Error('MRP_ACCEPTANCE_ADMIN_PASSWORD is required');
const output = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../evidence/runs',
  `legacy-phase1-gate-${new Date().toISOString().replace(/[-:.TZ]/g, '')}`);
await mkdir(output, { recursive: true });
const evidence = { project, base, startedAt: new Date().toISOString(), checks: [] };
const suffix = randomUUID().replaceAll('-', '').slice(0, 16);
const username = `legacy_gate_${suffix}`;
const readonlyUsername = `readonly_gate_${suffix}`;
const peerUsername = `export_peer_${suffix}`;
let forecastId;
let allowedId;
let deniedId;
let userId;
let readonlyUserId;
let peerUserId;
let allowedTaskId;
let deniedTaskId;
let exportTaskId;
let pathProbeTaskId;
let liveExportTaskId;
let liveExportFilePath;

function sql(query) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B mrp_acceptance'],
  { input: query, encoding: 'utf8' }).trim();
}

async function request(name, endpoint, { method = 'GET', token, body } = {}) {
  const response = await fetch(base + endpoint, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000)
  });
  const payload = await response.json();
  evidence.checks.push({ name, method, path: endpoint.split('?')[0], http: response.status });
  return { status: response.status, data: payload.data };
}

try {
  const health = await fetch('http://127.0.0.1:8183/actuator/health');
  if (health.status !== 200) throw Error(`Isolated API health: ${health.status}`);
  forecastId = Number(sql(`INSERT INTO forecast_version(version_no,file_name,file_checksum,status,created_by)
    VALUES(1,'legacy-gate.xlsx','legacy_gate_${suffix}','IMPORTED',1); SELECT LAST_INSERT_ID();`));
  allowedId = Number(sql(`INSERT INTO plan_version(version_no,factory_code,forecast_version_id,rule_version,current_week_start,status,created_by)
    VALUES(1,'爱培科',${forecastId},'v1','2026-11-02','PUBLISHED',1); SELECT LAST_INSERT_ID();`));
  deniedId = Number(sql(`INSERT INTO plan_version(version_no,factory_code,forecast_version_id,rule_version,current_week_start,status,created_by)
    VALUES(1,'永惠',${forecastId},'v1','2026-11-02','PUBLISHED',1); SELECT LAST_INSERT_ID();`));
  userId = Number(sql(`INSERT INTO user(username,password_hash,display_name)
    SELECT '${username}',password_hash,'Legacy gate' FROM user WHERE username='admin'; SELECT LAST_INSERT_ID();`));
  readonlyUserId = Number(sql(`INSERT INTO user(username,password_hash,display_name)
    SELECT '${readonlyUsername}',password_hash,'Read-only gate' FROM user WHERE username='admin'; SELECT LAST_INSERT_ID();`));
  peerUserId = Number(sql(`INSERT INTO user(username,password_hash,display_name)
    SELECT '${peerUsername}',password_hash,'Export peer gate' FROM user WHERE username='admin'; SELECT LAST_INSERT_ID();`));
  sql(`INSERT INTO user_role(user_id,role_id) SELECT ${userId},id FROM role WHERE code='READONLY';
    INSERT INTO user_factory_scope(user_id,factory_code) VALUES(${userId},'爱培科');
    INSERT INTO user_role(user_id,role_id) SELECT ${readonlyUserId},id FROM role WHERE code='READONLY';
    INSERT INTO user_factory_scope(user_id,factory_code) VALUES(${readonlyUserId},'爱培科'),(${readonlyUserId},'永惠');
    INSERT INTO user_role(user_id,role_id) SELECT ${peerUserId},id FROM role WHERE code='ADMIN';
    INSERT INTO user_factory_scope(user_id,factory_code) VALUES(${peerUserId},'爱培科'),(${peerUserId},'永惠');`);
  allowedTaskId = Number(sql(`INSERT INTO calc_task(task_type,business_scope,request_key,status,created_by)
    VALUES('RECALCULATION','爱培科','legacy_gate_allowed_${suffix}','SUCCEEDED',${userId}); SELECT LAST_INSERT_ID();`));
  deniedTaskId = Number(sql(`INSERT INTO calc_task(task_type,business_scope,request_key,status,created_by)
    VALUES('RECALCULATION','永惠','legacy_gate_denied_${suffix}','SUCCEEDED',${userId}); SELECT LAST_INSERT_ID();`));
  exportTaskId = Number(sql(`INSERT INTO export_task(task_type,business_scope,request_key,status,file_path,created_by)
    VALUES('PLAN_EXPORT','爱培科','legacy_gate_export_${suffix}','SUCCEEDED','/tmp/legacy-gate-${suffix}.xlsx',${userId}); SELECT LAST_INSERT_ID();`));
  pathProbeTaskId = Number(sql(`INSERT INTO export_task(task_type,business_scope,request_key,status,file_path,created_by)
    VALUES('PLAN_EXPORT','爱培科','legacy_gate_path_${suffix}','SUCCEEDED','/etc/hostname',
      (SELECT id FROM user WHERE username='admin')); SELECT LAST_INSERT_ID();`));
  const phase2TaskId = Number(sql("SELECT id FROM calc_task WHERE task_type <> 'RECALCULATION' ORDER BY id LIMIT 1;"));
  if (!phase2TaskId) throw Error('No Phase 2 task for type-isolation probe');

  const adminLogin = await request('admin login', '/auth/login', { method: 'POST', body: { username: 'admin', password: adminPassword } });
  const scopedLogin = await request('scoped login', '/auth/login', { method: 'POST', body: { username, password: adminPassword } });
  const readonlyLogin = await request('read-only login', '/auth/login', { method: 'POST', body: { username: readonlyUsername, password: adminPassword } });
  const peerLogin = await request('export peer login', '/auth/login', { method: 'POST', body: { username: peerUsername, password: adminPassword } });
  if ([adminLogin, scopedLogin, readonlyLogin, peerLogin].some(result => result.status !== 200)) throw Error('Fixture login failed');
  const admin = adminLogin.data.token;
  const scoped = scopedLogin.data.token;
  const readonly = readonlyLogin.data.token;
  const peer = peerLogin.data.token;

  const list = await request('scoped list', '/plans?page=1&pageSize=100', { token: scoped });
  const adminCurrent = await request('admin current fixture', '/plans/current', { token: admin });
  const current = await request('scoped current', '/plans/current', { token: scoped });
  const explicitCurrent = await request('scoped other-factory current', `/plans/current?factoryCode=${encodeURIComponent('永惠')}`, { token: scoped });
  const allowedGrid = await request('allowed grid', `/plans/${allowedId}/grid`, { token: scoped });
  const deniedGrid = await request('denied grid', `/plans/${deniedId}/grid`, { token: scoped });
  const deniedCompare = await request('denied compare', `/plans/compare?baseId=${allowedId}&currentId=${deniedId}`, { token: scoped });
  const allowedTask = await request('allowed old task', `/recalculations/${allowedTaskId}`, { token: scoped });
  const deniedTask = await request('denied old task', `/recalculations/${deniedTaskId}`, { token: scoped });
  const phase2Task = await request('phase2 task through old endpoint', `/recalculations/${phase2TaskId}`, { token: admin });
  const oldRecalculate = await request('old recalculate write', '/recalculations', { method: 'POST', token: admin, body: {} });
  const oldOverride = await request('old override write', `/plans/${allowedId}/overrides`, { method: 'POST', token: admin, body: {} });
  const oldPublish = await request('old publish write', '/plans/999999999999/publish', { method: 'POST', token: admin, body: {} });
  const oldDelete = await request('old override delete', '/plans/999999999999/overrides/x?weekStartDate=2026-11-02', { method: 'DELETE', token: admin });
  const exportEntry = await request('historical export validation', `/plans/${allowedId}/exports`, { method: 'POST', token: admin, body: {} });
  const liveExport = await request('real historical export', `/plans/${allowedId}/exports`, {
    method: 'POST', token: admin, body: { fields: ['整机料号', '合计'] }
  });
  if (liveExport.status !== 202 || !liveExport.data?.id) throw Error('Real export task was not created');
  liveExportTaskId = Number(liveExport.data.id);
  liveExportFilePath = sql(`SELECT file_path FROM export_task WHERE id=${liveExportTaskId};`);
  if (!/^\/data\/mrp\/uploads\/exports\/plan_[a-f0-9-]+\.xlsx$/.test(liveExportFilePath))
    throw Error('Generated export path is outside the isolated export directory');
  const ownDownload = await fetch(`${base}/export-tasks/${liveExportTaskId}/download`, {
    headers: { Authorization: `Bearer ${admin}` }, signal: AbortSignal.timeout(15000)
  });
  const ownBytes = new Uint8Array(await ownDownload.arrayBuffer());
  evidence.checks.push({ name: 'real export download', method: 'GET',
    path: `/export-tasks/${liveExportTaskId}/download`, http: ownDownload.status, bytes: ownBytes.length });
  const peerDownload = await fetch(`${base}/export-tasks/${liveExportTaskId}/download`, {
    headers: { Authorization: `Bearer ${peer}` }, signal: AbortSignal.timeout(15000)
  });
  evidence.checks.push({ name: 'same-permission peer download', method: 'GET',
    path: `/export-tasks/${liveExportTaskId}/download`, http: peerDownload.status });
  await peerDownload.body?.cancel();
  execFileSync('docker', ['exec', 'mrp-acceptance-20261104-api-1', 'rm', '--', liveExportFilePath]);
  const expiredDownload = await fetch(`${base}/export-tasks/${liveExportTaskId}/download`, {
    headers: { Authorization: `Bearer ${admin}` }, signal: AbortSignal.timeout(15000)
  });
  const expiredBody = await expiredDownload.json();
  evidence.checks.push({ name: 'missing export file', method: 'GET',
    path: `/export-tasks/${liveExportTaskId}/download`, http: expiredDownload.status });
  const scopedPublished = await request('scoped dual-factory batch', '/mrp/batches/2', { token: scoped });
  const scopedSummary = await request('scoped dual-factory summary', '/mrp/batches/2/summary', { token: scoped });
  const scopedExport = await request('scoped dual-factory export', '/mrp/plans/2/export?view=MACHINE_OUTPUT', { token: scoped });
  const readonlyPublished = await request('read-only dual-factory batch', '/mrp/batches/2', { token: readonly });
  const readonlyWrites = [
    await request('read-only forecast confirm', '/forecast-imports/999999999999/confirm', { method: 'POST', token: readonly }),
    await request('read-only inventory confirm', '/inventory-imports/items/999999999999/confirm', { method: 'POST', token: readonly }),
    await request('read-only daily transfer', '/mrp/batches/2/daily-transfers', { method: 'POST', token: readonly, body: {} }),
    await request('read-only publish', '/mrp/batches/2/publish', { method: 'POST', token: readonly, body: {} }),
    await request('read-only user delete', `/users/${readonlyUserId}`, { method: 'DELETE', token: readonly })
  ];
  const foreignExportTask = await request('foreign export task metadata', `/export-tasks/${exportTaskId}`, { token: admin });
  const foreignExportDownload = await request('foreign export download', `/export-tasks/${exportTaskId}/download`, { token: admin });
  const ownExportMetadata = await request('own export metadata', `/export-tasks/${pathProbeTaskId}`, { token: admin });
  const pathProbeResponse = await fetch(`${base}/export-tasks/${pathProbeTaskId}/download`, {
    headers: { Authorization: `Bearer ${admin}` }, signal: AbortSignal.timeout(15000)
  });
  evidence.checks.push({ name: 'outside export directory download', method: 'GET',
    path: `/export-tasks/${pathProbeTaskId}/download`, http: pathProbeResponse.status });
  await pathProbeResponse.body?.cancel();
  const published = await request('phase2 published batch', '/mrp/batches/2', { token: admin });
  const summary = await request('phase2 published summary', '/mrp/batches/2/summary', { token: admin });
  const activeSession = await request('active scoped session', '/plans', { token: scoped });
  const viewPermissionId = Number(sql("SELECT id FROM permission WHERE code='schedule:view' LIMIT 1;"));
  if (!viewPermissionId) throw Error('Missing schedule:view permission fixture');
  const revoked = await request('revoke scoped view permission', `/users/${userId}/permissions`, {
    method: 'PUT', token: admin, body: { permissions: [{ permissionId: viewPermissionId, effect: 'DENY' }] }
  });
  const revokedSession = await request('revoked old session', '/plans', { token: scoped });
  const disabled = await request('disable scoped account', `/users/${userId}/status`, {
    method: 'PUT', token: admin, body: { isActive: false }
  });
  const disabledSession = await request('disabled old session', '/plans', { token: scoped });

  const ids = Array.isArray(list.data) ? list.data.map(row => row.id) : [];
  evidence.assertions = {
    allowedListed: ids.includes(allowedId), deniedHidden: !ids.includes(deniedId),
    currentFixtureVisible: Array.isArray(adminCurrent.data) && adminCurrent.data.some(row => row.id === deniedId),
    currentHidden: Array.isArray(current.data) && !current.data.some(row => row.id === deniedId),
    explicitCurrentHidden: explicitCurrent.status === 200 && Array.isArray(explicitCurrent.data) && explicitCurrent.data.length === 0,
    allowedGrid: allowedGrid.status === 200, deniedGrid: deniedGrid.status === 403,
    deniedCompare: deniedCompare.status === 403,
    allowedTask: allowedTask.status === 200 && allowedTask.data?.id === allowedTaskId,
    deniedTask: deniedTask.status === 403,
    phase2TaskHidden: phase2Task.status === 404,
    writesDenied: [oldRecalculate, oldOverride, oldPublish, oldDelete].every(result => result.status === 403),
    exportAvailable: exportEntry.status === 400,
    dualFactoryDenied: scopedPublished.status === 403 && scopedSummary.status === 403,
    dualFactoryExportDenied: scopedExport.status === 403,
    readonlyWritesDenied: readonlyPublished.status === 200 && readonlyWrites.every(result => result.status === 403),
    exportTaskOwnerDenied: foreignExportTask.status === 403 && foreignExportDownload.status === 403,
    realDownloadAndPeerDenied: ownDownload.status === 200 && ownBytes[0] === 0x50 && ownBytes[1] === 0x4b
      && peerDownload.status === 403,
    missingFileNoPathLeak: expiredDownload.status === 404
      && !JSON.stringify(expiredBody).includes(liveExportFilePath),
    exportMetadataHidesPath: ownExportMetadata.status === 200 && !Object.hasOwn(ownExportMetadata.data, 'filePath'),
    outsideExportDirectoryDenied: pathProbeResponse.status === 404,
    revokedSessionDenied: activeSession.status === 200 && revoked.status === 200 && revokedSession.status === 403,
    disabledSessionDenied: activeSession.status === 200 && disabled.status === 200 && disabledSession.status === 401,
    publishedPreserved: published.status === 200 && published.data?.status === 'PUBLISHED' && summary.status === 200
  };
  evidence.status = Object.values(evidence.assertions).every(Boolean) ? 'PASS' : 'FAIL';
} catch (error) {
  evidence.status = 'FAIL';
  evidence.error = String(error);
} finally {
  try {
    if (userId) sql(`DELETE FROM audit_log WHERE resource_type='USER' AND resource_id='${userId}' AND action IN ('DISABLE','UPDATE_PERMISSIONS');`);
    if (allowedTaskId && deniedTaskId) sql(`DELETE FROM calc_task WHERE id IN (${allowedTaskId},${deniedTaskId});`);
    if (exportTaskId) sql(`DELETE FROM export_task WHERE id=${exportTaskId};`);
    if (liveExportFilePath && /^\/data\/mrp\/uploads\/exports\/plan_[a-f0-9-]+\.xlsx$/.test(liveExportFilePath))
      execFileSync('docker', ['exec', 'mrp-acceptance-20261104-api-1', 'rm', '-f', '--', liveExportFilePath]);
    if (liveExportTaskId) sql(`DELETE FROM export_task WHERE id=${liveExportTaskId};`);
    if (pathProbeTaskId) sql(`DELETE FROM export_task WHERE id=${pathProbeTaskId};`);
    if (userId) sql(`DELETE FROM user_permission WHERE user_id=${userId};`);
    if (userId) sql(`DELETE FROM user_factory_scope WHERE user_id=${userId}; DELETE FROM user_role WHERE user_id=${userId}; DELETE FROM user WHERE id=${userId};`);
    if (readonlyUserId) sql(`DELETE FROM user_factory_scope WHERE user_id=${readonlyUserId}; DELETE FROM user_role WHERE user_id=${readonlyUserId}; DELETE FROM user WHERE id=${readonlyUserId};`);
    if (peerUserId) sql(`DELETE FROM user_factory_scope WHERE user_id=${peerUserId}; DELETE FROM user_role WHERE user_id=${peerUserId}; DELETE FROM user WHERE id=${peerUserId};`);
    if (allowedId && deniedId) sql(`DELETE FROM plan_version WHERE id IN (${allowedId},${deniedId});`);
    if (forecastId) sql(`DELETE FROM forecast_version WHERE id=${forecastId};`);
    evidence.residualRows = Number(sql(`SELECT
      (SELECT COUNT(*) FROM user WHERE username IN ('${username}','${readonlyUsername}','${peerUsername}')) +
      (SELECT COUNT(*) FROM forecast_version WHERE file_checksum='legacy_gate_${suffix}') +
      (SELECT COUNT(*) FROM plan_version WHERE id IN (${allowedId || 0},${deniedId || 0})) +
      (SELECT COUNT(*) FROM calc_task WHERE request_key IN ('legacy_gate_allowed_${suffix}','legacy_gate_denied_${suffix}')) +
      (SELECT COUNT(*) FROM export_task WHERE request_key='legacy_gate_export_${suffix}') +
      (SELECT COUNT(*) FROM export_task WHERE id=${liveExportTaskId || 0}) +
      (SELECT COUNT(*) FROM export_task WHERE request_key='legacy_gate_path_${suffix}') +
      (SELECT COUNT(*) FROM audit_log WHERE resource_type='USER' AND resource_id='${userId || 0}' AND action IN ('DISABLE','UPDATE_PERMISSIONS')) +
      (SELECT COUNT(*) FROM user_permission WHERE user_id=${userId || 0});`));
    evidence.fixtureCleaned = evidence.residualRows === 0;
    if (!evidence.fixtureCleaned) evidence.status = 'FAIL';
  } catch (error) {
    evidence.fixtureCleaned = false;
    evidence.cleanupError = String(error);
    evidence.status = 'FAIL';
  }
  evidence.finishedAt = new Date().toISOString();
  await writeFile(path.join(output, 'result.json'), JSON.stringify(evidence, null, 2) + '\n');
  console.log(JSON.stringify({ status: evidence.status, assertions: evidence.assertions,
    fixtureCleaned: evidence.fixtureCleaned, evidence: path.join(output, 'result.json') }));
  if (evidence.status !== 'PASS') process.exitCode = 1;
}
