import { execFileSync } from 'node:child_process'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const output = process.argv[2]
if (!output?.endsWith('.json')) throw new Error('Pass a new result.json path')
const base = 'http://127.0.0.1:8184/api/v1'
const result = { status: 'RUNNING', startedAt: new Date().toISOString(), input: {
  factoryCode: 'ALL', forecastVersionId: 16, inventorySnapshotSourceId: 6,
  categoryRoleVersionId: 6, bom: 'current Shanghai source'
} }

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 -N -B mrp_testkit'],
  { input: query, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim()
}

function cloneItems(source, replacement) {
  const columns = sql('SHOW COLUMNS FROM inventory_item;').split('\n')
    .map(row => row.split('\t')[0]).filter(column => column !== 'id')
  const select = columns.map(column => replacement[column] ?? `\`${column}\``).join(',')
  return `INSERT INTO inventory_item (${columns.map(column => `\`${column}\``).join(',')}) ` +
    `SELECT ${select} FROM inventory_item WHERE snapshot_id=${source}`
}

async function request(path, token, method = 'GET') {
  const response = await fetch(base + path, { method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal: AbortSignal.timeout(180000) })
  const body = await response.json()
  return { status: response.status, body }
}

try {
  const password = readFileSync('/tmp/mrp-testkit-admin-final-20261105.secret', 'utf8').trim()
  const loginResponse = await fetch(base + '/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password }) })
  const login = await loginResponse.json()
  if (!loginResponse.ok || !login.success || !login.data?.token) throw new Error('Testkit admin login failed')
  const token = login.data.token
  const bom = await request('/mrp/batches/current-bom-status', token)
  result.bom = { status: bom.status, localCount: bom.body.data?.localCount }
  if (bom.status !== 200 || bom.body.data?.localCount < 100000) throw new Error('Current BOM unexpectedly small')
  const sourceRows = Number(sql('SELECT COUNT(*) FROM inventory_item WHERE snapshot_id=6;'))
  if (sourceRows !== 3112) throw new Error('Source inventory fixture changed')
  const sourceStatement = cloneItems(6, { snapshot_id: '@newSnapshot' })
  const defectiveStatement = cloneItems(6, { snapshot_id: '@newSnapshot',
    warehouse_code: "'UAT_APK_DEF'", warehouse_name: "'UAT_APK_DEF'",
    inventory_quality: "'DEFECTIVE'", odm_supplier_qty: '0', xa400_qty: '0',
    xa378_qty: '0', xa226_qty: '0', shipping_available_qty: '0',
    order_pending_qty: '0', remark: "'P0 frontier zero-quantity fixture'" }) + ' LIMIT 1'
  const fixtureId = Number(sql(`START TRANSACTION;
    INSERT INTO inventory_snapshot(snapshot_date,warehouse_code,file_name,file_checksum,status,created_by)
      SELECT snapshot_date,NULL,'acceptance-frontier-20261109',
        SHA2('acceptance-frontier-20261109-from-snapshot-6',256),'IMPORTED',created_by
      FROM inventory_snapshot WHERE id=6;
    SET @newSnapshot=LAST_INSERT_ID();
    ${sourceStatement};
    ${defectiveStatement};
    COMMIT;
    SELECT @newSnapshot;`).split('\n').at(-1))
  if (!Number.isSafeInteger(fixtureId) || fixtureId <= 6) throw new Error('Fixture snapshot was not created')
  result.input.inventorySnapshotId = fixtureId
  result.fixture = { sourceSnapshot: 6, snapshotId: fixtureId, sourceRows,
    copiedRows: Number(sql(`SELECT COUNT(*) FROM inventory_item WHERE snapshot_id=${fixtureId};`)),
    defectiveRows: Number(sql(`SELECT COUNT(*) FROM inventory_item WHERE snapshot_id=${fixtureId} AND warehouse_code='UAT_APK_DEF' AND inventory_quality='DEFECTIVE';`)) }
  if (result.fixture.copiedRows !== sourceRows + 1 || result.fixture.defectiveRows !== 1)
    throw new Error('Fixture copy counts differ')
  const query = new URLSearchParams({ factoryCode: 'ALL', forecastVersionId: '16',
    inventorySnapshotId: String(fixtureId), categoryRoleVersionId: '6' })
  const created = await request(`/mrp/batches?${query}`, token, 'POST')
  result.creation = { status: created.status, data: created.body.data,
    error: created.body.error }
  if (created.status !== 200 || !created.body.success || !created.body.data?.id)
    throw new Error(`Batch creation returned ${created.status}`)
  result.batchId = created.body.data.id
  const precheck = await request(`/mrp/batches/${result.batchId}/precheck`, token, 'POST')
  result.precheck = { status: precheck.status, data: precheck.body.data,
    error: precheck.body.error }
  if (precheck.status !== 200 || !precheck.body.success) throw new Error(`Precheck returned ${precheck.status}`)
  result.status = precheck.body.data?.status === 'READY_TO_CALCULATE' ? 'READY_TO_CALCULATE' : 'BLOCKED_PRECHECK'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  result.finishedAt = new Date().toISOString()
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, batchId: result.batchId,
  creationStatus: result.creation?.status, precheckStatus: result.precheck?.status,
  precheckState: result.precheck?.data?.status, failure: result.failure }))
if (result.status === 'FAIL') process.exitCode = 1
