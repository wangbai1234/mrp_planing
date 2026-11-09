import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus, { ElMessageBox } from 'element-plus'
import MrpWorkspace from '../MrpWorkspace.vue'
import CalculationProgressBar from '../components/CalculationProgress.vue'
const api = vi.hoisted(() => ({ getPlanningCalendarCheck: vi.fn(), publish: vi.fn(), reviseBatch: vi.fn(), getPublishCapacity: vi.fn(), getCurrentBatch: vi.fn(), getBatch: vi.fn(), getBatchSummary: vi.fn(), getCalculationProgress: vi.fn(), getLifecycleTask: vi.fn(), getMaterialPolicyNotices: vi.fn(), getPendingDefaultExplosionCount: vi.fn(), getStockRisksPage: vi.fn(), getPendingUnallocatedPage: vi.fn() }))
vi.mock('../../../api/mrp', () => ({ mrpApi: api }))
vi.mock('../../../stores/permission', () => ({ usePermissionStore: () => ({ hasPermission: (code: string) => ['mrp_batch:publish', 'mrp_shortage:view'].includes(code) }) }))
const batch = (id = 95, status = 'CALCULATING') => ({ id, status, factoryCode: 'ALL', factoryWarehouseMappingVersionId: 1, stockPolicyVersionId: 1, businessWeekVersionId: 1 })
const progress = (current = 65, phase = '生成日排产') => ({ taskId: 9, status: 'RUNNING', progressCurrent: current, progressTotal: 100, phase, errorMessage: null })
async function openWorkspace(initialPath = '/planning/95') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/planning/:id?', component: MrpWorkspace }] })
  await router.push(initialPath); await router.isReady()
  const page = mount(MrpWorkspace, { global: { plugins: [router, ElementPlus], stubs: { BatchCreateDialog: true, RollingPlanningGrid: true, teleport: true } } })
  await flushPromises()
  return { page, router, vm: page.vm as unknown as { loadWorkspace: (preserve?: boolean) => Promise<void> } }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getPlanningCalendarCheck.mockResolvedValue({ total: 0, missingDays: [], configurationIssues: [] })
  api.getCurrentBatch.mockResolvedValue(batch()); api.getBatch.mockImplementation(async id => batch(id))
  api.getBatchSummary.mockResolvedValue({ dailyPlanCount: 0 })
  api.getCalculationProgress.mockResolvedValue(progress())
  api.getLifecycleTask.mockResolvedValue(null)
  api.getPendingDefaultExplosionCount.mockResolvedValue(0)
  api.getStockRisksPage.mockResolvedValue({ items: [], total: 0 })
  api.getPendingUnallocatedPage.mockResolvedValue({ items: [], total: 0 })
})
describe('progress in current planning status bar', () => {
  it.each([
    ['READY', 'SUPERSEDED', 'PUBLISHED', 'MRP_PUBLISH'],
    ['PUBLISHED', 'PUBLISHED', 'DRAFT', 'MRP_REVISION']
  ])('follows an observed %s lifecycle task to its current result', async (initial, sourceStatus, resultStatus, taskType) => {
    const running = { taskId: 13, taskType, status: 'RUNNING', progressCurrent: 70, progressTotal: 100, phase: '处理中', errorMessage: null, resultBatchId: null }
    api.getCurrentBatch.mockResolvedValue(batch(95, initial))
    api.getBatch.mockResolvedValue(batch(95, initial))
    api.getLifecycleTask.mockResolvedValue(running)
    const { page, router, vm } = await openWorkspace()

    // The first current-batch read can precede the task's commit; completion must
    // check the current batch again before following the result.
    api.getCurrentBatch.mockResolvedValueOnce(batch(95, sourceStatus)).mockResolvedValue(batch(96, resultStatus))
    api.getBatch.mockImplementation(async id => id === 96 ? batch(96, resultStatus) : batch(95, sourceStatus))
    api.getLifecycleTask.mockImplementation(async id => id === 95 ? { ...running, status: 'SUCCEEDED', resultBatchId: 96 } : null)
    await vm.loadWorkspace(true); await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/planning/96')
    expect(page.text()).not.toContain('返回当前版本')
    page.unmount()
  })

  it('keeps an explicitly selected historical source visible after a successful revision', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(96, 'DRAFT'))
    api.getBatch.mockResolvedValue(batch(95, 'PUBLISHED'))
    api.getLifecycleTask.mockResolvedValue({ taskId: 13, taskType: 'MRP_REVISION', status: 'SUCCEEDED', resultBatchId: 96 })
    const { page, router } = await openWorkspace()
    expect(router.currentRoute.value.fullPath).toBe('/planning/95')
    expect(page.text()).toContain('返回当前版本')
    page.unmount()
  })

  it('does not follow an observed task whose result has already been cancelled', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'PUBLISHED'))
    api.getBatch.mockResolvedValue(batch(95, 'PUBLISHED'))
    const task = { taskId: 13, taskType: 'MRP_REVISION', status: 'RUNNING', resultBatchId: null }
    api.getLifecycleTask.mockResolvedValue(task)
    const { page, router, vm } = await openWorkspace()
    api.getLifecycleTask.mockResolvedValue({ ...task, status: 'SUCCEEDED', resultBatchId: 96 })
    await vm.loadWorkspace(true); await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/planning/95')
    expect(page.text()).not.toContain('返回当前版本')
    page.unmount()
  })

  it('does not reopen a cancelled revision from its completed lifecycle task', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'PUBLISHED'))
    api.getBatch.mockImplementation(async id => id === 96 ? batch(96, 'CANCELLED') : batch(95, 'PUBLISHED'))
    api.getLifecycleTask.mockResolvedValue({ taskId: 13, taskType: 'MRP_REVISION', status: 'SUCCEEDED', progressCurrent: 100, progressTotal: 100, phase: 'COMPLETED', errorMessage: null, resultBatchId: 96 })
    const { page, router } = await openWorkspace('/planning/96')
    const returnButton = () => page.findAll('button').find(button => button.text().includes('返回当前版本'))
    expect(returnButton()?.exists()).toBe(true)

    await returnButton()?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/planning')
    expect(Boolean(returnButton())).toBe(false)
    page.unmount()
  })

  it('keeps material release checks without showing material badges in planning', async () => {
    api.getCurrentBatch.mockResolvedValue({ ...batch(95, 'READY'), activeRunId: 42 })
    api.getBatch.mockResolvedValue({ ...batch(95, 'READY'), activeRunId: 42 })
    api.getBatchSummary.mockResolvedValue({ dailyPlanCount: 1, openIssues: 2, pendingUnallocated: 3 })
    api.getPendingDefaultExplosionCount.mockResolvedValueOnce(4).mockResolvedValue(0)
    api.getStockRisksPage.mockResolvedValue({ items: [], total: 5 })
    const { page, vm } = await openWorkspace()
    expect(page.find('.page-actions .el-badge').exists()).toBe(false)
    const badges = vm as unknown as { pendingDefaultCount: number; stockRiskCount: number }
    expect([badges.pendingDefaultCount, badges.stockRiskCount]).toEqual([4, 5])
    await vm.loadWorkspace(true); await flushPromises()
    expect(page.find('.page-actions .el-badge').exists()).toBe(false)
    expect([badges.pendingDefaultCount, badges.stockRiskCount]).toEqual([0, 5])
    page.unmount()
  })
  it('shows a bar on initial load even without task progress', async () => {
    api.getCalculationProgress.mockResolvedValue(null)
    const { page } = await openWorkspace()
    const bar = page.find('.status-bar [data-testid="calculation-progress"]')
    expect(bar.exists()).toBe(true); expect(bar.text()).toContain('正在计算物料需求')
    expect(bar.text()).toContain('读取进度中'); expect(bar.text()).not.toContain('重试中'); expect(bar.text()).not.toContain('0%')
    page.unmount()
  })
  it('shows the latest calculation failure after the batch returns to ready to calculate', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'READY_TO_CALCULATE'))
    api.getBatch.mockResolvedValue(batch(95, 'READY_TO_CALCULATE'))
    api.getCalculationProgress.mockResolvedValue({ ...progress(), status: 'FAILED', errorMessage: 'BOM snapshot not found: null' })
    const { page } = await openWorkspace()
    expect(page.find('.page-alert').text()).toContain('BOM snapshot not found: null')
    expect(page.find('.status-bar').text()).toContain('上次计算失败')
    page.unmount()
  })
  it('retains the last real progress during refresh and failed progress requests', async () => {
    const { page, vm } = await openWorkspace()
    expect(page.find('.status-bar').text()).toContain('65%')
    let resolve!: (value: unknown) => void
    api.getCalculationProgress.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const refreshing = vm.loadWorkspace(true); await flushPromises()
    expect(page.find('.status-bar').text()).toContain('65%')
    resolve(progress(85, '校验排产结果')); await refreshing; await flushPromises()
    expect(page.find('.status-bar').text()).toContain('85%')
    api.getCalculationProgress.mockRejectedValueOnce(new Error('暂时断网'))
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    await vm.loadWorkspace(true); await flushPromises()
    expect(page.find('.status-bar').text()).toContain('85%'); expect(page.find('.status-bar').text()).toContain('重试中')
    warn.mockRestore(); page.unmount()
  })
  it('ignores progress responses for a previously selected batch', async () => {
    const { page, vm, router } = await openWorkspace()
    let resolve!: (value: unknown) => void
    api.getCalculationProgress.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const oldRequest = vm.loadWorkspace(true); await flushPromises()
    api.getCalculationProgress.mockResolvedValue(progress(10, '计算物料需求'))
    await router.push('/planning/96'); await flushPromises()
    resolve(progress(85, '校验排产结果')); await oldRequest; await flushPromises()
    expect(page.find('.status-bar').text()).toContain('10%'); expect(page.find('.status-bar').text()).not.toContain('85%')
    page.unmount()
  })
  it('shows completion only after the batch is ready', async () => {
    const bar = mount(CalculationProgressBar, { props: { batchStatus: 'WAITING_DAILY_DISTRIBUTION', progress: progress(), retrying: false }, global: { plugins: [ElementPlus] } })
    expect(bar.text()).toContain('65%')
    await bar.setProps({ batchStatus: 'READY', retrying: true }); expect(bar.text()).not.toContain('重试中'); expect(bar.text()).toContain('100%'); expect(bar.text()).toContain('计算完成')
    await bar.setProps({ batchStatus: 'WAITING_DECISION' }); expect(bar.find('[data-testid="calculation-progress"]').exists()).toBe(false)
    bar.unmount()
  })
  it('restores a running publish task from the server after remount and disables its button', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'READY'))
    api.getBatch.mockResolvedValue(batch(95, 'READY'))
    api.getLifecycleTask.mockResolvedValue({ taskId: 12, taskType: 'MRP_PUBLISH', status: 'RUNNING', progressCurrent: 70, progressTotal: 100, phase: '复制日排产（7000/10000）', errorMessage: null, resultBatchId: null })
    const { page } = await openWorkspace()
    expect(page.find('.status-bar').text()).toContain('70%')
    expect(page.find('.status-bar').text()).toContain('复制日排产')
    expect(page.find('.status-bar [data-testid="lifecycle-progress"] .el-progress').exists()).toBe(true)
    expect(page.find('.status-bar [data-testid="calculation-progress"]').exists()).toBe(false)
    expect(page.find('.status-bar .el-button.is-loading').exists()).toBe(true)
    page.unmount()
  })
  it('keeps the last publish progress during polling errors and shows the next phase', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'READY'))
    api.getBatch.mockResolvedValue(batch(95, 'READY'))
    api.getCalculationProgress.mockResolvedValue(progress(100, '计算完成'))
    api.getLifecycleTask.mockResolvedValueOnce({ taskId: 12, taskType: 'MRP_PUBLISH', status: 'RUNNING', progressCurrent: 70, progressTotal: 100, phase: '复制日排产', errorMessage: null, resultBatchId: null })
    const { page, vm } = await openWorkspace()
    expect(page.find('.status-bar [data-testid="lifecycle-progress"]').text()).toContain('70%')
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    api.getLifecycleTask.mockRejectedValueOnce(new Error('暂时断网'))
    await vm.loadWorkspace(true); await flushPromises()
    expect(page.find('.status-bar [data-testid="lifecycle-progress"]').text()).toContain('70%')
    expect(page.find('.status-bar [data-testid="lifecycle-progress"]').text()).toContain('重试中')
    api.getLifecycleTask.mockResolvedValueOnce({ taskId: 12, taskType: 'MRP_PUBLISH', status: 'RUNNING', progressCurrent: 85, progressTotal: 100, phase: '确认发布版本', errorMessage: null, resultBatchId: null })
    await vm.loadWorkspace(true); await flushPromises()
    expect(page.find('.status-bar [data-testid="lifecycle-progress"]').text()).toContain('85%')
    expect(page.find('.status-bar [data-testid="lifecycle-progress"]').text()).toContain('确认发布版本')
    expect(page.find('.status-bar [data-testid="lifecycle-progress"]').text()).not.toContain('重试中')
    warn.mockRestore(); page.unmount()
  })
  it('keeps a running revision below 100% and labels pending work', async () => {
    const bar = mount(CalculationProgressBar, { props: { batchStatus: 'PUBLISHED', progress: null, retrying: false, lifecycle: { taskId: 13, taskType: 'MRP_REVISION', status: 'RUNNING', progressCurrent: 100, progressTotal: 100, phase: '确认修订版本', errorMessage: null, resultBatchId: null } }, global: { plugins: [ElementPlus] } })
    expect(bar.find('[data-testid="lifecycle-progress"]').text()).toContain('99%')
    await bar.setProps({ lifecycle: { taskId: 13, taskType: 'MRP_REVISION', status: 'PENDING', progressCurrent: 0, progressTotal: 100, phase: '等待生成修订版', errorMessage: null, resultBatchId: null } })
    expect(bar.find('[data-testid="lifecycle-progress"]').text()).toContain('修订任务排队中')
    bar.unmount()
  })
  it('shows a failed publish as an exception rather than completed calculation', async () => {
    const bar = mount(CalculationProgressBar, { props: { batchStatus: 'READY', progress: progress(100, '计算完成'), retrying: false, lifecycle: { taskId: 14, taskType: 'MRP_PUBLISH', status: 'FAILED', progressCurrent: 70, progressTotal: 100, phase: '复制日排产', errorMessage: '复制失败', resultBatchId: null } }, global: { plugins: [ElementPlus] } })
    expect(bar.find('[data-testid="lifecycle-progress"]').text()).toContain('70%')
    expect(bar.find('[data-testid="lifecycle-progress"]').text()).toContain('发布失败：复制失败')
    expect(bar.find('.el-progress.is-exception').exists()).toBe(true)
    bar.unmount()
  })
  it('prevents another submission when lifecycle status cannot be read', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'READY'))
    api.getBatch.mockResolvedValue(batch(95, 'READY'))
    api.getLifecycleTask.mockRejectedValue(new Error('暂时断网'))
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { page } = await openWorkspace()
    expect(page.find('.status-bar').text()).toContain('正在重试，请勿重复提交')
    expect(page.find('.status-bar .el-button.is-loading').exists()).toBe(true)
    warn.mockRestore(); page.unmount()
  })
  it.each(['decision', 'unallocated', 'policy', 'stock', 'calendar'])('blocks publishing for a pending %s badge and enables it when resolved', async kind => {
    const ready = { ...batch(95, 'READY'), activeRunId: 42 }
    api.getCurrentBatch.mockResolvedValue(ready); api.getBatch.mockResolvedValue(ready)
    api.getBatchSummary.mockResolvedValue({ openIssues: kind === 'decision' ? 1 : 0, pendingUnallocated: kind === 'unallocated' ? 1 : 0 })
    api.getPendingDefaultExplosionCount.mockResolvedValue(kind === 'policy' ? 1 : 0)
    api.getStockRisksPage.mockResolvedValue({ total: kind === 'stock' ? 1 : 0 })
    api.getPlanningCalendarCheck.mockResolvedValue({ total: kind === 'calendar' ? 1 : 0, missingDays: kind === 'calendar' ? [{ factoryCode: '永惠', date: '2026-10-01' }] : [], configurationIssues: [] })
    const { page, vm } = await openWorkspace()
    const publish = () => page.findAll('button').find(b => b.text() === '发布排产结果')!
    expect(publish().attributes('disabled')).toBeDefined()
    const actions = vm as unknown as { handlePrimaryAction: () => Promise<void> }
    await actions.handlePrimaryAction()
    expect(api.getPublishCapacity).not.toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled()
    api.getBatchSummary.mockResolvedValue({ openIssues: 0, pendingUnallocated: 0 })
    api.getPendingDefaultExplosionCount.mockResolvedValue(0); api.getStockRisksPage.mockResolvedValue({ total: 0 })
    api.getPlanningCalendarCheck.mockResolvedValue({ total: 0, missingDays: [], configurationIssues: [] })
    await vm.loadWorkspace(true); await flushPromises()
    expect(publish().attributes('disabled')).toBeUndefined()
    expect(page.find('.page-actions .el-badge').exists()).toBe(false)
    page.unmount()
  })
  it('keeps publication disabled when calendar validation fails', async () => {
    const ready = { ...batch(95, 'READY'), activeRunId: 42 }
    api.getCurrentBatch.mockResolvedValue(ready); api.getBatch.mockResolvedValue(ready)
    api.getPlanningCalendarCheck.mockRejectedValueOnce(new Error('网络失败'))
    const { page } = await openWorkspace()
    expect(page.text()).toContain('加载生产日历检查失败')
    expect(page.findAll('button').find(b => b.text() === '发布排产结果')?.attributes('disabled')).toBeDefined()
    page.unmount()
  })
  it('warns with latest factory and missing date before creating a revision', async () => {
    api.getCurrentBatch.mockResolvedValue(batch(95, 'PUBLISHED')); api.getBatch.mockResolvedValue(batch(95, 'PUBLISHED'))
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const { page } = await openWorkspace()
    api.getPlanningCalendarCheck.mockResolvedValueOnce({ total: 1, missingDays: [{ factoryCode: '永惠', date: '2026-10-01' }], configurationIssues: [] })
    await page.findAll('button').find(b => b.text() === '生成修订版')!.trigger('click'); await flushPromises()
    expect(api.getPlanningCalendarCheck).toHaveBeenLastCalledWith(95, true)
    expect(confirm).not.toHaveBeenCalled()
    expect(api.reviseBatch).not.toHaveBeenCalled()
    confirm.mockRestore(); page.unmount()
  })

})
