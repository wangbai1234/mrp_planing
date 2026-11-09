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
const date = process.env.CALENDAR_MISSING_DATE || '2026-11-03'
if (!/^2026-(10-30|11-03)$/.test(date)) throw new Error('Only reviewed fixture dates are allowed')
const monthPath = `/mrp/production-calendars/month?factoryCode=${encodeURIComponent(factory)}&month=${date.slice(0, 7)}`
const original = (await request(monthPath)).body.data.find(row => row.calendarDate === date)
if (!original?.id || original.productionDay !== true) throw new Error(`Missing original production day ${factory} ${date}`)

let batchId = null
let deleted = false
let failure = null
const result = { factory, date, originalId: original.id, checks: {}, cleanup: {} }
try {
  const removed = await request(`/mrp/production-calendars/${original.id}`, 'DELETE')
  if (removed.status !== 200) throw new Error(`Calendar delete failed: ${removed.status}`)
  deleted = true
  const latest = await request('/mrp/batches/97/calendar-check?latest=true')
  result.checks.existingBatchLatest = latest.body.data
  if (latest.status !== 200 || !latest.body.data.missingDays.some(day => day.factoryCode === factory && day.date === date))
    throw new Error('Removed date is not required by the selected forecast')

  const created = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=63', 'POST')
  result.checks.create = { status: created.status, error: created.body.error }
  if (created.status !== 200) throw new Error(`Batch creation failed: ${created.status}`)
  batchId = created.body.data.id
  result.batchId = batchId
  const check = await request(`/mrp/batches/${batchId}/calendar-check`)
  result.checks.frozenCalendar = check.body.data
  if (check.status !== 200 || !check.body.data.missingDays.some(day => day.factoryCode === factory && day.date === date))
    throw new Error('Frozen batch did not detect the missing date')
  const precheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
  result.checks.precheck = { status: precheck.status, error: precheck.body.error }
  if (precheck.status !== 400 || !JSON.stringify(precheck.body).includes('生产日历'))
    throw new Error(`Precheck did not block the missing calendar: ${precheck.status}`)
  const task = await request(`/mrp/batches/${batchId}/calculation-task`)
  result.checks.calculationTask = task.body.data ?? null
  if (task.status !== 200 || task.body.data != null) throw new Error('A calculation task was created after failed precheck')
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
  if (deleted) {
    try {
      const restored = await request('/mrp/production-calendars', 'POST', {
        factoryCode: original.factoryCode, calendarDate: original.calendarDate,
        productionDay: original.productionDay, dailyCapacity: original.dailyCapacity,
        capacityUnit: original.capacityUnit, reason: original.reason
      })
      result.cleanup.calendar = { status: restored.status, id: restored.body.data?.id }
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
const directory = join('test-kit', '2026-10-15', 'evidence', 'runs',
  date === '2026-11-03' ? '2026-11-03-calendar-missing-gate' : `2026-11-03-calendar-missing-gate-${date}`)
mkdirSync(directory, { recursive: true })
writeFileSync(join(directory, 'result.json'), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ status: result.status, batchId, precheck: result.checks.precheck, task: result.checks.calculationTask, cleanup: result.cleanup }))
if (failure) throw failure
