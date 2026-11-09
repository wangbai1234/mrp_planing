import { execFileSync } from 'node:child_process'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const container = 'mrp-testkit-mysql'
const database = 'mrp_testkit'
const base = 'http://127.0.0.1:8184/api/v1'
const source = Number(process.argv[5] ?? 13)
const output = process.argv[2]
const resumeBatch = Number(process.argv[3] ?? 0)
const resumeTask = Number(process.argv[4] ?? 0)
if (!output?.endsWith('.json')) throw new Error('Pass a new result.json path')
if (!Number.isSafeInteger(source) || source <= 0) throw new Error('Source batch ID must be positive')
if ((resumeBatch || resumeTask) && (!Number.isSafeInteger(resumeBatch) || !Number.isSafeInteger(resumeTask) || resumeBatch <= source || resumeTask <= 0))
  throw new Error('Resume requires existing temporary batch and task IDs')
const password = `A1!${randomBytes(24).toString('base64url')}`
const username = `calc_probe_${randomUUID().replaceAll('-', '').slice(0, 12)}`
const tables = ['mrp_requirement_detail', 'mrp_terminal_demand', 'mrp_daily_plan_detail']
const result = { status: 'RUNNING', sourceBatch: source, startedAt: new Date().toISOString(),
  source: {}, calculation: {}, checks: {}, cleanup: {}, metrics: {} }
let batch = 0
let taskId = 0
let monitor
let authUserId = 0

function sql(query) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    `MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -N -B ${database}`],
  { input: query, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim()
}

function columns(table) {
  return sql(`SHOW COLUMNS FROM \`${table}\`;`).split('\n').map(row => row.split('\t')[0])
}

function clone(table, where, replacements) {
  const cols = columns(table).filter(column => column !== 'id')
  return `INSERT INTO \`${table}\` (${cols.map(c => `\`${c}\``).join(',')}) SELECT ` +
    cols.map(c => replacements[c] ?? `\`${c}\``).join(',') + ` FROM \`${table}\` WHERE ${where};`
}

function digest(table, batchId) {
  const cols = columns(table).filter(c => !['id', 'batch_id', 'run_id', 'created_at', 'updated_at'].includes(c))
  const expression = cols.map(c => `IFNULL(HEX(\`${c}\`),'NULL')`).join(',')
  const lines = sql(`SELECT CONCAT_WS('|',${expression}) v FROM \`${table}\` WHERE batch_id=${batchId} ORDER BY v;`)
  return { rows: lines ? lines.split('\n').length : 0, sha256: createHash('sha256').update(lines).digest('hex') }
}

