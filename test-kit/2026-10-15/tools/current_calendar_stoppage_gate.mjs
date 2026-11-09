import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const base = 'http://127.0.0.1:8182/api/v1'
const password = process.env.E2E_ADMIN_PASSWORD
if (!password) throw new Error('E2E_ADMIN_PASSWORD is required')
const login = await fetch(`${base}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password })
})
if (login.status !== 200) throw new Error(`Login failed: ${login.status}`)
const token = (await login.json()).data.token
const headers = { Authorization: `Bearer ${token}` }
async function request(path, method = 'GET', body) {
  const response = await fetch(`${base}${path}`, {
    method, headers: { ...headers, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {})
  })
  return { status: response.status, body: await response.json() }
}

const factory = '爱培科'
const date = '2026-11-03'
const monthPath = `/mrp/production-calendars/month?factoryCode=${encodeURIComponent(factory)}&month=2026-11`
const original = (await request(monthPath)).body.data.find(row => row.calendarDate === date)
if (!original?.id || original.productionDay !== true) throw new Error(`Missing original production day ${factory} ${date}`)
let batchId = null
let updated = null
let failure = null
const result = { factory, date, originalId: original.id, checks: {}, cleanup: {} }
try {
  const stopped = await request(`/mrp/production-calendars/${original.id}`, 'PUT', {
    ...original, productionDay: false, dailyCapacity: '0', reason: 'UAT 显式休息日'
  })
  if (stopped.status !== 200) throw new Error(`Stopping day failed: ${stopped.status}`)
  updated = stopped.body.data
  const latest = await request('/mrp/batches/97/calendar-check?latest=true')
  result.checks.existingBatchLatest = latest.body.data
  if (latest.status !== 200 || latest.body.data.total !== 0)
    throw new Error('Explicit rest day was treated as missing calendar')

  const created = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=63', 'POST')
  result.checks.create = { status: created.status, error: created.body.error }
  if (created.status !== 200) throw new Error(`Batch creation failed: ${created.status}`)
  batchId = created.body.data.id
  result.batchId = batchId
  const frozen = await request(`/mrp/batches/${batchId}/calendar-check`)
  result.checks.frozenCalendar = frozen.body.data
  if (frozen.status !== 200 || frozen.body.data.total !== 0)
    throw new Error('Frozen explicit rest day was treated as missing calendar')
  const precheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
  result.checks.precheck = { status: precheck.status, state: precheck.body.data?.status, error: precheck.body.error }
  if (precheck.status !== 200 || JSON.stringify(precheck.body).includes('生产日历或业务周缺失'))
    throw new Error(`Explicit rest day incorrectly blocked precheck: ${precheck.status}`)
} catch (error) {
  failure = error
} finally {
  if (batchId !== null) {
    try {
      const cancelled = await request(`/mrp/batches/${batchId}/cancel`, 'POST')
      result.cleanup.batch = { status: cancelled.status, state: cancelled.body.data?.status }
      if (cancelled.status !== 200 || cancelled.body.data?.status !== 'CANCELLED') throw new Error('Batch cancellation failed')
    } catch (error) { failure ??= error }
  }
  if (updated) {
    try {
      const restored = await request(`/mrp/production-calendars/${original.id}`, 'PUT', {
        ...original, revision: updated.revision
      })
      result.cleanup.calendar = { status: restored.status, revision: restored.body.data?.revision }
      if (restored.status !== 200) throw new Error(`Calendar restoration failed: ${restored.status}`)
      const after = (await request(monthPath)).body.data.find(row => row.calendarDate === date)
      if (!after || after.productionDay !== original.productionDay || after.dailyCapacity !== original.dailyCapacity
          || after.capacityUnit !== original.capacityUnit || after.reason !== original.reason)
        throw new Error('Calendar values differ after restoration')
      result.cleanup.calendar.verified = true
    } catch (error) { failure ??= error }
  }
}

result.status = failure ? 'FAIL' : 'PASS'
if (failure) result.error = String(failure)
const directory = join('test-kit', '2026-10-15', 'evidence', 'runs', '2026-11-03-calendar-stoppage-gate')
mkdirSync(directory, { recursive: true })
writeFileSync(join(directory, 'result.json'), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ status: result.status, batchId, precheck: result.checks.precheck, calendar: result.checks.frozenCalendar, cleanup: result.cleanup }))
if (failure) throw failure
