import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElRadioGroup } from 'element-plus'
import WeeklySummary from '../components/LegacyWeeklySummary.vue'
import MonthlySummary from '../components/LegacyMonthlySummary.vue'

const mocks = vi.hoisted(() => ({ getWeeklySummary: vi.fn(), getMonthlySummary: vi.fn() }))
vi.mock('../../../api/mrp', () => ({ mrpApi: mocks }))
beforeEach(() => vi.clearAllMocks())

describe('plan summary capacity basis', () => {
  it.each(['weekly', 'monthly'] as const)('keeps machine and terminal %s totals separate and precise', async (period) => {
    const api = period === 'weekly' ? mocks.getWeeklySummary : mocks.getMonthlySummary
    api.mockResolvedValueOnce([{ monthKey: '2030-01', businessMonth: '2030-01', weekKey: '2030-01-W1', weekStart: '2030-01-01', weekEnd: '2030-01-07', totalQuantity: '1000', materialUnit: '台', planCount: 2 }])
      .mockResolvedValueOnce([{ monthKey: '2030-01', businessMonth: '2030-01', weekKey: '2030-01-W1', weekStart: '2030-01-01', weekEnd: '2030-01-07', totalQuantity: '900719999999.000001', materialUnit: '件', planCount: 3 }, { monthKey: '2030-01', businessMonth: '2030-01', weekKey: '2030-01-W1', weekStart: '2030-01-01', weekEnd: '2030-01-07', totalQuantity: '12', materialUnit: null, aggregationKey: 'MISSING-M001', planCount: 1 }])
    const page = mount(period === 'weekly' ? WeeklySummary : MonthlySummary, {props: {batchId: 5, ruleVersion: 'v3-whole-machine-capacity'}, global: {plugins: [ElementPlus]}})
    await flushPromises()
    expect(api).toHaveBeenLastCalledWith(5, 'MACHINE_OUTPUT')
    expect(page.text()).toContain('整机产量（台）')
    page.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'TERMINAL'); await flushPromises()
    expect(api).toHaveBeenLastCalledWith(5, 'TERMINAL')
    expect(page.text()).toContain('900,719,999,999.000001')
    expect(page.text()).toContain('单位未知 · MISSING-M001')
    expect(page.text()).not.toContain('整机产量（台）')
    page.unmount()
  })

  it('opens a historical batch in its terminal view', async () => {
    mocks.getWeeklySummary.mockResolvedValue([])
    const page = mount(WeeklySummary, {props: {batchId: 1, ruleVersion: 'v2-daily-capacity'}, global: {plugins: [ElementPlus]}})
    await flushPromises()
    expect(mocks.getWeeklySummary).toHaveBeenCalledWith(1, 'TERMINAL')
    expect(page.findComponent(ElRadioGroup).exists()).toBe(false)
    expect(page.text()).toContain('暂无周汇总数据')
    page.unmount()
  })
})
