import { expect, test } from '@playwright/test'
import { readFileSync, copyFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'

const base = 'http://127.0.0.1:5192'
const evidence = resolve('../test-kit/2026-10-15/evidence/runs/2026-11-02-import-boundary-160231')
const formulaEvidence = resolve('../test-kit/2026-10-15/evidence/runs/2026-11-02-import-matrix-170517')
const mixedEvidence = resolve('../test-kit/2026-10-15/evidence/runs/2026-11-02-import-negative-154756')
const inventoryBoundaryEvidence = resolve('../test-kit/2026-10-15/evidence/runs/2026-11-02-import-matrix-100931')
const quantityEvidence = resolve('../test-kit/2026-10-15/evidence/runs/2026-11-03-quantity-semantics-123214')

for (const sample of [
  { route: '/forecast', file: 'forecast_empty_data-160231.xlsx', label: '经营计划批量导入', directory: evidence },
  { route: '/inventory', file: 'inventory_unknown_material-160231.xlsx', label: '库存批量导入', directory: evidence },
  { route: '/forecast', file: 'F08_formula_qty-170517.xlsx', label: '经营计划批量导入', directory: formulaEvidence },
  { route: '/inventory', file: 'I07_formula_qty-170517.xlsx', label: '库存批量导入', directory: formulaEvidence },
  { route: '/forecast', file: 'forecast_mixed_valid_negative.xlsx', label: '经营计划批量导入', directory: mixedEvidence },
  { route: '/inventory', file: 'inventory_mixed_valid_negative.xlsx', label: '库存批量导入', directory: mixedEvidence },
  { route: '/inventory', file: 'inventory_negative_qty.xlsx', label: '库存批量导入', directory: mixedEvidence },
  { route: '/inventory', file: 'I02_blank_qty-100931.xlsx', label: '库存批量导入', directory: inventoryBoundaryEvidence },
  { route: '/inventory', file: 'I02_nonnumeric_qty-100931.xlsx', label: '库存批量导入', directory: inventoryBoundaryEvidence },
  { route: '/forecast', file: 'F07_six_decimal.xlsx', label: '经营计划批量导入', directory: quantityEvidence },
]) {
  test(`${sample.label} ${sample.file}：错误预览禁确认并可下载报告`, async ({ page, request }) => {
    const auth = await request.post(base + '/api/v1/auth/login', {
      data: { username: 'admin', password: 'admin123' },
    })
    expect(auth.status()).toBe(200)
    const user = (await auth.json()).data as {
      token: string; userId: number; username: string; displayName: string;
      roles: string[]; permissions: string[]
    }
    const failures: string[] = []
    page.on('pageerror', error => failures.push(error.message))
    page.on('response', response => {
      if (response.url().includes('/api/v1/') && response.status() >= 500) failures.push(response.url())
    })
    await page.goto(base + '/login')
    await page.evaluate(data => {
      localStorage.setItem('mrp_token', data.token)
      localStorage.setItem('mrp_user_info', JSON.stringify({
        id: data.userId, username: data.username, displayName: data.displayName,
        roles: data.roles, permissions: data.permissions,
      }))
    }, user)
    await page.goto(base + sample.route)
    await page.getByRole('button', { name: '导入', exact: true }).click()
    const dialog = page.getByRole('dialog').filter({ hasText: sample.label })
    await dialog.locator('input[type=file]').setInputFiles({
      name: sample.file,
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: readFileSync(resolve(sample.directory, sample.file)),
    })
    await expect(dialog.getByText('错误行')).toBeVisible()
    await expect(dialog.getByRole('button', { name: '开始导入' })).toBeDisabled()
    await expect(dialog.getByRole('button', { name: '下载错误数据' })).toBeVisible()
    const download = page.waitForEvent('download')
    await dialog.getByRole('button', { name: '下载错误数据' }).click()
    const report = await download
    expect(report.suggestedFilename()).toMatch(/\.xlsx$/)
    const reportPath = await report.path()
    expect(reportPath).toBeTruthy()
    const workbookPath = `${reportPath}.xlsx`
    copyFileSync(reportPath!, workbookPath)
    const lastHeader = execFileSync('python3', ['-c',
      'import openpyxl,sys; w=openpyxl.load_workbook(sys.argv[1],read_only=True); print(w.active.cell(1,w.active.max_column).value)',
      workbookPath], { encoding: 'utf8' }).trim()
    if (sample.file.startsWith('forecast_empty_data')) expect(lastHeader).toMatch(/^错误原因：file:/)
    else expect(lastHeader).toBe('错误原因')
    expect(failures).toEqual([])
  })
}
