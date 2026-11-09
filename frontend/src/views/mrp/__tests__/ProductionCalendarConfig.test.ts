import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElRadioGroup, ElDialog, ElInput, ElButton, ElDrawer, ElMessageBox } from 'element-plus'
import ProductionCalendarConfig from '../ProductionCalendarConfig.vue'

const mocks = vi.hoisted(() => ({
  manage: true,
  getFactories: vi.fn(), getCalendarMonth: vi.fn(), getCalendarPage: vi.fn(), getAuditLogs: vi.fn(),
  previewCalendarBatch: vi.fn(), saveCalendarBatch: vi.fn(), createProductionCalendar: vi.fn(), updateProductionCalendar: vi.fn(),
  deleteProductionCalendar: vi.fn(), downloadCalendarMonth: vi.fn(),
  previewProductionCalendarImport: vi.fn(), importProductionCalendar: vi.fn(), downloadProductionCalendarTemplate: vi.fn(), downloadProductionCalendarErrors: vi.fn()
}))
vi.mock('../../../api/mrp', () => ({ mrpApi: mocks }))
vi.mock('../../../api/audit', () => ({ getAuditLogs: mocks.getAuditLogs }))
vi.mock('../../../stores/permission', () => ({ usePermissionStore: () => ({ hasPermission: (permission: string) => permission !== 'mrp_calendar:manage' || mocks.manage }) }))
const month = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`
const row = (day: string, capacity = '1000', unit = '台') => ({ id: Number(day), factoryCode: '爱培科', calendarDate: `${month}-${day}`, productionDay: true, dailyCapacity: capacity, capacityUnit: unit, reason: '', revision: 0 })
function createWrapper() {
  return mount(ProductionCalendarConfig, { global: { plugins: [ElementPlus], stubs: {
    teleport: true, ConfigImportDialog: true,
    // Select's popper uses browser layout; selection itself is verified in the live browser.
    ElSelect: { props: ['modelValue'], template: '<div><slot /></div>' },
    ElOption: true,
  } } })
}

beforeEach(() => {
  vi.clearAllMocks(); mocks.manage = true
  mocks.getFactories.mockResolvedValue(['爱培科', '永惠'])
  mocks.getCalendarMonth.mockResolvedValue([row('01'), row('02')])
  mocks.getCalendarPage.mockResolvedValue({ items: [row('02')], total: 31, page: 1, pageSize: 20 })
  mocks.getAuditLogs.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 })
  mocks.previewCalendarBatch.mockResolvedValue({ inserted: 2, updated: 0, preserved: 20, fingerprint: 'frozen-preview', rows: [row('03')] })
})

describe('production calendar', () => {
  it('sums only valid machine capacity and marks legacy units for maintenance', async () => {
    mocks.getCalendarMonth.mockResolvedValue([row('01', '900719999999.000000'), row('02', '1.000000'), row('03', '2.500000', '件'), row('04', '100', '')])
    const wrapper = createWrapper(); await flushPromises()
    expect(wrapper.find('.summary-row').text()).toContain('900720000000 台')
    const cell = wrapper.findAll('.day-cell').find(item => item.text().startsWith('04'))
    expect(cell?.text()).toContain('未配置')
    expect(wrapper.text()).toContain('原配置 2.5 件，需重新维护')
    expect(wrapper.text()).not.toContain('.000000')
    expect(wrapper.findAllComponents(ElButton).some(button => button.text() === '导出')).toBe(false)
    expect(mocks.getFactories).toHaveBeenCalled()
    expect(mocks.getCalendarPage).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('renders the server page rather than paginating monthly rows in the browser', async () => {
    const wrapper = createWrapper(); await flushPromises()
    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'list'); await flushPromises()
    expect(mocks.getCalendarPage).toHaveBeenLastCalledWith(expect.objectContaining({ factoryCode: '爱培科', month, page: 1, pageSize: 20 }))
    expect(wrapper.findAll('.el-table__body-wrapper tbody tr')).toHaveLength(1)
    expect(wrapper.find('.el-table__body-wrapper').text()).toContain(`${month}-02`)
    wrapper.unmount()
  })

  it('opens row logs independently and deletes the selected row after confirmation', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel')
    mocks.getAuditLogs.mockResolvedValue({ items: [{ id: 1, created_at: '2026-10-22T09:00:00', operator_name: '管理员', action: 'UPDATE', resource_type: 'mrp_production_calendar', before_value: '{"dailyCapacity":800}', after_value: '{"dailyCapacity":1000}' }], total: 1, page: 1, pageSize: 20 })
    const wrapper = createWrapper(); await flushPromises()
    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'list'); await flushPromises()
    const action = (label: string) => wrapper.find('.el-table__body-wrapper').findAllComponents(ElButton).find(button => button.text() === label)!
    await action('查看日志').trigger('click'); await flushPromises()
    expect(mocks.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ resourceType: 'mrp_production_calendar', resourceId: `爱培科|${month}-02` }))
    const logDialog = wrapper.findAllComponents(ElDialog).find(dialog => String(dialog.props('title')).startsWith('查看日志'))!
    expect(logDialog.text()).toContain(`${month}-02`)
    expect(logDialog.text()).toContain('管理员')
    expect(wrapper.findComponent(ElDrawer).props('modelValue')).toBe(false)
    logDialog.vm.$emit('update:modelValue', false); await flushPromises()
    await action('编辑').trigger('click'); await flushPromises()
    const drawer = wrapper.findComponent(ElDrawer)
    expect(drawer.text()).not.toContain('修改记录')
    expect(drawer.findAllComponents(ElButton).some(button => button.text() === '删除')).toBe(false)
    expect(mocks.getAuditLogs).toHaveBeenCalledTimes(1)
    drawer.vm.$emit('update:modelValue', false); await flushPromises()
    await action('删除').trigger('click'); await flushPromises()
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining(`${month}-02`), '删除日期配置', expect.any(Object))
    expect(mocks.deleteProductionCalendar).not.toHaveBeenCalled()
    confirm.mockResolvedValueOnce('confirm')
    await action('删除').trigger('click'); await flushPromises()
    expect(mocks.deleteProductionCalendar).toHaveBeenCalledWith(2)
    expect(mocks.getCalendarPage).toHaveBeenCalledTimes(2)
    confirm.mockRestore(); wrapper.unmount()
  })

  it('keeps list logs accessible to viewers while hiding deletion', async () => {
    mocks.manage = false
    const wrapper = createWrapper(); await flushPromises()
    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'list'); await flushPromises()
    const buttons = wrapper.find('.el-table__body-wrapper').findAllComponents(ElButton)
    expect(buttons.map(button => button.text())).toEqual(['查看日志', '查看'])
    await buttons[0]!.trigger('click'); await flushPromises()
    expect(mocks.getAuditLogs).toHaveBeenCalledWith(expect.objectContaining({ resourceType: 'mrp_production_calendar', resourceId: `爱培科|${month}-02` }))
    expect(wrapper.findAllComponents(ElDialog).find(dialog => String(dialog.props('title')).startsWith('查看日志'))!.text()).toContain('暂无操作记录')
    wrapper.unmount()
  })

  it('invalidates a batch preview when inputs change and commits its fingerprint', async () => {
    const wrapper = createWrapper(); await flushPromises()
    await wrapper.findAllComponents(ElButton).find(button => button.text() === '新增')!.trigger('click'); await flushPromises()
    const dialog = wrapper.findAllComponents(ElDialog).find(dialog => dialog.props('title') === '批量设置生产日历')!
    const capacityInput = dialog.findAllComponents(ElInput).find(input => input.props('placeholder') === '正整数（台/日）')!
    capacityInput.vm.$emit('update:modelValue', '1000')
    await flushPromises()
    await dialog.findAllComponents(ElButton).find(button => button.text() === '预览')!.trigger('click'); await flushPromises()
    expect(mocks.previewCalendarBatch).toHaveBeenCalledWith(expect.objectContaining({ overwrite: false, dailyCapacity: '1000', capacityUnit: '台' }))
    const confirm = () => dialog.findAllComponents(ElButton).find(button => button.text() === '确认')!
    expect(confirm().props('disabled')).toBe(false)
    capacityInput.vm.$emit('update:modelValue', '800'); await flushPromises()
    expect(confirm().props('disabled')).toBe(true)
    await dialog.findAllComponents(ElButton).find(button => button.text() === '预览')!.trigger('click'); await flushPromises()
    await confirm().trigger('click'); await flushPromises()
    expect(mocks.saveCalendarBatch).toHaveBeenCalledWith(expect.objectContaining({ dailyCapacity: '800', expectedFingerprint: 'frozen-preview' }))
    wrapper.unmount()
  })

  it('requires confirmation before saving a legacy unit as machine capacity', async () => {
    mocks.getCalendarMonth.mockResolvedValue([row('01', '100.000000', '件')])
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel')
    const wrapper = createWrapper(); await flushPromises()
    await wrapper.find('[aria-label="查看 ' + month + '-01 配置"]').trigger('click'); await flushPromises()
    const drawer = wrapper.findComponent(ElDrawer)
    expect(drawer.text()).toContain('原配置 100 件，需重新维护')
    expect(drawer.findAllComponents(ElInput).find(input => input.props('modelValue') === '台')!.props('disabled')).toBe(true)
    const save = drawer.findAllComponents(ElButton).find(button => button.text() === '确认')!
    await save.trigger('click'); await flushPromises()
    expect(mocks.updateProductionCalendar).not.toHaveBeenCalled()
    confirm.mockResolvedValueOnce('confirm')
    await save.trigger('click'); await flushPromises()
    expect(mocks.updateProductionCalendar).toHaveBeenCalledWith(1, expect.objectContaining({capacityUnit: '台', dailyCapacity: '100'}))
    confirm.mockRestore(); wrapper.unmount()
  })

  it('lets viewers inspect a day but hides editing and importing actions', async () => {
    mocks.manage = false
    const wrapper = createWrapper(); await flushPromises()
    expect(wrapper.findAllComponents(ElButton).some(button => ['新增', '导入'].includes(button.text()))).toBe(false)
    await wrapper.find('[aria-label="查看 ' + month + '-01 配置"]').trigger('click'); await flushPromises()
    expect(wrapper.findComponent(ElDrawer).text()).toContain('日期详情')
    expect(wrapper.findComponent(ElDrawer).findAllComponents(ElButton).some(button => button.text() === '确认')).toBe(false)
    expect(mocks.getAuditLogs).toHaveBeenCalledWith(expect.objectContaining({ resourceType: 'mrp_production_calendar', resourceId: `爱培科|${month}-01` }))
    wrapper.unmount()
  })
})