async function request(path, token, method = 'GET', data) {
  const response = await fetch(base + path, { method,
    headers: { Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
    body: data ? JSON.stringify(data) : undefined })
  const body = await response.json()
  if (!response.ok || !body.success) throw new Error(`${method} ${path}: HTTP ${response.status}, ${body.error?.code ?? 'unknown'}`)
  return body.data
}

function check(condition, message) {
  if (!condition) throw new Error(message)
}

function dbStatus() {
  return Object.fromEntries(sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete','Questions','Bytes_sent');")
    .split('\n').map(line => { const [name, value] = line.split('\t'); return [name, Number(value)] }))
}

function sampleWorker() {
  try {
    const raw = execFileSync('docker', ['stats', '--no-stream', '--format', '{{.MemUsage}}|{{.CPUPerc}}',
      process.env.MRP_CALC_STATS_CONTAINER], { encoding: 'utf8' }).trim()
    result.metrics.containerSamples.push({ at: new Date().toISOString(), raw })
  } catch (error) {
    result.metrics.sampleError = String(error)
  }
}

try {
  const sourceRow = sql(`SELECT status,active_run_id,input_checksum,result_checksum FROM mrp_calc_batch WHERE id=${source};`)
  check((sourceRow.startsWith(`PUBLISHED\t${source}\t`) || sourceRow.startsWith(`SUPERSEDED\t${source}\t`)),
    `Source batch ${source} is not published/superseded with its original run`)
  result.source.batch = sourceRow
  const parent = Number(sql(`SELECT IFNULL(revision_of_batch_id,0) FROM mrp_calc_batch WHERE id=${source};`))
  const parentSuperseded = parent > 0 && Number(sql(`SELECT IFNULL(superseded_by_batch_id,0) FROM mrp_calc_batch WHERE id=${parent};`)) === source
  const ownPools = Number(sql(`SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id=${source};`))
  const poolSource = ownPools ? source : parentSuperseded ? parent : source
  result.source.poolSource = poolSource
  result.source.poolRows = Number(sql(`SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id=${poolSource};`))
  check(Number(sql(`SELECT COUNT(*) FROM mrp_inventory_allocation WHERE pool_id IN
    (SELECT id FROM mrp_inventory_pool WHERE batch_id=${poolSource});`)) === 0,
    'Source has inventory allocations; pool ID remapping is required')
  for (const table of tables) result.source[table] = digest(table, source)

  const hash = execFileSync('htpasswd', ['-niB', 'fixture'], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':')[1]
  authUserId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${username}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) SELECT ${authUserId},id FROM role WHERE code='ADMIN';`)
  const loginResponse = await fetch(base + '/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })
  const login = await loginResponse.json()
  check(loginResponse.ok && login.success && login.data?.token, 'Testkit admin login failed')
  const token = login.data.token

  let submittedAt = Date.now()
  if (resumeBatch) {
    batch = resumeBatch
    taskId = resumeTask
    result.calculation.resumed = true
  } else {
    const statements = ['START TRANSACTION;', clone('mrp_calc_batch', `id=${source}`, {
      status: "'READY_TO_CALCULATE'", active_run_id: 'NULL', published_at: 'NULL',
      active_request_key: 'NULL', revision_of_batch_id: String(source),
      superseded_by_batch_id: 'NULL', result_checksum: 'NULL', version: '0' }),
      'SET @newBatch=LAST_INSERT_ID();']
    for (const table of ['mrp_batch_factory_scope', 'mrp_batch_business_week_scope']) {
      statements.push(clone(table, `batch_id=${source}`, { batch_id: '@newBatch' }))
    }
    statements.push(clone('mrp_inventory_pool', `batch_id=${poolSource}`, { batch_id: '@newBatch' }))
    statements.push('COMMIT;', 'SELECT @newBatch;')
    batch = Number(sql(statements.join('\n')).split('\n').at(-1))
    check(Number.isSafeInteger(batch) && batch > source, 'Could not create temporary batch')
    submittedAt = Date.now()
    const task = await request(`/mrp/batches/${batch}/calculate`, token, 'POST', {})
    taskId = task.id
  }
  result.calculation.batch = batch
  result.calculation.task = taskId
  if (!resumeBatch) {
    result.metrics.dbBefore = dbStatus()
    if (process.env.MRP_CALC_STATS_CONTAINER) {
      result.metrics.sampledContainer = process.env.MRP_CALC_STATS_CONTAINER
      result.metrics.containerSamples = []
      sampleWorker()
      monitor = setInterval(sampleWorker, 2000)
    }
  }
  let taskState = ''
  while (Date.now() - submittedAt < 600_000) {
    taskState = sql(`SELECT status,IFNULL(result_resource_id,0),IFNULL(error_code,'') FROM calc_task WHERE id=${taskId};`)
    if (taskState.startsWith('SUCCEEDED\t') || taskState.startsWith('FAILED\t')) break
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  result.calculation.elapsedMs = Date.now() - submittedAt
  result.calculation.taskState = taskState
  if (monitor) {
    clearInterval(monitor)
    monitor = undefined
    result.metrics.dbAfter = dbStatus()
    result.metrics.dbDelta = Object.fromEntries(Object.keys(result.metrics.dbBefore)
      .map(key => [key, result.metrics.dbAfter[key] - result.metrics.dbBefore[key]]))
  }
  check(taskState.startsWith('SUCCEEDED\t'), `Calculation did not succeed: ${taskState}`)
  const batchRow = sql(`SELECT status,active_run_id,input_checksum,result_checksum FROM mrp_calc_batch WHERE id=${batch};`)
  result.calculation.batchState = batchRow
  const [status, run, inputChecksum, resultChecksum] = batchRow.split('\t')
  check(status === 'READY' && Number(run) === Number(taskState.split('\t')[1]), 'Task and batch run disagree')
  check(inputChecksum === sourceRow.split('\t')[2], 'Input checksum differs')
  result.calculation.readyResultChecksum = resultChecksum
  for (const table of tables) {
    result.calculation[table] = digest(table, batch)
    check(JSON.stringify(result.calculation[table]) === JSON.stringify(result.source[table]), `${table} differs from source`)
  }
  const requirement = sql(`SELECT COUNT(*),SUM(gross_qty<>inventory_used_qty+net_qty),
    SUM(inventory_before_qty<>inventory_used_qty+inventory_after_qty),
    SUM(gross_qty<0 OR inventory_used_qty<0 OR inventory_after_qty<0 OR net_qty<0)
    FROM mrp_requirement_detail WHERE batch_id=${batch};`)
  result.checks.requirement = requirement
  check(requirement.split('\t').slice(1).every(value => value === '0'), 'Requirement conservation failed')
  const conservation = await request(`/mrp/batches/${batch}/plans/conservation`, token)
  result.checks.conservation = conservation
  check(conservation.mismatchCount === 0, 'API conservation mismatch')
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (monitor) clearInterval(monitor)
  if (batch && result.status === 'PASS') {
    const cleanupTables = ['mrp_stock_risk', 'mrp_material_policy_notice', 'mrp_daily_plan_override',
      'mrp_daily_plan_detail', 'mrp_terminal_demand', 'mrp_requirement_detail', 'mrp_unallocated_quantity',
      'mrp_decision_issue', 'mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_inventory_pool', 'mrp_calc_run']
    sql(['START TRANSACTION;', ...cleanupTables.map(table => `DELETE FROM ${table} WHERE batch_id=${batch};`),
      `DELETE FROM calc_task WHERE id=${taskId};`, `DELETE FROM mrp_calc_batch WHERE id=${batch};`, 'COMMIT;'].join('\n'))
    result.cleanup.batchRows = Number(sql(`SELECT COUNT(*) FROM mrp_calc_batch WHERE id=${batch};`))
    result.cleanup.taskRows = Number(sql(`SELECT COUNT(*) FROM calc_task WHERE id=${taskId};`))
    result.cleanup.factRows = Object.fromEntries(tables.map(table =>
      [table, Number(sql(`SELECT COUNT(*) FROM ${table} WHERE batch_id=${batch};`))]))
    if (result.cleanup.batchRows || result.cleanup.taskRows || Object.values(result.cleanup.factRows).some(Boolean)) result.status = 'FAIL'
  }
  result.sourceUnchanged = sql(`SELECT status,active_run_id,input_checksum,result_checksum FROM mrp_calc_batch WHERE id=${source};`) === result.source.batch
  if (!result.sourceUnchanged) result.status = 'FAIL'
  if (authUserId) {
    sql(`DELETE FROM audit_log WHERE operator_id=${authUserId}; DELETE FROM user_role WHERE user_id=${authUserId};
      DELETE FROM user WHERE id=${authUserId};`)
    result.authCleanup = Number(sql(`SELECT COUNT(*) FROM user WHERE id=${authUserId};`))
    if (result.authCleanup) result.status = 'FAIL'
  }
  result.finishedAt = new Date().toISOString()
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, batch, taskId, elapsedMs: result.calculation.elapsedMs,
  sourceUnchanged: result.sourceUnchanged, failure: result.failure }))
if (result.status !== 'PASS') process.exitCode = 1
