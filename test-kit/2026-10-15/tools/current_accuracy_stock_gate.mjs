import { execFileSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const output = process.argv[2]
if (!output?.endsWith('.json')) throw new Error('Pass a new result.json path')
const database = 'mrp_testkit'
const mysql = 'mrp-testkit-mysql'
const base = 'http://127.0.0.1:8184/api/v1'
const source = 7
const snapshot = 33
const result = { status: 'RUNNING', sourceBatch: source, bomSnapshot: snapshot,
  startedAt: new Date().toISOString(), checks: {}, cleanup: {} }
let batch = 0
let taskId = 0
let taskTerminal = false

function sql(query) {
  return execFileSync('docker', ['exec', '-i', mysql, 'sh', '-c',
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

function check(ok, message) { if (!ok) throw new Error(message) }

async function request(path, token, method = 'GET', data) {
  const response = await fetch(base + path, { method,
    headers: { Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
    body: data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(180000) })
  const body = await response.json()
  check(response.ok && body.success, `${method} ${path}: HTTP ${response.status}, ${body.error?.code ?? 'unknown'}`)
  return body.data
}

try {
  const sourceRow = sql(`SELECT status,forecast_version_id,inventory_snapshot_id,bom_snapshot_id,
    category_role_version_id,material_policy_version_id,production_calendar_version_id,
    factory_warehouse_mapping_version_id,stock_policy_version_id,business_week_version_id
    FROM mrp_calc_batch WHERE id=${source};`)
  check(sourceRow === 'SUPERSEDED\t15\t5\t34\t6\t5\t5\t5\t5\t29', 'Source fixture differs from original accuracy-stock import')
  check(sql(`SELECT COUNT(*),checksum FROM bom_snapshot WHERE id=${snapshot} AND status='READY';`) ===
    '1\t4235e232629a4138a25a125d9466993454c1289d7c928cd0baeb35cfd75024b1', 'Original BOM snapshot differs')
  check(Number(sql(`SELECT COUNT(*) FROM bom_snapshot_detail WHERE snapshot_id=${snapshot};`)) === 134045, 'Original BOM edge count differs')
  check(Number(sql(`SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id=${source};`)) === 162, 'Frozen source pools differ')
  check(Number(sql(`SELECT COUNT(*) FROM mrp_inventory_allocation WHERE pool_id IN
    (SELECT id FROM mrp_inventory_pool WHERE batch_id=${source});`)) === 0, 'Pool allocations require ID mapping')
  const sourceUnchanged = sql(`SELECT status,active_run_id,input_checksum,result_checksum FROM mrp_calc_batch WHERE id=${source};`)
  result.checks.source = { row: sourceRow, poolRows: 162, snapshotEdges: 134045 }

  const statements = ['START TRANSACTION;', clone('mrp_calc_batch', `id=${source}`, {
    status: "'READY_TO_CALCULATE'", bom_snapshot_id: String(snapshot), active_run_id: 'NULL',
    published_at: 'NULL', active_request_key: 'NULL', revision_of_batch_id: 'NULL',
    superseded_by_batch_id: 'NULL', result_checksum: 'NULL', issue_count: '0', unresolved_count: '0',
    version: '0', input_checksum: `SHA2(CONCAT(input_checksum,'|snapshot:${snapshot}'),256)` }),
    'SET @newBatch=LAST_INSERT_ID();']
  for (const table of ['mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_inventory_pool'])
    statements.push(clone(table, `batch_id=${source}`, { batch_id: '@newBatch' }))
  statements.push('COMMIT;', 'SELECT @newBatch;')
  batch = Number(sql(statements.join('\n')).split('\n').at(-1))
  check(Number.isSafeInteger(batch) && batch > source, 'Could not create temporary batch')
  result.batchId = batch

  const password = readFileSync('/tmp/mrp-testkit-admin-final-20261105.secret', 'utf8').trim()
  const loginResponse = await fetch(base + '/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password }) })
  const login = await loginResponse.json()
  check(loginResponse.ok && login.success && login.data?.token, 'Testkit admin login failed')
  const token = login.data.token
  const task = await request(`/mrp/batches/${batch}/calculate`, token, 'POST', {})
  taskId = task.id
  result.taskId = taskId
  const started = Date.now()
  let state = ''
  while (Date.now() - started < 600000) {
    state = sql(`SELECT status,IFNULL(result_resource_id,0),IFNULL(error_code,'') FROM calc_task WHERE id=${taskId};`)
    if (state.startsWith('SUCCEEDED\t') || state.startsWith('FAILED\t')) { taskTerminal = true; break }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  result.calculation = { elapsedMs: Date.now() - started, state }
  check(state.startsWith('SUCCEEDED\t'), `Calculation did not succeed: ${state}`)
  const batchState = sql(`SELECT status,active_run_id FROM mrp_calc_batch WHERE id=${batch};`)
  check(batchState === `READY\t${state.split('\t')[1]}`, `Batch/run mismatch: ${batchState}`)
  result.calculation.batchState = batchState
  result.checks.requirement = sql(`SELECT COUNT(*),SUM(gross_qty<>inventory_used_qty+net_qty),
    SUM(inventory_before_qty<>inventory_used_qty+inventory_after_qty),
    SUM(gross_qty<0 OR inventory_used_qty<0 OR inventory_after_qty<0 OR net_qty<0)
    FROM mrp_requirement_detail WHERE batch_id=${batch};`)
  check(result.checks.requirement.split('\t').slice(1).every(value => value === '0'), 'Requirement conservation failed')

  const facts = []
  for (const view of ['TERMINAL', 'MACHINE_OUTPUT']) {
    let page = 1
    let count = 0
    let total = 0
    do {
      const params = new URLSearchParams({ page: String(page++), pageSize: '100', view,
        startDate: '2026-10-01', endDate: '2027-04-30' })
      const response = await request(`/mrp/batches/${batch}/rolling-details?${params}`, token)
      total = response.total
      facts.push(...response.items)
      count += response.items.length
      check(response.items.length || count === total, `Pagination stopped early: ${view}`)
    } while (count < total)
    result.checks[`${view}Rows`] = count
  }
  const folder = dirname(resolve(output))
  mkdirSync(folder, { recursive: true })
  writeFileSync(`${folder}/facts.json`, JSON.stringify(facts, null, 2) + '\n')
  const compare = execFileSync('python3', ['test-kit/2026-10-15/tools/compare_results.py',
    'accuracy-stock', folder, 'facts.json', 'oracle-comparison.json'], { encoding: 'utf8' })
  result.checks.oracle = JSON.parse(compare)
  check(result.checks.oracle.status === 'PASS' && result.checks.oracle.actualKeys === 8436,
    'Original accuracy-stock oracle mismatch')
  result.checks.conservation = await request(`/mrp/batches/${batch}/plans/conservation`, token)
  check(result.checks.conservation.mismatchCount === 0, 'API conservation mismatch')
  check(sql(`SELECT status,active_run_id,input_checksum,result_checksum FROM mrp_calc_batch WHERE id=${source};`) === sourceUnchanged,
    'Frozen source batch changed')
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (batch && taskTerminal) {
    const cleanupTables = ['mrp_stock_risk', 'mrp_material_policy_notice', 'mrp_daily_plan_override',
      'mrp_daily_plan_detail', 'mrp_terminal_demand', 'mrp_requirement_detail', 'mrp_unallocated_quantity',
      'mrp_decision_issue', 'mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_inventory_pool', 'mrp_calc_run']
    try {
      sql(['START TRANSACTION;', ...cleanupTables.map(table => `DELETE FROM ${table} WHERE batch_id=${batch};`),
        `DELETE FROM calc_task WHERE id=${taskId};`, `DELETE FROM mrp_calc_batch WHERE id=${batch};`, 'COMMIT;'].join('\n'))
      result.cleanup = { batchRows: Number(sql(`SELECT COUNT(*) FROM mrp_calc_batch WHERE id=${batch};`)),
        taskRows: Number(sql(`SELECT COUNT(*) FROM calc_task WHERE id=${taskId};`)),
        factRows: Number(sql(`SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id=${batch};`)) }
      if (Object.values(result.cleanup).some(Boolean)) result.status = 'FAIL'
    } catch (error) { result.status = 'FAIL'; result.cleanupError = String(error) }
  } else if (batch) {
    result.cleanup = { skipped: 'Task not terminal; retain temporary batch for inspection' }
  }
  result.finishedAt = new Date().toISOString()
  mkdirSync(dirname(resolve(output)), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, batchId: batch, taskId,
  calculation: result.calculation, oracle: result.checks.oracle, failure: result.failure, cleanup: result.cleanup }))
if (result.status !== 'PASS') process.exitCode = 1
