import { execFileSync } from 'node:child_process'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const output = process.argv[2]
const batchId = Number(process.argv[3])
const resumeTaskId = Number(process.argv[4] ?? 0)
if (!output?.endsWith('.json') || !Number.isSafeInteger(batchId) || batchId <= 0)
  throw new Error('Pass a new result.json path and temporary batch ID')
const base = 'http://127.0.0.1:8184/api/v1'
const result = { status: 'RUNNING', batchId, startedAt: new Date().toISOString(), samples: [] }
let monitor

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 -N -B mrp_testkit'],
  { input: query, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim()
}

function dbCounters() {
  return Object.fromEntries(sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete','Questions','Bytes_sent');")
    .split('\n').map(row => { const [key, value] = row.split('\t'); return [key, Number(value)] }))
}

function sample() {
  try {
    const raw = execFileSync('docker', ['stats', '--no-stream', '--format', '{{.MemUsage}}|{{.CPUPerc}}',
      'mrp-bigcalc-worker-20261109'], { encoding: 'utf8' }).trim()
    result.samples.push({ at: new Date().toISOString(), raw })
  } catch (error) { result.sampleError = String(error) }
}

async function api(path, token, method = 'GET') {
  const response = await fetch(base + path, { method,
    headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(180000) })
  const body = await response.json()
  if (!response.ok || !body.success) throw new Error(`${method} ${path}: HTTP ${response.status}, ${body.error?.code ?? 'unknown'}`)
  return body.data
}

try {
  const initial = sql(`SELECT status,IFNULL(bom_snapshot_id,-1),LENGTH(bom_source_checksum),forecast_version_id,
    inventory_snapshot_id FROM mrp_calc_batch WHERE id=${batchId};`)
  if (!(resumeTaskId ? initial.startsWith('SCHEDULE_READY\t-1\t64\t16\t69') :
    initial.startsWith('READY_TO_CALCULATE\t-1\t64\t16\t69')))
    throw new Error(`Unexpected batch fixture: ${initial}`)
  result.input = initial
  const password = readFileSync('/tmp/mrp-testkit-admin-final-20261105.secret', 'utf8').trim()
  const response = await fetch(base + '/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password }) })
  const login = await response.json()
  if (!response.ok || !login.success || !login.data?.token) throw new Error('Testkit admin login failed')
  const token = login.data.token
  if (!resumeTaskId) {
    result.dbBefore = dbCounters()
    sample()
    monitor = setInterval(sample, 2000)
  }
  const started = Date.now()
  result.taskId = resumeTaskId || (await api(`/mrp/batches/${batchId}/calculate`, token, 'POST')).id
  let taskState = ''
  while (Date.now() - started < 900000) {
    taskState = sql(`SELECT status,IFNULL(result_resource_id,0),IFNULL(error_code,'') FROM calc_task WHERE id=${result.taskId};`)
    if (taskState.startsWith('SUCCEEDED\t') || taskState.startsWith('FAILED\t')) break
    await new Promise(resolve => setTimeout(resolve, 2000))
  }
  result.calculation = { elapsedMs: resumeTaskId ? null : Date.now() - started, taskState,
    resumedVerification: Boolean(resumeTaskId) }
  if (monitor) {
    clearInterval(monitor)
    monitor = undefined
    sample()
    result.dbAfter = dbCounters()
    result.dbDelta = Object.fromEntries(Object.keys(result.dbBefore).map(key =>
      [key, result.dbAfter[key] - result.dbBefore[key]]))
  }
  if (!taskState.startsWith('SUCCEEDED\t')) throw new Error(`Calculation failed or timed out: ${taskState}`)
  result.batchState = sql(`SELECT status,active_run_id,IFNULL(result_checksum,'') FROM mrp_calc_batch WHERE id=${batchId};`)
  if (!result.batchState.startsWith('SCHEDULE_READY\t'))
    throw new Error(`Batch did not reach SCHEDULE_READY: ${result.batchState}`)
  result.requirements = sql(`SELECT COUNT(*),COALESCE(SUM(gross_qty<>inventory_used_qty+net_qty),0),
    COALESCE(SUM(inventory_before_qty<>inventory_used_qty+inventory_after_qty),0),
    COALESCE(SUM(gross_qty<0 OR inventory_used_qty<0 OR inventory_after_qty<0 OR net_qty<0),0)
    FROM mrp_requirement_detail WHERE batch_id=${batchId};`)
  result.facts = sql(`SELECT (SELECT COUNT(*) FROM mrp_terminal_demand WHERE batch_id=${batchId}),
    (SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id=${batchId}),
    (SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id=${batchId});`)
  const conservation = await api(`/mrp/batches/${batchId}/plans/conservation`, token)
  result.conservation = { mismatchCount: conservation.mismatchCount,
    count: Array.isArray(conservation.items) ? conservation.items.length : undefined }
  if (result.requirements.split('\t').slice(1).some(value => value !== '0') || conservation.mismatchCount !== 0)
    throw new Error('Requirement or daily conservation mismatch')
  result.status = 'PASS_LIMITED'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (monitor) clearInterval(monitor)
  result.finishedAt = new Date().toISOString()
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, batchId, taskId: result.taskId,
  calculation: result.calculation, requirements: result.requirements,
  facts: result.facts, conservation: result.conservation, failure: result.failure }))
if (result.status === 'FAIL') process.exitCode = 1
