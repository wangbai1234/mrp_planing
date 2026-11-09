import { execFileSync, spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { expect, test } from '@playwright/test'

const api = 'http://127.0.0.1:8182/api/v1'

function sql(statement: string) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
  { input: statement, encoding: 'utf8' }).trim()
}

async function holdDailyResultWrites(): Promise<ChildProcessWithoutNullStreams> {
  const process = spawn('docker', ['exec', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --unbuffered --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit -e "$1"',
    'mrp-progress-lock', "LOCK TABLES mrp_daily_plan_detail WRITE; SELECT 'LOCKED'; SELECT SLEEP(20); UNLOCK TABLES;"])
  await new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Timed out acquiring isolated daily-result table lock')), 10_000)
    process.stdout.on('data', chunk => {
      if (String(chunk).includes('LOCKED')) { clearTimeout(timeout); resolve() }
    })
    process.on('error', error => { clearTimeout(timeout); reject(error) })
    process.on('exit', code => { clearTimeout(timeout); reject(new Error('Daily-result table lock exited: ' + code)) })
  })
  return process
}

async function waitForLockRelease(process: ChildProcessWithoutNullStreams) {
  if (process.exitCode !== null) {
    expect(process.exitCode).toBe(0)
    return
  }
  const code = await new Promise<number | null>(resolve => process.once('exit', resolve))
  expect(code).toBe(0)
}

test('B05: real running worker progress survives a planning page refresh', async ({ page, request }, testInfo) => {
  test.setTimeout(240_000)
  const login = await request.post(api + '/auth/login', { data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD } })
  expect(login.status()).toBe(200)
  const headers = { Authorization: 'Bearer ' + (await login.json()).data.token }
  const created = await request.post(api + '/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', { headers })
  expect(created.status()).toBe(200)
  const batchId = (await created.json()).data.id as number
  let dailyResultLock: ChildProcessWithoutNullStreams | null = null
  try {
    const precheck = await request.post(api + '/mrp/batches/' + batchId + '/precheck', { headers })
    expect((await precheck.json()).data.status).toBe('READY_TO_CALCULATE')
    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill('admin')
    await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/planning$/, { timeout: 15_000 })
    await page.goto('/planning/' + batchId)
    await expect(page.getByRole('button', { name: '开始 MRP 计算' })).toBeVisible({ timeout: 15_000 })
    const summary = await request.get(api + '/mrp/batches/' + batchId + '/summary', { headers })
    expect(summary.status()).toBe(200)
    const summaryBody = await summary.body()
    await page.route('**/mrp/batches/' + batchId + '/summary', route => route.fulfill({ status: 200, contentType: 'application/json', body: summaryBody }))
    dailyResultLock = await holdDailyResultWrites()
    const submitted = page.waitForResponse(response => response.url().endsWith('/mrp/batches/' + batchId + '/calculate') && response.request().method() === 'POST', { timeout: 15_000 })
    await page.getByRole('button', { name: '开始 MRP 计算' }).click({ timeout: 15_000 })
    const taskId = (await (await submitted).json()).data.id as number
    const progress = page.getByTestId('calculation-progress')
    await expect(progress).toContainText('任务 #' + taskId)
    const running = page.waitForResponse(async response => {
      if (!response.url().endsWith('/mrp/batches/' + batchId + '/calculation-task') || response.status() !== 200) return false
      const task = (await response.json()).data
      return task?.taskId === taskId && task.status === 'RUNNING' && task.progressCurrent >= 76 && task.phase?.startsWith('保存日排产')
    }, { timeout: 60_000 })
    const task = (await (await running).json()).data
    await expect(progress).toContainText(task.phase)
    await expect(progress.locator('.progress-percent')).toContainText(/^[1-9]\d?%$/)
    const live = await request.get(api + '/mrp/batches/' + batchId + '/calculation-task', { headers })
    expect((await live.json()).data).toMatchObject({ taskId, status: 'RUNNING', phase: task.phase })
    await page.screenshot({ path: testInfo.outputPath('b05-running-task.png'), fullPage: true })
    await page.reload()
    await expect(page.getByText('排产版本 #' + batchId, { exact: false })).toBeVisible()
    const afterRefresh = await request.get(api + '/mrp/batches/' + batchId + '/calculation-task', { headers })
    expect((await afterRefresh.json()).data).toMatchObject({ taskId, status: 'RUNNING', phase: task.phase })
    await expect(page.getByTestId('calculation-progress')).toContainText('任务 #' + taskId)
    await waitForLockRelease(dailyResultLock)
    dailyResultLock = null
    for (let attempt = 0; attempt < 90; attempt++) {
      const response = await request.get(api + '/mrp/batches/' + batchId + '/calculation-task', { headers })
      if ((await response.json()).data?.status === 'SUCCEEDED') break
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    const finished = await request.get(api + '/mrp/batches/' + batchId, { headers })
    expect((await finished.json()).data.status).toBe('SCHEDULE_READY')
    expect(sql('SELECT COUNT(*) FROM mrp_calc_run WHERE batch_id=' + batchId + ';')).toBe('1')
  } finally {
    if (dailyResultLock) await waitForLockRelease(dailyResultLock)
    for (let attempt = 0; attempt < 90; attempt++) {
      const current = await request.get(api + '/mrp/batches/' + batchId, { headers })
      if (!['CALCULATING', 'WRITING', 'WAITING_DAILY_DISTRIBUTION'].includes((await current.json()).data?.status)) break
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    const cancelled = await request.post(api + '/mrp/batches/' + batchId + '/cancel', { headers })
    expect(cancelled.status()).toBe(200)
  }
})

