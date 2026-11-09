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
const monthPath = `/mrp/production-calendars/month?factoryCode=${encodeURIComponent(factory)}&month=2026-11`
const originalWeek = (await request(monthPath)).body.data.filter(row => row.calendarDate >= '2026-11-01' && row.calendarDate <= '2026-11-07')
if (originalWeek.length !== 7 || originalWeek.filter(row => row.productionDay).length !== 5)
  throw new Error('The reviewed W1 fixture has changed')
const changed = []
let batchId = null
let failure = null
const result = { factory, week: '2026-11-01..2026-11-07', checks: {}, cleanup: {} }
try {
  for (const original of originalWeek.filter(row => row.productionDay)) {
    const updated = await request(`/mrp/production-calendars/${original.id}`, 'PUT', {
      ...original, productionDay: false, dailyCapacity: '0', reason: 'UAT 整周停产'
    })
    if (updated.status !== 200) throw new Error(`Stopping ${original.calendarDate} failed: ${updated.status}`)
    changed.push({ original, revision: updated.body.data.revision })
  }
  const created = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=47', 'POST')
  result.checks.create = { status: created.status, error: created.body.error }
  if (created.status !== 200) throw new Error(`Batch creation failed: ${created.status}`)
  batchId = created.body.data.id
  result.batchId = batchId
  const calendar = await request(`/mrp/batches/${batchId}/calendar-check`)
  result.checks.calendar = calendar.body.data
  if (calendar.status !== 200 || calendar.body.data.total !== 0) throw new Error('Explicit stopped week was treated as missing')
  const precheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
  result.checks.precheck = { status: precheck.status, state: precheck.body.data?.status, error: precheck.body.error }
  if (precheck.status !== 200 || precheck.body.data?.status !== 'READY_TO_CALCULATE')
    throw new Error(`Precheck failed: ${precheck.status}`)
  const submitted = await request(`/mrp/batches/${batchId}/calculate`, 'POST')
  result.checks.submitted = { status: submitted.status, taskId: submitted.body.data?.id, error: submitted.body.error }
  if (submitted.status !== 200 || !submitted.body.data?.id) throw new Error('Calculation was not submitted')
  let task
  for (let attempt = 0; attempt < 90; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 1000))
    const polled = await request(`/mrp/batches/${batchId}/calculation-task`)
    task = polled.body.data
    if (task && ['SUCCEEDED', 'FAILED'].includes(task.status)) break
  }
  result.checks.task = task
  if (task?.status !== 'SUCCEEDED') throw new Error(`Calculation task did not succeed: ${task?.status}`)
  const unallocated = await request(`/mrp/batches/${batchId}/unallocated`)
  result.checks.unallocated = unallocated.body.data
  const stoppedWeek = unallocated.body.data.filter(row => row.demandKey?.startsWith(`${factory}|`) && row.reasonCode === 'NO_PRODUCTION_DAY')
  if (unallocated.status !== 200 || !stoppedWeek.length
      || stoppedWeek.every(row => Number(row.quantity) <= 0)) throw new Error('Stopped-week quantity was silently dropped')
  const conservation = await request(`/mrp/batches/${batchId}/plans/conservation`)
  result.checks.conservation = conservation.body.data
  if (conservation.status !== 200 || conservation.body.data.mismatchCount !== 0)
    throw new Error('Stopped-week quantity is not conserved')
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
  for (const { original, revision } of changed.toReversed()) {
    try {
      const restored = await request(`/mrp/production-calendars/${original.id}`, 'PUT', { ...original, revision })
      if (restored.status !== 200) throw new Error(`Restoring ${original.calendarDate} failed: ${restored.status}`)
    } catch (error) { failure ??= error }
  }
  const after = (await request(monthPath)).body.data
  result.cleanup.calendarVerified = originalWeek.every(original => {
    const row = after.find(item => item.calendarDate === original.calendarDate)
    return row && row.productionDay === original.productionDay && row.dailyCapacity === original.dailyCapacity
      && row.capacityUnit === original.capacityUnit && row.reason === original.reason
  })
  if (!result.cleanup.calendarVerified) failure ??= new Error('Calendar values differ after restoration')
}

result.status = failure ? 'FAIL' : 'PASS'
if (failure) result.error = String(failure)
const directory = join('test-kit', '2026-10-15', 'evidence', 'runs', '2026-11-03-calendar-stopped-week')
mkdirSync(directory, { recursive: true })
writeFileSync(join(directory, 'result.json'), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ status: result.status, batchId, task: result.checks.task?.status,
  unallocated: result.checks.unallocated?.length, conservation: result.checks.conservation?.mismatchCount,
  cleanup: result.cleanup }))
if (failure) throw failure
