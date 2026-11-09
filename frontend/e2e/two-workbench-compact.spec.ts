import { test, expect } from '@playwright/test'

test('排产与物料工作台通过状态交接且没有互跳入口', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'))
  await page.goto('/planning')
  await expect(page.getByRole('heading', { name: '排产计划' })).toBeVisible()
  const version = page.locator('.version-line')
  if (await version.count()) {
    await expect(page.getByRole('button', { name: '版本信息' })).toBeVisible()
    await expect(page.getByRole('button', { name: '物料缺料' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '生产日历检查' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '执行与锁定' })).toHaveCount(0)
    const scheduleConfirmed = Boolean(await page.getByText('整机已确认', { exact: true }).count())
    if (scheduleConfirmed) {
      await expect(page.getByRole('button', { name: '确认整机排产' })).toHaveCount(0)
    }
    await page.getByRole('button', { name: '版本信息' }).click()
    await expect(page.getByRole('dialog', { name: '版本信息' })).not.toContainText('执行与锁定')
    await page.goto('/shortage')
    await expect(page.getByRole('heading', { name: '物料缺料' })).toBeVisible()
    await expect(page.locator('.app-breadcrumb')).toContainText('物料缺料')
    await expect(page.getByRole('button', { name: '返回整机排产' })).toHaveCount(0)
    if (scheduleConfirmed) await expect(page.getByText('整机排产已确认，可以开始物料缺料分析')).toBeVisible()
    await expect(page.getByRole('tab', { name: /待处理异常/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /待分配需求/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /库存风险/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /物料反馈/ })).toBeVisible()
    if (await page.getByRole('button', { name: '导出物料汇总' }).count()) {
      await page.getByRole('tab', { name: '日备料' }).click()
      await expect(page.getByRole('button', { name: '导出日备料' })).toBeVisible()
      await page.getByRole('tab', { name: '末级物料' }).click()
    }
    await expect(page.getByRole('button', { name: '数据口径' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '刷新状态' })).toHaveCount(0)
    await expect(page.getByPlaceholder('待处理与追溯')).toHaveCount(0)
    await expect(page.getByText('同一版本 · 整机确认', { exact: false })).toHaveCount(0)
    await expect(page.getByText('materials', { exact: true })).toHaveCount(0)
    if (await page.getByText('历史版本按物料汇总展示原末级结果', { exact: false }).count()) {
      await expect(page.getByRole('columnheader', { name: '整批库存风险' })).toBeVisible()
      await expect(page.getByRole('cell', { name: '历史未评估' }).first()).toBeVisible()
      await expect(page.getByRole('cell', { name: '未评估', exact: true }).first()).toBeVisible()
    }
    const sourceButton = page.getByRole('button', { name: '受影响整机' }).first()
    if (await sourceButton.count()) {
      await sourceButton.click()
      const dialog = page.getByRole('dialog', { name: '受影响整机' })
      await expect(dialog.getByRole('button', { name: '查看排产' })).toHaveCount(0)
    }
  }
})

test('整机负荷日格文字保持在单元格内', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'))
  await page.goto('/planning')
  await expect(page.getByRole('heading', { name: '排产计划' })).toBeVisible()

  const loadCells = page.locator('.cell-load')
  await expect(loadCells.first()).toBeVisible()
  for (const width of [1440, 1920]) {
    await page.setViewportSize({ width, height: width === 1440 ? 900 : 1080 })
    const overflow = await loadCells.evaluateAll(elements => elements.slice(0, 12).flatMap((button, index) => {
      const bounds = button.getBoundingClientRect()
      return [...button.querySelectorAll('.cell-line, small')].flatMap(node => {
        const text = node.getBoundingClientRect()
        return text.left < bounds.left - 1 || text.right > bounds.right + 1
          || text.top < bounds.top - 1 || text.bottom > bounds.bottom + 1
          || node.scrollWidth > node.clientWidth + 1 ? [`${index}: ${node.textContent}`] : []
      })
    }))
    expect(overflow, `viewport ${width}px`).toEqual([])
  }
})

test('生产执行在独立页面展示已发布版本', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'))
  await page.goto('/execution')
  await expect(page.getByRole('heading', { name: '生产执行' })).toBeVisible()
  await expect(page.locator('.app-breadcrumb')).toContainText('生产执行')
  await expect(page.getByRole('columnheader', { name: '整机料号' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '待执行' }).first()).toBeVisible()
  await expect(page.getByText('该日期暂无整机日排产')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '执行与锁定' })).toHaveCount(0)
  await expect(page.getByText('加载生产执行失败', { exact: false })).toHaveCount(0)
})
