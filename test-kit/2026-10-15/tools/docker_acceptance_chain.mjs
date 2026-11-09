// Runs only against the isolated Docker acceptance API. No production writes.
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const base = 'http://127.0.0.1:8183/api/v1';
const adminPassword = process.env.MRP_ACCEPTANCE_ADMIN_PASSWORD;
if (!adminPassword) throw new Error('MRP_ACCEPTANCE_ADMIN_PASSWORD is required');
const fixture = path.join(root, 'evidence/runs/2026-11-02-bom-chain-20261102193527160379');
const output = path.join(root, 'evidence/runs/docker-acceptance-20261104');
await fs.mkdir(output, { recursive: true });
let token;
const record = { environment: 'mrp-acceptance-20261104', base, startedAt: new Date().toISOString(), steps: [] };

async function request(endpoint, { method = 'GET', body, file } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  let payload;
  if (file) {
    const form = new FormData();
    form.append('file', new Blob([await fs.readFile(file)]), path.basename(file));
    payload = form;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const start = performance.now();
  const response = await fetch(base + endpoint, { method, headers, body: payload, signal: AbortSignal.timeout(120000) });
  const result = await response.json();
  record.steps.push({ method, endpoint: endpoint.split('?')[0], http: response.status, ms: Math.round(performance.now() - start) });
  if (!response.ok || result.success === false) throw Error(`${endpoint}: HTTP ${response.status} ${JSON.stringify(result)}`);
  return { status: response.status, data: result.data };
}

async function waitFor(batchId, expected) {
  const deadline = Date.now() + 120000;
  while (Date.now() < deadline) {
    const task = (await request(`/mrp/batches/${batchId}/calculation-task`)).data;
    if (task?.status === 'SUCCEEDED') return task;
    if (task?.status === 'FAILED') throw Error(`${expected} task failed: ${JSON.stringify(task)}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw Error(`${expected} task timed out`);
}

if (process.argv.includes('--finish')) {
  const finish = { environment: record.environment, startedAt: record.startedAt };
  try {
    const previous = JSON.parse(await fs.readFile(path.join(output, 'result.json'), 'utf8'));
    if (previous.status !== 'PASS' || !previous.batchId) throw Error('Initial chain is not PASS');
    const priorFinish = await fs.readFile(path.join(output, 'finish.json'), 'utf8').then(JSON.parse).catch(() => null);
    const login = (await request('/auth/login', { method: 'POST', body: { username: 'admin', password: adminPassword } })).data;
    token = login.token;
    const batchId = previous.batchId;
    finish.batchId = batchId;
    if (!priorFinish?.publishTaskId) {
      const review = (await request(`/mrp/batches/${batchId}/reviews`)).data;
      if (review.schedule !== 'CONFIRMED' || !review.materialFingerprint) throw Error('Material review is not ready');
      finish.materialConfirmation = (await request(`/mrp/batches/${batchId}/material-confirmation`, {
        method: 'POST', body: { fingerprint: review.materialFingerprint }
      })).data;
      if (finish.materialConfirmation.material !== 'CONFIRMED') throw Error('Material confirmation did not persist');
      const capacity = (await request(`/mrp/batches/${batchId}/capacity-review`)).data;
      finish.capacityFingerprint = capacity.fingerprint;
      finish.publishTaskId = (await request(`/mrp/batches/${batchId}/publish`, {
        method: 'POST', body: { capacityFingerprint: capacity.fingerprint }
      })).data.taskId;
    } else {
      finish.materialConfirmation = priorFinish.materialConfirmation;
      finish.capacityFingerprint = priorFinish.capacityFingerprint;
      finish.publishTaskId = priorFinish.publishTaskId;
    }
    const deadline = Date.now() + 120000;
    while (Date.now() < deadline) {
      finish.publishTask = (await request(`/mrp/batches/${batchId}/lifecycle-task?kind=publish`)).data;
      if (finish.publishTask?.status === 'SUCCEEDED') break;
      if (finish.publishTask?.status === 'FAILED') throw Error(`Publish failed: ${JSON.stringify(finish.publishTask)}`);
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    if (finish.publishTask?.status !== 'SUCCEEDED') throw Error('Publish timed out');
    finish.sourceBatch = (await request(`/mrp/batches/${batchId}`)).data;
    finish.publishedBatchId = finish.publishTask.resultBatchId;
    if (!finish.publishedBatchId) throw Error('Publish task has no result batch');
    finish.batch = (await request(`/mrp/batches/${finish.publishedBatchId}`)).data;
    finish.review = (await request(`/mrp/batches/${finish.publishedBatchId}/reviews`)).data;
    if (finish.batch.status !== 'PUBLISHED' || finish.review.schedule !== 'CONFIRMED' || finish.review.material !== 'CONFIRMED')
      throw Error(`Published state incorrect: ${finish.batch.status}/${finish.review.schedule}/${finish.review.material}`);
    if (finish.sourceBatch.status !== 'SUPERSEDED') throw Error('Source batch was not superseded');
    finish.status = 'PASS';
  } catch (error) {
    finish.status = 'FAIL';
    finish.error = String(error);
    process.exitCode = 1;
  } finally {
    finish.steps = record.steps;
    finish.finishedAt = new Date().toISOString();
    await fs.writeFile(path.join(output, 'finish.json'), JSON.stringify(finish, null, 2) + '\n');
    console.log(JSON.stringify({ status: finish.status, batchId: finish.batchId, error: finish.error, evidence: path.join(output, 'finish.json') }));
  }
  process.exit();
}

try {
  const login = (await request('/auth/login', { method: 'POST', body: { username: 'admin', password: adminPassword } })).data;
  token = login.token;
  if (!token) throw Error('No acceptance token');
  record.input = {};
  for (const kind of ['forecast', 'inventory']) {
    const file = path.join(fixture, `${kind}-bom.xlsx`);
    record.input[kind] = { sha256: crypto.createHash('sha256').update(await fs.readFile(file)).digest('hex') };
    const route = kind === 'forecast' ? '/forecast-imports' : '/inventory-imports/items';
    const preview = (await request(route, { method: 'POST', file })).data;
    if (preview.errorRows !== 0) throw Error(`${kind} preview errors: ${JSON.stringify(preview.errors)}`);
    record.input[kind].preview = { taskId: preview.taskId, totalRows: preview.totalRows, errorRows: preview.errorRows };
    const confirmed = (await request(`${route}/${preview.taskId}/confirm`, { method: 'POST' })).data;
    record.input[kind].id = kind === 'forecast' ? confirmed.id : confirmed.snapshotId;
  }
  const params = new URLSearchParams({ factoryCode: 'ALL', forecastVersionId: String(record.input.forecast.id), inventorySnapshotId: String(record.input.inventory.id) });
  const batch = (await request(`/mrp/batches?${params}`, { method: 'POST' })).data;
  record.batchId = batch.id;
  record.createStatus = batch.status;
  const precheck = (await request(`/mrp/batches/${batch.id}/precheck`, { method: 'POST' })).data;
  record.precheckStatus = precheck.status;
  if (precheck.status !== 'READY_TO_CALCULATE') throw Error(`Precheck blocked: ${precheck.status}`);
  record.scheduleTaskId = (await request(`/mrp/batches/${batch.id}/calculate`, { method: 'POST' })).data.id;
  record.scheduleTask = await waitFor(batch.id, 'schedule');
  record.scheduleStatus = (await request(`/mrp/batches/${batch.id}`)).data.status;
  if (record.scheduleStatus !== 'SCHEDULE_READY') throw Error(`Unexpected schedule status ${record.scheduleStatus}`);
  const review = (await request(`/mrp/batches/${batch.id}/reviews`)).data;
  record.scheduleReviewBefore = review.schedule;
  record.scheduleReviewAfter = (await request(`/mrp/batches/${batch.id}/schedule-confirmation`, {
    method: 'POST', body: { fingerprint: review.scheduleFingerprint }
  })).data.schedule;
  if (record.scheduleReviewAfter !== 'CONFIRMED') throw Error('Schedule confirmation did not persist');
  record.materialTaskId = (await request(`/mrp/batches/${batch.id}/material-analysis`, { method: 'POST' })).data.id;
  record.materialTask = await waitFor(batch.id, 'material');
  const finalBatch = (await request(`/mrp/batches/${batch.id}`)).data;
  record.finalStatus = finalBatch.status;
  record.runId = finalBatch.activeRunId;
  record.review = (await request(`/mrp/batches/${batch.id}/reviews`)).data;
  record.summary = (await request(`/mrp/batches/${batch.id}/summary`)).data;
  if (record.finalStatus !== 'MATERIAL_REVIEW' || record.review.schedule !== 'CONFIRMED')
    throw Error(`Cross-role handoff incorrect: ${record.finalStatus}/${record.review.schedule}`);
  record.status = 'PASS';
} catch (error) {
  record.status = 'FAIL';
  record.error = String(error);
  process.exitCode = 1;
} finally {
  record.finishedAt = new Date().toISOString();
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(record, null, 2) + '\n');
  console.log(JSON.stringify({ status: record.status, batchId: record.batchId, runId: record.runId, error: record.error, evidence: path.join(output, 'result.json') }));
}
