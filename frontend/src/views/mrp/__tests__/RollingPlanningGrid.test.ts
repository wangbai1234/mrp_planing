import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import RollingPlanningGrid from '../components/RollingPlanningGrid.vue'
import DailyPlanningGrid from '../components/DailyPlanningGrid.vue'
import WeeklySummary from '../components/WeeklySummary.vue'
import MonthlySummary from '../components/MonthlySummary.vue'
import type { DailyGridRow, LinkedTransferPreview, PlanningCellFact } from '../../../api/mrp'

const api = vi.hoisted(() => ({ getRollingGrid: vi.fn(), getPlanningCellDetails: vi.fn(), previewLinkedTransfer: vi.fn(), commitLinkedTransfer: vi.fn() }))
vi.mock('../../../api/mrp', () => ({ mrpApi: api }))
vi.mock('../../../stores/permission', () => ({ usePermissionStore: () => ({ hasPermission: () => true }) }))
const days = ['2026-09-30', '2026-10-01', '2026-10-02']
const row = { rowKey: 'visual', factoryCode: 'APK', rootMaterialCode: 'A', terminalMaterialCode: 'D', terminalType: 'RAW_MATERIAL', supplyMode: 'SELF_MADE', pathGroupKey: '', materialUnit: '件', rowTotal: '201', dayQuantities: { '2026-09-30': '33', '2026-10-01': '84', '2026-10-02': '84' }, weekTotals: {} } satisfies DailyGridRow
const fact = { rowKey: 'machine-key', factoryCode: 'APK', rootMaterialCode: 'A', terminalMaterialCode: 'A', terminalType: 'MACHINE_OUTPUT', sourceMonth: '2026-10-01', scheduleDate: days[0], effectiveQuantity: '34', materialUnit: '台' } as PlanningCellFact
const line = (type: string, suggested: string) => ({ rowKey: type, terminalMaterialCode: type === 'MACHINE_OUTPUT' ? 'A' : 'D', terminalType: type, supplyMode: 'SELF_MADE', sourceMonth: '2026-10-01', pathGroupKey: 'p', sourceQuantity: type === 'MACHINE_OUTPUT' ? '34' : '167', targetQuantity: '0', suggestedQuantity: suggested, sourceRevision: -1, targetRevision: -1 })
interface PageVm {
  periods: Array<{ key: string; days: string[] }>
  startTransfer: (fact: PlanningCellFact) => void
  makePreview: () => Promise<void>
  confirmTransfer: () => Promise<void>
  targetDate: string
  machineQuantity: string
  reason: string
  edits: Record<string,string>
  validTransfers: boolean
  transferError: string
  preview: LinkedTransferPreview | null
  periodQuantity: (row: DailyGridRow, period: { days: string[] }) => string
}
function wrapper(mode: 'day'|'week'|'month' = 'day') {
  return mount(RollingPlanningGrid, { props: { batchId: 8, mode }, global: { plugins: [ElementPlus], stubs: { teleport: true, ElAutoResizer: { template: '<div><slot :width="1200" :height="500" /></div>' }, ElTableV2: { template: '<div />', methods: { scrollToLeft: vi.fn() } }, ElSelect: { props: ['modelValue'], template: '<div><slot /></div>' }, ElOption: true } } })
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getRollingGrid.mockResolvedValue({ activeRunId: 90, ruleVersion: 'v4-rolling-business-weeks', capacityBasis: 'MACHINE_OUTPUT', rows: [row], totalRows: 1, calendarDays: days, weekGroups: [{ weekKey: '2026-09-W5', weekStart: days[0], weekEnd: days[2], days }], capacityLoads: [], calendarDayInfo: [] })
  api.getPlanningCellDetails.mockResolvedValue({ items: [], total: 0 })
  api.previewLinkedTransfer.mockResolvedValue({ fingerprint: 'version-hash', lines: [line('MACHINE_OUTPUT', '17.000000'), line('RAW_MATERIAL', '84.000000')], sourceLoad: null, targetLoad: null })
  api.commitLinkedTransfer.mockResolvedValue(undefined)
})
describe('rolling planning date axis and exact transfers', () => {
  it('shows an empty machine result while retaining the whole-factory load', async () => {
    api.getRollingGrid.mockResolvedValue({ activeRunId: 90, ruleVersion: 'v5-shifted-business-weeks', capacityBasis: 'MACHINE_OUTPUT', rows: [], totalRows: 0, calendarDays: days, weekGroups: [], capacityLoads: [{ factoryCode: 'APK', date: days[0], load: '11255', dailyCapacity: '10', status: 'OVERLOAD', productionDay: true }], calendarDayInfo: [] })
    const page = wrapper(); await flushPromises()
    expect(page.text()).toContain('暂无匹配的整机')
    expect(page.text()).not.toContain('暂无匹配的排产数据')
    expect((page.vm as unknown as { fixedRows: DailyGridRow[] }).fixedRows).toHaveLength(1)
    page.unmount()
  })
  it.each(['v4-rolling-business-weeks', 'v5-shifted-business-weeks'])('allows daily linked adjustment for %s', async ruleVersion => {
    api.getRollingGrid.mockResolvedValue({ activeRunId: 90, ruleVersion, capacityBasis: 'MACHINE_OUTPUT', rows: [row], totalRows: 1, calendarDays: days, weekGroups: [], capacityLoads: [] })
    const page = wrapper(); await flushPromises()
    expect((page.vm as unknown as { canEdit: boolean }).canEdit).toBe(true)
    expect(page.text()).not.toContain('旧规则计划')
    page.unmount()
  })
  it('bounds rendered date columns while preserving total width and reusing requests across modes', async () => {
    const calendarDays = Array.from({ length: 184 }, (_, i) => new Date(Date.UTC(2026, 8, 30 + i)).toISOString().slice(0, 10))
    api.getRollingGrid.mockResolvedValue({ activeRunId: 90, ruleVersion: 'v4-rolling-business-weeks', rows: [row], totalRows: 1, calendarDays, weekGroups: [], capacityLoads: [] })
    const page = wrapper(); await flushPromises()
    const vm = page.vm as unknown as { columns: Array<{key: string; width: number}>; onScroll: (p: { scrollLeft: number }) => void }
    const width = () => vm.columns.reduce((sum, column) => sum + column.width, 0)
    expect(vm.columns.length).toBeLessThan(20); expect(width()).toBe(480 + 184 * 128)
    vm.onScroll({ scrollLeft: 18000 }); await flushPromises()
    expect(width()).toBe(480 + 184 * 128)
    expect(vm.columns.some(column => column.key === calendarDays[142])).toBe(true)
    await page.setProps({ mode: 'month' }); await flushPromises()
    expect(api.getRollingGrid).toHaveBeenCalledTimes(1)
    expect(vm.columns.some(column => column.key === '2027-04')).toBe(true)
    await page.setProps({ mode: 'day' }); await flushPromises()
    expect(vm.columns.some(column => column.key === calendarDays[0])).toBe(true)
    page.unmount()
  })
  it.each([
    [DailyPlanningGrid, 'day'], [WeeklySummary, 'week'], [MonthlySummary, 'month']
  ] as const)('renders the continuous table for published legacy plans: %s', (component, mode) => {
    const page = mount(component, { props: { batchId: 86, ruleVersion: 'v2' }, global: { stubs: { RollingPlanningGrid: true } } })
    const grid = page.findComponent(RollingPlanningGrid)
    expect(grid.exists()).toBe(true)
    expect(grid.props('mode')).toBe(mode)
    expect(grid.props('readOnly')).toBe(true)
    page.unmount()
  })
  it('keeps legacy terminal quantities out of the machine load summary', async () => {
    api.getRollingGrid.mockResolvedValue({ activeRunId: 90, ruleVersion: 'v2', capacityBasis: 'TERMINAL', rows: [row], totalRows: 1, calendarDays: days, weekGroups: [], capacityLoads: [] })
    const page = wrapper(); await flushPromises()
    expect(page.text()).toContain('旧规则计划')
    expect(page.text()).toContain('旧版本未记录整机产出')
    expect((page.vm as unknown as { canEdit: boolean; fixedRows: unknown[] }).canEdit).toBe(false)
    expect((page.vm as unknown as { fixedRows: unknown[] }).fixedRows).toEqual([])
    page.unmount()
  })
  it('labels the frozen daily capacity separately from the scheduled machine load', async () => {
    api.getRollingGrid.mockResolvedValue({ activeRunId: 54, ruleVersion: 'v5-shifted-business-weeks', capacityBasis: 'MACHINE_OUTPUT', rows: [], totalRows: 0, calendarDays: ['2026-10-29'], weekGroups: [], capacityLoads: [{ factoryCode: '永惠', date: '2026-10-29', load: '11255', dailyCapacity: '10', excess: '11245', capacityUnit: '台', productionDay: true, configured: true, status: 'OVERLOAD' }] })
    const page = wrapper(); await flushPromises()
    const vm = page.vm as unknown as { fixedRows: Array<{ rowKey: string }>; cellValues: Map<string, Map<string, { text: string }>> }
    expect(vm.cellValues.get(vm.fixedRows[0]!.rowKey)?.get('2026-10-29')?.text).toBe('已排 11,255 台\n日产能 10 台')
    expect(page.text()).toContain('全厂已排负荷来自整机日事实')
    page.unmount()
  })
  it('requests the whole version without a 28-day window and aggregates the same rows by natural month', async () => {
    const page = wrapper('month'); await flushPromises(); const vm = page.vm as unknown as PageVm
    expect(api.getRollingGrid).toHaveBeenCalledWith(8, { page: 1, pageSize: 20, factoryCode: undefined, keyword: undefined, rootMaterialCode: undefined, view: 'MACHINE_OUTPUT' })
    expect(vm.periods.map(period => period.key)).toEqual(['2026-09','2026-10'])
    expect(vm.periodQuantity(row, vm.periods[1]!)).toBe('168')
    page.unmount()
  })
  it('filters the machine grid by the shortage source root', async () => {
    const page = mount(RollingPlanningGrid, { props: { batchId: 8, mode: 'day', rootMaterialCode: 'A' }, global: { plugins: [ElementPlus], stubs: { teleport: true, ElAutoResizer: { template: '<div><slot :width="1200" :height="500" /></div>' }, ElTableV2: { template: '<div />', methods: { scrollToLeft: vi.fn() } }, ElSelect: { props: ['modelValue'], template: '<div><slot /></div>' }, ElOption: true } } })
    await flushPromises()
    expect(api.getRollingGrid).toHaveBeenCalledWith(8, expect.objectContaining({ rootMaterialCode: 'A', view: 'MACHINE_OUTPUT' }))
    page.unmount()
  })
  it('uses the frozen business week even when it crosses natural months', async () => {
    const page = wrapper('week'); await flushPromises(); const vm = page.vm as unknown as PageVm
    expect(vm.periods[0]!.key).toBe('2026-09-W5')
    expect(vm.periodQuantity(row, vm.periods[0]!)).toBe('201')
    page.unmount()
  })
  it('normalizes padded integers, rejects fractional edits and atomically submits every integer line', async () => {
    const page = wrapper(); await flushPromises(); const vm = page.vm as unknown as PageVm
    vm.startTransfer(fact); vm.targetDate = '2026-10-02'; vm.machineQuantity = '17'; await flushPromises(); await vm.makePreview(); await flushPromises()
    expect(vm.edits.RAW_MATERIAL).toBe('84')
    expect(vm.validTransfers).toBe(true)
    vm.edits.RAW_MATERIAL = '83.5'; await flushPromises()
    expect(vm.validTransfers).toBe(false)
    await vm.confirmTransfer(); expect(api.commitLinkedTransfer).not.toHaveBeenCalled()
    vm.edits.RAW_MATERIAL = '83'; vm.reason = '人工均衡负荷'; await flushPromises(); await vm.confirmTransfer()
    expect(api.commitLinkedTransfer).not.toHaveBeenCalled()
    vm.edits.RAW_MATERIAL = '84'; await flushPromises(); await vm.confirmTransfer()
    expect(api.commitLinkedTransfer).toHaveBeenCalledWith(8, expect.objectContaining({ rowKey: 'machine-key', quantity: '17', expectedRunId: 90, fingerprint: 'version-hash', lines: [{ rowKey: 'MACHINE_OUTPUT', quantity: '17' }, { rowKey: 'RAW_MATERIAL', quantity: '84' }] }))
    page.unmount()
  })
  it('rejects fractional suggestions instead of silently rounding a backend discrepancy', async () => {
    api.previewLinkedTransfer.mockResolvedValue({ fingerprint: 'bad', lines: [line('RAW_MATERIAL','83.500000')] })
    const page = wrapper(); await flushPromises(); const vm = page.vm as unknown as PageVm
    vm.startTransfer(fact); vm.targetDate = '2026-10-02'; vm.machineQuantity = '17'; await flushPromises(); await vm.makePreview(); await flushPromises()
    expect(vm.preview).toBeNull(); expect(vm.transferError).toContain('非整数')
    page.unmount()
  })
  it('invalidates an in-flight preview when the machine quantity changes', async () => {
    let resolve!: (value: unknown) => void
    api.previewLinkedTransfer.mockImplementation(() => new Promise(done => { resolve = done }))
    const page = wrapper(); await flushPromises(); const vm = page.vm as unknown as PageVm
    vm.startTransfer(fact); vm.targetDate = '2026-10-02'; vm.machineQuantity = '17'; await flushPromises()
    const pending = vm.makePreview(); vm.machineQuantity = '18'; await flushPromises()
    resolve({ fingerprint: 'old', lines: [line('MACHINE_OUTPUT', '17')] }); await pending; await flushPromises()
    expect(vm.preview).toBeNull(); expect(vm.validTransfers).toBe(false)
    page.unmount()
  })
})
