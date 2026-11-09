import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mrpApi } from '../mrp'
import { get, post } from '../client'
vi.mock('../client', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn(), download: vi.fn(), upload: vi.fn(), uploadBlob: vi.fn() }))
const params = () => new URL(vi.mocked(get).mock.calls.at(-1)![0], 'http://localhost').searchParams
beforeEach(() => { vi.mocked(get).mockReset(); vi.mocked(get).mockResolvedValue({ items: [] }) })
describe('MRP filters and record logs', () => {
  it.each([undefined, null, '', '  '])('omits cleared filter %s', async (empty) => {
    await mrpApi.getStockPolicyPage('', empty, 1, 20)
    expect(params().has('warehouseCode')).toBe(false)
    expect(params().get('pageSize')).toBe('20')
    await mrpApi.getMaterialPolicyPage('', empty, empty, 1, 20)
    expect(params().has('supplyMode')).toBe(false)
    expect(params().has('explosionPolicy')).toBe(false)
  })
  it('keeps effective filters', async () => {
    await mrpApi.getMaterialPolicyPage(' 电机 ', 'SELF_MADE', 'STOP', 2, 10)
    expect(Object.fromEntries(params())).toEqual({ keyword: '电机', supplyMode: 'SELF_MADE', explosionPolicy: 'STOP', page: '2', pageSize: '10' })
    await mrpApi.getStockPolicyPage('', 'YH', 1, 20)
    expect(params().get('warehouseCode')).toBe('YH')
  })
  it('omits empty notice filter', async () => {
    await mrpApi.getMaterialPolicyNotices(1, '', '', 1, 20)
    expect(params().has('missingType')).toBe(false)
  })
  it('confirms default explosion only within the selected batch', async () => {
    await mrpApi.getPendingDefaultExplosionCount(106)
    expect(vi.mocked(get).mock.calls.at(-1)?.[0]).toBe('/mrp/batches/106/material-policy-notices/pending-default-count')
    await mrpApi.confirmDefaultExplosion(106)
    expect(vi.mocked(post).mock.calls.at(-1)?.[0]).toBe('/mrp/batches/106/material-policy-notices/confirm-default-explosion')
  })
  it('uses the selected allocation resource when restoring automatic allocation', async () => {
    await mrpApi.resetAllocation(73)
    expect(vi.mocked(post).mock.calls.at(-1)?.[0]).toBe('/mrp/inventory/allocations/73/reset')
  })
  it('paginates pending unallocated rows in the selected batch', async () => {
    await mrpApi.getPendingUnallocatedPage(106, 2, 20, ' 日历 ')
    expect(vi.mocked(get).mock.calls.at(-1)?.[0]).toContain('/mrp/batches/106/unallocated/page?')
    expect(Object.fromEntries(params())).toEqual({ page: '2', pageSize: '20', keyword: '日历' })
  })
  it('queries exact record key before pagination', async () => {
    await mrpApi.getConfigLogs('stock_policy', 'YH|M001')
    expect(params().get('resourceId')).toBe('YH|M001')
    expect(params().has('keyword')).toBe(false)
  })
})


describe('production view queries', () => {
  it('uses default view when omitted and passes explicit views to all aggregations', async () => {
    await mrpApi.getDailyGrid(1, {page: 1, pageSize: 20})
    expect(params().has('view')).toBe(false)
    await mrpApi.getDailyGrid(1, {page: 1, pageSize: 20, view: 'MACHINE_OUTPUT'})
    expect(params().get('view')).toBe('MACHINE_OUTPUT')
    await mrpApi.getWeeklySummary(1, 'TERMINAL')
    expect(params().get('view')).toBe('TERMINAL')
    await mrpApi.getMonthlySummary(1, 'MACHINE_OUTPUT')
    expect(params().get('view')).toBe('MACHINE_OUTPUT')
    await mrpApi.getPlanningCellDetails(1, { page: 1, pageSize: 20, factoryCode: '永惠', startDate: '2026-10-29', endDate: '2026-10-29', view: 'MACHINE_OUTPUT' })
    expect(params().get('view')).toBe('MACHINE_OUTPUT')
    expect(params().get('terminalType')).toBe('MACHINE_OUTPUT')
  })
})

describe('shortage material and warehouse queries', () => {
  it('keeps source month on the material page and paginates a specific factory material pool', async () => {
    await mrpApi.getShortagePage(73, { page: 2, pageSize: 20, factoryCode: '永惠', sourceMonth: '2026-10-01' })
    expect(vi.mocked(get).mock.calls.at(-1)?.[0]).toContain('/mrp/batches/73/shortage/page?')
    expect(params().get('sourceMonth')).toBe('2026-10-01')
    await mrpApi.getShortagePools(73, { page: 2, pageSize: 20, factoryCode: '永惠', terminalMaterialCode: 'PCBA-01' })
    expect(vi.mocked(get).mock.calls.at(-1)?.[0]).toContain('/mrp/batches/73/shortage/pools?')
    expect(Object.fromEntries(params())).toEqual({ page: '2', pageSize: '20', factoryCode: '永惠', terminalMaterialCode: 'PCBA-01' })
  })
})


describe('batch creation defaults', () => {
  it('sends one inventory version and omits BOM/role overrides for server-side latest resolution', async () => {
    await mrpApi.createBatch('爱培科', 5, 20)
    const query = new URL(vi.mocked(post).mock.calls.at(-1)![0], 'http://localhost').searchParams
    expect(Object.fromEntries(query)).toEqual({ factoryCode: '爱培科', forecastVersionId: '5', inventorySnapshotId: '20' })
  })
})
