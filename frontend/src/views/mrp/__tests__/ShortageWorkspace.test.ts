import { reactive } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import ShortageWorkspace from '../ShortageWorkspace.vue'

const route = reactive({ params: { id: '1' }, query: {}, fullPath: '/shortage/1' })
const mocks = vi.hoisted(() => ({
  getBatch: vi.fn(), getWorkbenchReviews: vi.fn(), getBatchFactories: vi.fn(),
  getShortagePage: vi.fn(), getMaterialFeedback: vi.fn(), getIssuesPage: vi.fn(),
  getPendingDefaultExplosionCount: vi.fn(), getStockRisksPage: vi.fn(),
  getPendingUnallocatedPage: vi.fn(), getCalculationProgress: vi.fn(), confirmMaterial: vi.fn()
}))
vi.mock('../../../api/mrp', () => ({ mrpApi: mocks }))
vi.mock('../../../stores/permission', () => ({ usePermissionStore: () => ({ hasPermission: () => true }) }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn() }) }))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}
function batch(id: number) { return { id, activeRunId: id, factoryCode: '永惠', revisionNo: 1, ruleVersion: 'v5-shifted-business-weeks', status: 'MATERIAL_REVIEW' } }
function demand(code: string) { return { factoryCode: '永惠', terminalMaterialCode: code, terminalType: 'RAW_MATERIAL', supplyMode: 'PURCHASED', quantity: 1, inventoryPoolCount: 0, warehouseRiskCount: 1 } }

beforeEach(() => {
  vi.clearAllMocks()
  route.params.id = '1'; route.fullPath = '/shortage/1'
  mocks.getBatch.mockImplementation(async (id: number) => batch(id))
  mocks.getWorkbenchReviews.mockResolvedValue({ schedule: 'CONFIRMED', material: 'PENDING', openFeedback: 0 })
  mocks.getBatchFactories.mockResolvedValue(['永惠'])
  mocks.getShortagePage.mockResolvedValue({ items: [], total: 0 })
  mocks.getMaterialFeedback.mockResolvedValue({ items: [], total: 0 })
  mocks.getIssuesPage.mockResolvedValue({ total: 0 })
  mocks.getPendingDefaultExplosionCount.mockResolvedValue(0)
  mocks.getStockRisksPage.mockResolvedValue({ total: 0 })
  mocks.getPendingUnallocatedPage.mockResolvedValue({ total: 0 })
  mocks.getCalculationProgress.mockResolvedValue(null)
})

describe('shortage workspace request order', () => {
  it('skips review fingerprints for published legacy batches but keeps v5 reviews', async () => {
    mocks.getBatch.mockImplementation(async (id: number) => ({ ...batch(id),
      status: 'PUBLISHED', ruleVersion: id === 1 ? 'v4-rolling-business-weeks' : 'v5-shifted-business-weeks' }))
    const page = shallowMount(ShortageWorkspace, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(mocks.getWorkbenchReviews).not.toHaveBeenCalled()
    expect(mocks.getShortagePage).toHaveBeenCalledWith(1, expect.anything())
    route.params.id = '2'; route.fullPath = '/shortage/2'
    await flushPromises()
    expect(mocks.getWorkbenchReviews).toHaveBeenCalledExactlyOnceWith(2)
    page.unmount()
  })

  it('ignores an old batch response after navigation', async () => {
    const old = deferred<ReturnType<typeof batch>>()
    mocks.getBatch.mockImplementation((id: number) => id === 1 ? old.promise : Promise.resolve(batch(id)))
    const page = shallowMount(ShortageWorkspace, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    route.params.id = '2'; route.fullPath = '/shortage/2'
    await flushPromises()
    old.resolve(batch(1)); await flushPromises()
    expect(page.text()).toContain('批次 #2')
    expect(page.text()).not.toContain('批次 #1')
    page.unmount()
  })

  it('keeps new batch demand when the previous batch query finishes last', async () => {
    const old = deferred<{ items: ReturnType<typeof demand>[]; total: number }>()
    mocks.getShortagePage.mockImplementation((id: number) => id === 1 ? old.promise : Promise.resolve({ items: [demand('NEW')], total: 1 }))
    const page = shallowMount(ShortageWorkspace, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    route.params.id = '2'; route.fullPath = '/shortage/2'
    await flushPromises()
    old.resolve({ items: [demand('OLD')], total: 1 }); await flushPromises()
    const state = page.vm as unknown as { demands: { terminalMaterialCode: string }[] }
    expect(state.demands.map(item => item.terminalMaterialCode)).toEqual(['NEW'])
    page.unmount()
  })

  it('does not apply an old material confirmation to the new batch', async () => {
    const old = deferred<{ material: string; materialFingerprint: string }>()
    const next = deferred<{ material: string; materialFingerprint: string }>()
    mocks.getWorkbenchReviews.mockImplementation(async (id: number) => ({ schedule: 'CONFIRMED', material: 'PENDING', materialFingerprint: `fp-${id}`, openFeedback: 0 }))
    mocks.confirmMaterial.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise)
    const page = shallowMount(ShortageWorkspace, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const state = page.vm as unknown as { confirmMaterial: () => Promise<void>; review: { material: string; materialFingerprint: string }; saving: boolean }
    const confirming = state.confirmMaterial()
    expect(mocks.confirmMaterial).toHaveBeenCalledWith(1, 'fp-1')
    route.params.id = '2'; route.fullPath = '/shortage/2'
    await flushPromises()
    expect(state.saving).toBe(false)
    const nextConfirmation = state.confirmMaterial()
    expect(mocks.confirmMaterial).toHaveBeenCalledWith(2, 'fp-2')
    old.resolve({ material: 'CONFIRMED', materialFingerprint: 'fp-1' })
    await confirming; await flushPromises()
    expect(state.review.material).toBe('PENDING')
    expect(state.saving).toBe(true)
    mocks.getWorkbenchReviews.mockImplementation(async (id: number) => ({ schedule: 'CONFIRMED', material: id === 2 ? 'CONFIRMED' : 'PENDING', materialFingerprint: `fp-${id}`, openFeedback: 0 }))
    next.resolve({ material: 'CONFIRMED', materialFingerprint: 'fp-2' })
    await nextConfirmation; await flushPromises()
    expect(state.review.material).toBe('CONFIRMED')
    expect(state.review.materialFingerprint).toBe('fp-2')
    expect(state.saving).toBe(false)
    page.unmount()
  })
})
