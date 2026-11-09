import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const api = 'http://127.0.0.1:8182/api/v1'
const generator = resolve('../test-kit/2026-10-15/tools/generate_bom_chain_fixture.py')

test('双岗从导入和整机确认交接到原材料缺料展开', async ({ browser, request, page }, testInfo) => {
  test.setTimeout(300_000)
  const fixture = JSON.parse(execFileSync('python3', [generator], { encoding: 'utf8' })) as {
    root: string; forecast: string; inventory: string; forecastSha256: string; inventorySha256: string;
    expectedRawMaterials: string[]
  }
  const evidenceDir = dirname(fixture.forecast)
  const failures: string[] = []
  const adminLogin = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(adminLogin.status()).toBe(200)
  const adminToken = (await adminLogin.json()).data.token as string
  const adminHeaders = { Authorization: `Bearer ${adminToken}` }
  const currentForecastBefore = await request.get(`${api}/forecast-versions/current`, { headers: adminHeaders })
  const inventoryBefore = await request.get(`${api}/inventory-snapshots/page?page=1&pageSize=3`, { headers: adminHeaders })
  expect(currentForecastBefore.status()).toBe(200)
  expect(inventoryBefore.status()).toBe(200)
  const forecastBeforeId = ((await currentForecastBefore.json()).data as { id: number }).id
  const inventoryBeforeCount = ((await inventoryBefore.json()).data as { total: number }).total
  const rolesResponse = await request.get(`${api}/roles/active`, { headers: adminHeaders })
  expect(rolesResponse.status()).toBe(200)
  const roles = (await rolesResponse.json()).data as { id: number; code: string }[]
  const accounts: { id: number; username: string; role: string }[] = []
  const contexts = [] as Awaited<ReturnType<typeof browser.newContext>>[]
  const evidence: Record<string, unknown> = { fixture, environment: '8182/5192/3317/6391', forecastBeforeId, inventoryBeforeCount }
  let completed = false

  const loginRole = async (role: string) => {
    const account = accounts.find(item => item.role === role)
    expect(account).toBeDefined()
    const context = await browser.newContext()
    contexts.push(context)
    const rolePage = await context.newPage()
    rolePage.on('pageerror', error => failures.push(`${role}: ${error.message}`))
    await rolePage.goto('/login')
    await rolePage.getByPlaceholder('邮箱').fill(account!.username)
    await rolePage.getByPlaceholder('密码').fill(process.env.E2E_ROLE_PASSWORD || '')
    const loginResponse = rolePage.waitForResponse(response => response.url().endsWith('/auth/login') && response.request().method() === 'POST')
    await rolePage.getByRole('button', { name: '登录', exact: true }).click()
    const response = await loginResponse
    expect(response.status()).toBe(200)
    return { page: rolePage, token: (await response.json()).data.token as string }
  }

  const importFile = async (route: string, label: string, path: string, endpoint: string) => {
    await page.goto(route)
    await page.getByRole('button', { name: '导入', exact: true }).click()
    const dialog = page.getByRole('dialog').filter({ hasText: label })
    const previewResponse = page.waitForResponse(response => response.url().endsWith(endpoint) && response.request().method() === 'POST')
    await dialog.locator('input[type=file]').setInputFiles({
      name: path.split('/').at(-1) || 'import.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: readFileSync(path)
    })
    const preview = await previewResponse
    expect(preview.status()).toBe(202)
    const staging = (await preview.json()).data as { taskId: number; totalRows: number; errorRows: number; successRows: number; recognizedMonths?: string[] }
    expect(staging.errorRows).toBe(0)
    expect(staging.successRows).toBeGreaterThan(0)
    if (route === '/forecast') {
      expect(staging.totalRows).toBe(2)
      expect(staging.recognizedMonths).toHaveLength(6)
      const stillCurrent = await request.get(`${api}/forecast-versions/current`, { headers: adminHeaders })
      expect(((await stillCurrent.json()).data as { id: number }).id).toBe(forecastBeforeId)
    } else {
      expect(staging.totalRows).toBe(164)
      const stillListed = await request.get(`${api}/inventory-snapshots/page?page=1&pageSize=3`, { headers: adminHeaders })
      expect(((await stillListed.json()).data as { total: number }).total).toBe(inventoryBeforeCount)
    }
    const confirmResponse = page.waitForResponse(response => response.url().includes(`${endpoint}/${staging.taskId}/confirm`) && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: '开始导入' }).click()
    const confirmed = await confirmResponse
    expect(confirmed.status()).toBe(200)
    await expect(dialog).not.toBeVisible()
    return (await confirmed.json()).data as Record<string, number>
  }

  try {
    for (const role of ['PLANNER', 'MATERIAL']) {
      const roleId = roles.find(item => item.code === role)?.id
      expect(roleId, `${role} exists`).toBeDefined()
      const username = `e2e-bom-${role.toLowerCase()}-${Date.now()}`
      const created = await request.post(`${api}/users`, {
        headers: adminHeaders, data: { username, displayName: username, roleIds: [roleId] }
      })
      expect(created.status()).toBe(200)
      accounts.push({ id: (await created.json()).data.id, username, role })
    }

    page.on('pageerror', error => failures.push(`ADMIN: ${error.message}`))
    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill('admin')
    await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/planning$/)

    const forecast = await importFile('/forecast', '经营计划批量导入', fixture.forecast, '/forecast-imports')
    const inventory = await importFile('/inventory', '库存批量导入', fixture.inventory, '/inventory-imports/items')
    evidence.forecastVersionId = forecast.id
    evidence.inventorySnapshotId = inventory.snapshotId
    const currentForecastAfter = await request.get(`${api}/forecast-versions/current`, { headers: adminHeaders })
    const inventoryAfter = await request.get(`${api}/inventory-snapshots/page?page=1&pageSize=3`, { headers: adminHeaders })
    expect(((await currentForecastAfter.json()).data as { id: number }).id).toBe(forecast.id)
    expect(((await inventoryAfter.json()).data as { total: number }).total).toBe(inventoryBeforeCount + 1)
    const forecastDetails = await request.get(`${api}/forecast-versions/${forecast.id}`, { headers: adminHeaders })
    const inventoryDetails = await request.get(`${api}/inventory-snapshots/${inventory.snapshotId}?pageSize=100`, { headers: adminHeaders })
    expect(forecastDetails.status()).toBe(200)
    expect(inventoryDetails.status()).toBe(200)
    const forecastData = (await forecastDetails.json()).data as { recognizedMonths: string[]; total: number; items: { factoryCode: string; months: Record<string, number> }[] }
    const inventoryData = (await inventoryDetails.json()).data as { total: number; items: { warehouseCode: string; inventoryQuality: string }[] }
    const inventorySecondPage = await request.get(`${api}/inventory-snapshots/${inventory.snapshotId}?page=2&pageSize=100`, { headers: adminHeaders })
    expect(inventorySecondPage.status()).toBe(200)
    const inventoryRows = [...inventoryData.items, ...((await inventorySecondPage.json()).data as { items: { warehouseCode: string; inventoryQuality: string }[] }).items]
    expect(forecastData.recognizedMonths).toHaveLength(6)
    expect(forecastData.total).toBe(2)
    expect(forecastData.items.map(item => item.factoryCode).sort()).toEqual(['永惠', '爱培科'])
    expect(forecastData.items.every(item => Object.keys(item.months).length === 6)).toBe(true)
    expect(inventoryData.total).toBe(164)
    for (const warehouse of ['UAT_APK', 'UAT_YH'])
      expect(inventoryRows.filter(item => item.warehouseCode === warehouse).length).toBe(82)
    expect(inventoryRows).toHaveLength(164)
    expect(inventoryRows.every(item => item.inventoryQuality === 'GOOD')).toBe(true)
    evidence.importChecks = { forecastBeforeUnchangedDuringPreview: true, inventoryCountBeforeUnchangedDuringPreview: true,
      forecastCurrentAfter: forecast.id, inventoryCountAfter: inventoryBeforeCount + 1,
      forecastMaterials: forecastData.total, forecastMonths: forecastData.recognizedMonths, inventoryRows: inventoryData.total,
      inventoryGoodRows: inventoryRows.length }

    const planner = await loginRole('PLANNER')
    const material = await loginRole('MATERIAL')
    await expect(planner.page).toHaveURL(/\/planning$/)
    await expect(material.page).toHaveURL(/\/shortage$/)
    const plannerHeaders = { Authorization: `Bearer ${planner.token}` }
    const materialHeaders = { Authorization: `Bearer ${material.token}` }
    const create = await request.post(`${api}/mrp/batches?factoryCode=ALL&forecastVersionId=${forecast.id}&inventorySnapshotId=${inventory.snapshotId}`, {
      headers: plannerHeaders
    })
    expect(create.status()).toBe(200)
    const createdBatch = (await create.json()).data as { id: number; status: string; factoryCode: string; forecastVersionId: number; inventorySnapshotId: number; bomSourceChecksum: string; categoryRoleVersionId: number; materialPolicyVersionId: number; productionCalendarVersionId: number; factoryWarehouseMappingVersionId: number; stockPolicyVersionId: number; businessWeekVersionId: number; inputChecksum: string }
    const batchId = createdBatch.id
    expect(createdBatch.status).toBe('DRAFT')
    expect(createdBatch.factoryCode).toBe('ALL')
    expect(createdBatch.forecastVersionId).toBe(forecast.id)
    expect(createdBatch.inventorySnapshotId).toBe(inventory.snapshotId)
    for (const key of ['bomSourceChecksum', 'categoryRoleVersionId', 'materialPolicyVersionId',
      'productionCalendarVersionId', 'factoryWarehouseMappingVersionId', 'stockPolicyVersionId',
      'businessWeekVersionId', 'inputChecksum'] as const) expect(createdBatch[key], key).toBeTruthy()
    const batchFactoriesResponse = await request.get(`${api}/mrp/batches/${batchId}/factories`, { headers: plannerHeaders })
    expect(batchFactoriesResponse.status()).toBe(200)
    expect(((await batchFactoriesResponse.json()).data as string[]).sort()).toEqual(['永惠', '爱培科'])
    evidence.frozenBatch = createdBatch
    evidence.batchId = batchId
    await material.page.reload()
    await expect(material.page.getByText('暂不能计算缺料：整机日排产尚未生成')).toBeVisible()
    writeFileSync(resolve(evidenceDir, 'shortage-before-schedule.png'), await material.page.screenshot())

    const precheck = await request.post(`${api}/mrp/batches/${batchId}/precheck`, { headers: plannerHeaders })
    expect(precheck.status()).toBe(200)
    expect((await precheck.json()).data.status).toBe('READY_TO_CALCULATE')
    const calculate = await request.post(`${api}/mrp/batches/${batchId}/calculate`, { headers: plannerHeaders })
    expect(calculate.status()).toBe(200)
    evidence.scheduleTaskId = (await calculate.json()).data.id
    await expect.poll(async () => {
      const state = await request.get(`${api}/mrp/batches/${batchId}/calculation-task`, { headers: plannerHeaders })
      return (await state.json()).data?.status
    }, { timeout: 120_000, intervals: [1000, 2000, 3000] }).toBe('SUCCEEDED')
    const scheduleReady = await request.get(`${api}/mrp/batches/${batchId}`, { headers: plannerHeaders })
    expect(((await scheduleReady.json()).data as { status: string }).status).toBe('SCHEDULE_READY')
    await material.page.reload()
    await expect(material.page.getByText('暂不能计算缺料：等待排产员确认整机日计划')).toBeVisible()
    writeFileSync(resolve(evidenceDir, 'shortage-awaiting-confirmation.png'), await material.page.screenshot())
    await planner.page.reload()
    await planner.page.getByRole('button', { name: '确认整机排产' }).click()
    await expect(planner.page.getByText('整机已确认', { exact: true }).first()).toBeVisible()
    await material.page.reload()
    await expect(material.page.getByText('整机排产已确认，可以开始物料缺料分析')).toBeVisible()
    writeFileSync(resolve(evidenceDir, 'shortage-ready-to-analyze.png'), await material.page.screenshot())
    const analysisResponse = material.page.waitForResponse(response => response.url().endsWith(`/mrp/batches/${batchId}/material-analysis`) && response.request().method() === 'POST')
    await material.page.getByRole('button', { name: '开始物料分析' }).click()
    const submitted = await analysisResponse
    expect(submitted.status()).toBe(202)
    evidence.materialTaskId = (await submitted.json()).data.id
    await expect.poll(async () => {
      const state = await request.get(`${api}/mrp/batches/${batchId}/calculation-task`, { headers: materialHeaders })
      return (await state.json()).data?.status
    }, { timeout: 120_000, intervals: [1000, 2000, 3000] }).toBe('SUCCEEDED')

    const shortageResponse = await request.get(`${api}/mrp/batches/${batchId}/shortage/page?pageSize=100`, { headers: materialHeaders })
    expect(shortageResponse.status()).toBe(200)
    const shortage = (await shortageResponse.json()).data as { items: { factoryCode: string; terminalMaterialCode: string; terminalType: string; quantity: string }[] }
    const rawRows = shortage.items.filter(item => item.terminalType === 'RAW_MATERIAL')
    for (const factory of ['爱培科', '永惠']) {
      for (const code of fixture.expectedRawMaterials) {
        expect(rawRows.filter(item => item.factoryCode === factory && item.terminalMaterialCode === code).map(item => Number(item.quantity))).toEqual([10])
      }
    }
    evidence.rawRows = rawRows.map(item => ({ factory: item.factoryCode, material: item.terminalMaterialCode, quantity: item.quantity }))
    evidence.crossRoleMachineGridHttp = (await request.get(`${api}/mrp/batches/${batchId}/rolling-grid?view=MACHINE_OUTPUT`, { headers: materialHeaders })).status()
    evidence.crossRoleShortageHttp = (await request.get(`${api}/mrp/batches/${batchId}/shortage/page`, { headers: plannerHeaders })).status()
    expect(evidence.crossRoleMachineGridHttp).toBe(403)
    expect(evidence.crossRoleShortageHttp).toBe(403)
    expect(failures).toEqual([])
    await material.page.reload()
    await expect(material.page.getByText('11520471').first()).toBeVisible()
    writeFileSync(resolve(evidenceDir, 'shortage-raw-materials.png'), await material.page.screenshot())
    await testInfo.attach('material-workbench', { body: await material.page.screenshot(), contentType: 'image/png' })
    completed = true
  } finally {
    for (const context of contexts) await context.close()
    for (const account of accounts) {
      const removed = await request.delete(`${api}/users/${account.id}`, { headers: adminHeaders })
      expect(removed.status()).toBe(200)
    }
    evidence.status = completed ? 'PASS' : 'FAIL'
    evidence.pageErrors = failures
    writeFileSync(resolve(evidenceDir, 'result.json'), JSON.stringify(evidence, null, 2) + '\n')
    await testInfo.attach('bom-chain-result', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' })
  }
})