test('B05: failed real worker task displays its reason and can be retried', async ({ page, request }, testInfo) => {
  test.setTimeout(180_000)
  const login = await request.post(api + '/auth/login', { data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD } })
  expect(login.status()).toBe(200)
  const headers = { Authorization: 'Bearer ' + (await login.json()).data.token }
  const created = await request.post(api + '/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', { headers })
  expect(created.status()).toBe(200)
  const batchId = (await created.json()).data.id as number
  try {
    const precheck = await request.post(api + '/mrp/batches/' + batchId + '/precheck', { headers })
    expect((await precheck.json()).data.status).toBe('READY_TO_CALCULATE')
    const started = await request.post(api + '/mrp/batches/' + batchId + '/calculate', { headers })
    expect(started.status()).toBe(200)
    const taskId = (await started.json()).data.id as number
    // Corrupt only this isolated task's checksum after submission to force a real Worker failure.
    expect(sql("UPDATE calc_task SET input_checksum=REPEAT('f',64) WHERE id=" + taskId + " AND status='PENDING'; SELECT ROW_COUNT();")).toBe('1')
    let failed: { status: string; errorMessage?: string; progressCurrent?: number } | null = null
    for (let attempt = 0; attempt < 90; attempt++) {
      const response = await request.get(api + '/mrp/batches/' + batchId + '/calculation-task', { headers })
      const task = (await response.json()).data
      if (task?.status === 'FAILED') { failed = task; break }
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    expect(failed?.status).toBe('FAILED')
    expect(failed?.errorMessage).toContain('Input checksum mismatch')
    const before = await request.get(api + '/mrp/batches/' + batchId, { headers })
    const beforeBatch = (await before.json()).data
    expect(beforeBatch.status).toBe('READY_TO_CALCULATE')
    expect(beforeBatch.activeRunId).toBeNull()
    expect(sql("SELECT COUNT(*) FROM mrp_calc_run WHERE batch_id=" + batchId + ';')).toBe('0')

    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill('admin')
    await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/planning$/)
    await page.goto('/planning/' + batchId)
    await expect(page.getByText('MRP 计算失败（任务 #' + taskId)).toBeVisible()
    await expect(page.getByText(/Input checksum mismatch/)).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('b05-failed-task.png'), fullPage: true })
    const retryResponse = page.waitForResponse(response => response.url().endsWith('/mrp/batches/' + batchId + '/calculate') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '开始 MRP 计算' }).click()
    const retried = await retryResponse
    expect(retried.status()).toBe(200)
    const retryTaskId = (await retried.json()).data.id as number
    expect(retryTaskId).not.toBe(taskId)
    let succeeded = false
    for (let attempt = 0; attempt < 90; attempt++) {
      const response = await request.get(api + '/mrp/batches/' + batchId + '/calculation-task', { headers })
      const task = (await response.json()).data
      if (task?.taskId === taskId) { await new Promise(resolve => setTimeout(resolve, 1000)); continue }
      expect(task?.taskId).toBe(retryTaskId)
      if (task?.status === 'SUCCEEDED') { succeeded = true; break }
      expect(task?.status).not.toBe('FAILED')
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    expect(succeeded).toBe(true)
    const after = await request.get(api + '/mrp/batches/' + batchId, { headers })
    expect((await after.json()).data.status).toBe('SCHEDULE_READY')
    expect(sql("SELECT COUNT(*) FROM mrp_calc_run WHERE batch_id=" + batchId + ';')).toBe('1')
    await page.reload()
    await expect(page.getByRole('button', { name: '确认整机排产' })).toBeVisible()
  } finally {
    for (let attempt = 0; attempt < 90; attempt++) {
      const current = await request.get(api + '/mrp/batches/' + batchId, { headers })
      const status = (await current.json()).data?.status
      if (!['CALCULATING', 'WRITING', 'WAITING_DAILY_DISTRIBUTION'].includes(status)) break
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    const cancelled = await request.post(api + '/mrp/batches/' + batchId + '/cancel', { headers })
    expect(cancelled.status()).toBe(200)
    expect((await cancelled.json()).data.status).toBe('CANCELLED')
  }
})
