import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElButton, ElDatePicker, ElInput, ElRadioGroup } from 'element-plus'
import DailyPlanningGrid from '../components/LegacyDailyPlanningGrid.vue'

const mocks = vi.hoisted(() => ({ getDailyGrid: vi.fn(), getDailyPlans: vi.fn(), getDailyOverrides: vi.fn(), getCapacityLoads: vi.fn(), transferDaily: vi.fn() }))
vi.mock('../../../api/mrp', () => ({ mrpApi: mocks }))
vi.mock('../../../stores/permission', () => ({ usePermissionStore: () => ({ hasPermission: () => true }) }))
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
const row = (code: string, date: string) => ({ rowKey: code, factoryCode: '爱培科', rootMaterialCode: code, terminalMaterialCode: '末级', terminalType: 'RAW_MATERIAL', supplyMode: 'SELF_MADE', pathGroupKey: 'path', materialUnit: '件', dayQuantities: { [date]: '10' }, rowTotal: '10', weekTotals: {} })
function grid(code: string, date: string) { return { activeRunId: 1, rows: [row(code, date)], totalRows: 1, calendarDays: [date], calendarDayInfo: [{date, productionDay: true, reason: ''}], weekGroups: [], capacityLoads: [] } }
function wrapper() { return mount(DailyPlanningGrid, {props: {batchId: 1}, global: {plugins: [ElementPlus], stubs: {teleport: true, ElSelect: {props: ['modelValue'], template: '<div><slot /></div>'}, ElOption: true}}}) }
beforeEach(() => { vi.clearAllMocks(); mocks.getDailyOverrides.mockResolvedValue([]); mocks.getDailyPlans.mockResolvedValue([]) })

describe('daily planning request races', () => {
  it('can transfer again from a day created by an override without a system plan', async () => {
    const sourceDate = new Date().toISOString().slice(0, 10)
    const source = row('来源', sourceDate)
    mocks.getDailyGrid.mockResolvedValue({...grid('来源', sourceDate), capacityLoads: [{factoryCode: '爱培科', date: sourceDate, configured: true, productionDay: true, load: '10', dailyCapacity: '100', capacityUnit: '件', status: 'NORMAL'}]})
    mocks.getDailyOverrides.mockResolvedValue([{...source, scheduleDate: sourceDate, manualQuantity: '10', revision: 3}])
    mocks.getDailyPlans.mockResolvedValue([])
    mocks.getCapacityLoads.mockResolvedValue([{factoryCode: '爱培科', date: '2030-01-01', configured: true, productionDay: true, load: '0', dailyCapacity: '100', capacityUnit: '件', status: 'NORMAL'}])
    mocks.transferDaily.mockResolvedValue(undefined)
    const page = wrapper(); await flushPromises()
    await page.find('.day-cell').trigger('click'); await flushPromises()
    expect((page.vm as unknown as {editing: {systemQuantity: string}}).editing.systemQuantity).toBe('0')
    const picker = page.findComponent(ElDatePicker)
    picker.vm.$emit('update:modelValue', '2030-01-01'); picker.vm.$emit('change', '2030-01-01')
    page.findAllComponents(ElInput).find(input => input.props('placeholder') === '正数，最多6位小数')!.vm.$emit('update:modelValue', '10')
    page.findAllComponents(ElInput).find(input => input.props('type') === 'textarea')!.vm.$emit('update:modelValue', '回转空白日')
    await flushPromises()
    await page.findAllComponents(ElButton).find(button => button.text() === '确认')!.trigger('click'); await flushPromises()
    expect(mocks.transferDaily).toHaveBeenCalledWith(1, expect.objectContaining({sourceDate, expectedSourceQuantity: '10', expectedSourceRevision: 3, expectedTargetQuantity: '0', expectedTargetRevision: -1, quantity: '10'}))
    page.unmount()
  })

  it('keeps unknown demand visible when known load falls below daily capacity', async () => {
    const sourceDate = new Date().toISOString().slice(0, 10)
    const data = grid('来源', sourceDate)
    data.rows[0]!.dayQuantities[sourceDate] = '1000'
    mocks.getDailyGrid.mockResolvedValue({...data, capacityLoads: [{factoryCode: '爱培科', date: sourceDate, configured: true, productionDay: true, load: '1200', dailyCapacity: '1000', capacityUnit: '件', status: 'OVERLOAD', partiallyUnassessed: true}]})
    mocks.getDailyPlans.mockResolvedValue([{...row('来源', sourceDate), scheduleDate: sourceDate, systemQuantity: '1000', effectiveQuantity: '1000'}])
    mocks.getCapacityLoads.mockResolvedValue([{factoryCode: '爱培科', date: '2030-01-01', configured: true, productionDay: true, load: '0', dailyCapacity: '1000', capacityUnit: '件', status: 'NORMAL', partiallyUnassessed: false}])
    const page = wrapper(); await flushPromises()
    await page.find('.day-cell').trigger('click'); await flushPromises()
    const picker = page.findComponent(ElDatePicker)
    picker.vm.$emit('update:modelValue', '2030-01-01'); picker.vm.$emit('change', '2030-01-01')
    page.findAllComponents(ElInput).find(input => input.props('placeholder') === '正数，最多6位小数')!.vm.$emit('update:modelValue', '200')
    await flushPromises()
    expect(page.find('.transfer-preview').text()).toContain('产能未评估；部分需求未评估')
    page.unmount()
  })

  it('keeps the latest date window when the earlier request completes last', async () => {
    const first = deferred<ReturnType<typeof grid>>(); const latest = deferred<ReturnType<typeof grid>>()
    mocks.getDailyGrid.mockReturnValueOnce(first.promise).mockReturnValueOnce(latest.promise)
    const page = wrapper(); await flushPromises()
    const next = page.findAllComponents(ElButton).find(button => button.text() === '后28天')!
    await next.trigger('click'); await flushPromises()
    const date = mocks.getDailyGrid.mock.calls[1]![1].startDate
    latest.resolve(grid('新窗口', date)); await flushPromises()
    first.resolve(grid('旧窗口', '2020-01-01')); await flushPromises()
    expect(page.find('.daily-table').text()).toContain('新窗口')
    expect(page.find('.daily-table').text()).not.toContain('旧窗口')
    page.unmount()
  })

  it('clears target loading and rejects a stale response after clearing the date', async () => {
    const sourceDate = new Date().toISOString().slice(0, 10)
    mocks.getDailyGrid.mockResolvedValue(grid('来源', sourceDate))
    mocks.getDailyPlans.mockResolvedValue([{...row('来源', sourceDate), scheduleDate: sourceDate, systemQuantity: '10', effectiveQuantity: '10'}])
    const loads = deferred<unknown[]>(); mocks.getCapacityLoads.mockReturnValue(loads.promise)
    const page = wrapper(); await flushPromises()
    await page.find('.day-cell').trigger('click'); await flushPromises()
    const picker = page.findComponent(ElDatePicker)
    picker.vm.$emit('update:modelValue', '2030-01-01'); picker.vm.$emit('change', '2030-01-01'); await flushPromises()
    expect((page.vm as unknown as {targetLoading: boolean}).targetLoading).toBe(true)
    picker.vm.$emit('update:modelValue', ''); picker.vm.$emit('change', ''); await flushPromises()
    expect((page.vm as unknown as {targetLoading: boolean}).targetLoading).toBe(false)
    loads.resolve([{factoryCode: '爱培科', date: '2030-01-01', configured: true, productionDay: true, load: '0', dailyCapacity: '100', capacityUnit: '件', status: 'NORMAL'}]); await flushPromises()
    expect((page.vm as unknown as {targetLoad: unknown}).targetLoad).toBe(null)
    expect(mocks.transferDaily).not.toHaveBeenCalled()
    page.unmount()
  })
})


