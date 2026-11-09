import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import BatchCreateDialog from '../components/BatchCreateDialog.vue'

const api = vi.hoisted(() => ({ getFactories: vi.fn(), getForecastVersions: vi.fn(), getCurrentForecastVersion: vi.fn(), getBatchCreationVersions: vi.fn(), getFactoryWarehouseMappings: vi.fn(), getBusinessWeeks: vi.fn(), getCurrentBomStatus: vi.fn(), getInventorySnapshots: vi.fn(), getForecastMaterials: vi.fn(), createBatch: vi.fn() }))
vi.mock('../../../api/mrp', () => ({ mrpApi: api }))
interface DialogVm { factoryCode: string; inventorySnapshotId: number | null; missingWarehouses: string[]; ready: boolean; submit: () => Promise<void>; calendarMonths: string[]; forecastVersionId: number }
const shared = { id: 20, warehouseCode: null, status: 'IMPORTED' }
async function openDialog() {
  const dialog = mount(BatchCreateDialog, { props: { modelValue: false }, global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: { props: ['modelValue'], template: '<div><slot /></div>' }, ElOption: true } } })
  await dialog.setProps({ modelValue: true }); await flushPromises()
  return dialog
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getFactories.mockResolvedValue(['爱培科','永惠'])
  api.getForecastVersions.mockResolvedValue([{ id: 5, versionNo: 1, status: 'IMPORTED' }])
  api.getCurrentForecastVersion.mockResolvedValue({ id: 5 })
  api.getBatchCreationVersions.mockResolvedValue({ categoryRoleVersions: [{ id: 8, versionNo: 1, status: 'PUBLISHED' }] })
  api.getFactoryWarehouseMappings.mockResolvedValue([{ factoryCode: '爱培科', warehouseCode: 'APK', warehouseName: '爱培科仓', enabled: true, qualityConfigured: true }, { factoryCode: '永惠', warehouseCode: 'YH', warehouseName: '永惠仓', enabled: true, qualityConfigured: true }])
  api.getBusinessWeeks.mockResolvedValue([{ businessMonth: '2026-12-01' }, { businessMonth: '2027-01-01' }])
  api.getCurrentBomStatus.mockResolvedValue({ localCount: 100 })
  api.getInventorySnapshots.mockImplementation(async (code?: string) => code === 'YH' ? [shared] : [shared, { id: 21, warehouseCode: 'APK', status: 'IMPORTED' }])
  api.getForecastMaterials.mockResolvedValue([{ factoryCode: '爱培科', months: { '2027-01': 1000 } }, { factoryCode: '永惠', months: { '2027-01': 600 } }])
  api.createBatch.mockResolvedValue({ id: 99 })
})
describe('one inventory version with factory selection', () => {
  it('only displays the three approved fields and requires an explicit inventory version', async () => {
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    expect(vm.factoryCode).toBe('ALL'); expect(vm.inventorySnapshotId).toBeNull(); expect(vm.ready).toBe(false)
    expect(dialog.findAll('.el-form-item__label').map(label => label.text())).toEqual(['工厂','经营计划','库存版本'])
    await vm.submit(); expect(api.createBatch).not.toHaveBeenCalled()
    dialog.unmount()
  })
  it.each(['ALL','爱培科','永惠'])('uses the same combined import for %s and lets the server freeze latest configuration', async factory => {
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    vm.factoryCode = factory; vm.inventorySnapshotId = 20; await flushPromises()
    expect(vm.ready).toBe(true); await vm.submit()
    expect(api.createBatch).toHaveBeenCalledWith(factory, 5, 20)
    expect(dialog.emitted('created')).toEqual([[99]])
    dialog.unmount()
  })
  it('validates warehouse coverage against import details and keeps the same version when changing factories', async () => {
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    vm.inventorySnapshotId = 21; await flushPromises()
    expect(vm.ready).toBe(false); expect(vm.missingWarehouses).toEqual(['YH'])
    vm.factoryCode = '爱培科'; await flushPromises()
    expect(vm.ready).toBe(true); expect(vm.inventorySnapshotId).toBe(21)
    vm.factoryCode = '永惠'; await flushPromises()
    expect(vm.ready).toBe(false); await vm.submit(); expect(api.createBatch).not.toHaveBeenCalled()
    dialog.unmount()
  })
  it('only requires calendar months belonging to the selected factory', async () => {
    api.getForecastMaterials.mockResolvedValue([{ factoryCode: '爱培科', months: { '2027-01': 1000 } }, { factoryCode: '永惠', months: { '2027-03': 600 } }])
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    vm.inventorySnapshotId = 20; await flushPromises(); expect(vm.ready).toBe(false)
    vm.factoryCode = '爱培科'; await flushPromises()
    expect(vm.ready).toBe(true); expect(vm.calendarMonths).toEqual(['2026-12','2027-01'])
    vm.factoryCode = '永惠'; await flushPromises()
    expect(vm.ready).toBe(false); expect(vm.calendarMonths).toEqual(['2027-02','2027-03'])
    dialog.unmount()
  })
  it('blocks creation while checking a newly selected forecast calendar', async () => {
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    vm.inventorySnapshotId = 20; await flushPromises(); expect(vm.ready).toBe(true)
    let resolve!: (value: unknown) => void
    api.getForecastMaterials.mockImplementation(() => new Promise(done => { resolve = done }))
    vm.forecastVersionId = 6; await flushPromises(); expect(vm.ready).toBe(false)
    resolve([{ factoryCode: '爱培科', months: { '2027-02': 1000 } }]); await flushPromises()
    expect(vm.ready).toBe(false); expect(vm.calendarMonths).toEqual(['2027-01','2027-02'])
    dialog.unmount()
  })
  it('blocks unavailable configurations and offers retry after a data loading failure', async () => {
    api.getInventorySnapshots.mockRejectedValue(new Error('库存服务暂不可用'))
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    expect(vm.ready).toBe(false); expect(dialog.text()).toContain('库存服务暂不可用'); expect(dialog.text()).toContain('重新加载')
    dialog.unmount()
  })
  it('blocks an unconfirmed warehouse quality before submitting the batch', async () => {
    api.getFactoryWarehouseMappings.mockResolvedValue([
      { factoryCode: '爱培科', warehouseCode: 'APK', warehouseName: '爱培科仓', enabled: true, qualityConfigured: false },
      { factoryCode: '永惠', warehouseCode: 'YH', warehouseName: '永惠仓', enabled: true, qualityConfigured: true }
    ])
    const dialog = await openDialog(); const vm = dialog.vm as unknown as DialogVm
    vm.inventorySnapshotId = 20; await flushPromises()
    expect(vm.ready).toBe(false)
    expect(dialog.text()).toContain('请先在仓库管理中确认良品属性：爱培科仓')
    await vm.submit(); expect(api.createBatch).not.toHaveBeenCalled()
    vm.factoryCode = '永惠'; await flushPromises()
    expect(vm.ready).toBe(true)
    dialog.unmount()
  })
})