describe('machine capacity basis', () => {
  it.each(['MACHINE_OUTPUT', 'RAW_MATERIAL'])('only machine transfers change factory machine load: %s', async (type) => {
    const date = new Date().toISOString().slice(0, 10)
    const source = { ...row('整机', date), terminalType: type, materialUnit: type === 'MACHINE_OUTPUT' ? '台' : '件' }
    const data = { ...grid('整机', date), rows: [source], view: type === 'MACHINE_OUTPUT' ? 'MACHINE_OUTPUT' : 'TERMINAL', capacityBasis: 'MACHINE_OUTPUT', ruleVersion: 'v3-whole-machine-capacity', capacityLoads: [{ factoryCode: '爱培科', date, configured: true, productionDay: true, load: '1200', dailyCapacity: '1000', capacityUnit: '台', status: 'OVERLOAD' }] }
    mocks.getDailyGrid.mockResolvedValue(data)
    mocks.getDailyPlans.mockResolvedValue([{...source, scheduleDate: date, systemQuantity: '10', effectiveQuantity: '10'}])
    mocks.getCapacityLoads.mockResolvedValue([{factoryCode: '爱培科', date: '2030-01-01', configured: true, productionDay: true, load: '900', dailyCapacity: '1000', capacityUnit: '台', status: 'NORMAL'}])
    const page = wrapper(); await flushPromises()
    expect(page.find('.day-cell').classes('overload')).toBe(type === 'MACHINE_OUTPUT')
    expect(mocks.getDailyGrid).toHaveBeenLastCalledWith(1, expect.objectContaining({view: undefined}))
    await page.find('.day-cell').trigger('click'); await flushPromises()
    const picker = page.findComponent(ElDatePicker)
    picker.vm.$emit('update:modelValue', '2030-01-01'); picker.vm.$emit('change', '2030-01-01')
    const input = page.findAllComponents(ElInput).find(item => item.props('placeholder') === (type === 'MACHINE_OUTPUT' ? '正整数（台）' : '正数，最多6位小数'))!
    input.vm.$emit('update:modelValue', '2'); await flushPromises()
    expect(page.find('.transfer-preview').text()).toContain(type === 'MACHINE_OUTPUT' ? '1,198 / 1,000 台' : '1,200 / 1,000 台')
    expect(page.find('.transfer-preview').text()).toContain(type === 'MACHINE_OUTPUT' ? '902 / 1,000 台' : '900 / 1,000 台')
    if (type === 'MACHINE_OUTPUT') {
      input.vm.$emit('update:modelValue', '1.5'); await flushPromises()
      expect(page.find('.transfer-preview').text()).not.toContain('调整后预览')
    }
    page.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'TERMINAL'); await flushPromises()
    expect(mocks.getDailyGrid).toHaveBeenLastCalledWith(1, expect.objectContaining({view: 'TERMINAL'}))
    page.unmount()
  })
})
