import { describe, expect, it } from 'vitest'
import { defaultPage, menuItems, permissionGrouping, visibleMenu } from '../menu'
import type { Permission } from '../../types/permission'

function permission(partial: Pick<Permission, 'id' | 'code'> & Partial<Permission>): Permission {
  return { name: partial.code, type: 'BUTTON', parentId: null, sort: 0, status: 'ACTIVE', createdAt: '', updatedAt: '', ...partial }
}

// The real parent chains, flattened to what the permission page has to place.
const catalog: Permission[] = [
  permission({ id: 1, code: 'mrp', type: 'MENU' }),
  permission({ id: 2, code: 'schedule', type: 'MENU', parentId: 1 }),
  permission({ id: 7, code: 'schedule:plan', type: 'PAGE', parentId: 2 }),
  permission({ id: 16, code: 'schedule:view', parentId: 7 }),
  permission({ id: 3, code: 'forecast', type: 'MENU', parentId: 1 }),
  permission({ id: 9, code: 'forecast:list', type: 'PAGE', parentId: 3 }),
  permission({ id: 17, code: 'forecast:view', parentId: 9 }),
  permission({ id: 5, code: 'capacity', type: 'MENU', parentId: 1 }),
  permission({ id: 11, code: 'capacity:list', type: 'PAGE', parentId: 5 }),
  permission({ id: 18, code: 'capacity:view', parentId: 11 }),
  permission({ id: 19, code: 'capacity:create', parentId: 11 }),
  permission({ id: 30, code: 'mrp_workspace', type: 'MENU', parentId: 1 }),
  permission({ id: 31, code: 'mrp_workspace:view', type: 'PAGE', parentId: 30 }),
  permission({ id: 32, code: 'mrp_batch', type: 'MENU', parentId: 1 }),
  permission({ id: 33, code: 'mrp_batch:list', type: 'PAGE', parentId: 32 }),
  permission({ id: 34, code: 'mrp_batch:create', parentId: 33 }),
  permission({ id: 40, code: 'mrp_config', type: 'MENU', parentId: 1 }),
  permission({ id: 41, code: 'mrp_calendar:view', type: 'PAGE', parentId: 40 }),
  permission({ id: 42, code: 'mrp_calendar:manage', parentId: 41 }),
  permission({ id: 50, code: 'user', type: 'MENU', parentId: 1 }),
  permission({ id: 51, code: 'user:list', type: 'PAGE', parentId: 50 }),
  permission({ id: 52, code: 'user:view', parentId: 51 }),
  permission({ id: 53, code: 'audit', type: 'MENU', parentId: 1 }),
  permission({ id: 54, code: 'audit:list', type: 'PAGE', parentId: 53 }),
  permission({ id: 55, code: 'audit:view', parentId: 54 })
]

function sectionOf(pageCode: string): string | undefined {
  return permissionGrouping().prefixes.find(entry => pageCode === entry.prefix || pageCode.startsWith(`${entry.prefix}:`))?.section
}

describe('permission grouping', () => {
  it('places every page permission into exactly one menu section', () => {
    const placed = catalog.filter(item => item.type === 'PAGE').map(item => [item.code, sectionOf(item.code)])
    expect(placed).toEqual([
      ['schedule:plan', 'mrp_workspace'],
      ['forecast:list', 'forecast'],
      ['capacity:list', 'mrp_config'],
      ['mrp_workspace:view', 'mrp_workspace'],
      ['mrp_batch:list', 'mrp_workspace'],
      ['mrp_calendar:view', 'mrp_calendar'],
      ['user:list', 'user'],
      ['audit:list', 'user']
    ])
  })

  it('never lets one permission match two sections', () => {
    const { prefixes } = permissionGrouping()
    for (const item of catalog) {
      const matches = prefixes.filter(entry => item.code === entry.prefix || item.code.startsWith(`${entry.prefix}:`))
      expect(matches.slice(0, 1)).toEqual(matches.filter(entry => entry.prefix === matches[0]?.prefix))
    }
  })

  it('picks up a newly added page without any other change', () => {
    const added = permission({ id: 90, code: 'quality:list', type: 'PAGE' })
    menuItems.push({ code: 'quality', label: '质检', path: '/quality', icon: 'Files', group: '基础信息', order: 40, permission: 'quality:view' })
    try {
      expect(sectionOf(added.code)).toBe('quality')
      expect(visibleMenu(['quality:view']).flatMap(group => group.items).map(item => item.code)).toContain('quality')
      expect(visibleMenu(['forecast:view']).flatMap(group => group.items).map(item => item.code)).not.toContain('quality')
    } finally {
      menuItems.pop()
    }
  })
})

describe('visible menu', () => {
  it('opens the first authorized page for each independent workbench role', () => {
    expect(defaultPage(['mrp_workspace:view', 'mrp_schedule:confirm'])).toBe('/planning')
    expect(defaultPage(['mrp_shortage:view', 'mrp_shortage:manage'])).toBe('/shortage')
    expect(defaultPage(['user:view'])).toBe('/admin/users')
    expect(defaultPage([])).toBe('/waiting-permission')
  })
  it('keeps the confirmed master data and base rule menu order', () => {
    expect(visibleMenu(menuItems.map(item => item.permission).filter((code): code is string => Boolean(code)))
      .filter(group => ['基础信息', '基础规则'].includes(group.group))
      .map(group => [group.group, group.items.map(item => item.label)])).toEqual([
        ['基础信息', ['工厂管理', '仓库管理', '物料管理', '物料分类', 'BOM管理']],
        ['基础规则', ['物料策略', '库存控制策略', '生产日历', '业务周期间']]
      ])
    expect(visibleMenu(['mrp_factory:view']).flatMap(group => group.items).map(item => item.path)).toEqual(['/mrp/factories'])
    expect(visibleMenu(['mrp_warehouse_mapping:view']).flatMap(group => group.items).map(item => item.path)).toEqual(['/mrp/warehouses'])
  })

  it('shows a page once its view permission is granted', () => {
    const codes = visibleMenu(['forecast:view', 'inventory:view']).flatMap(group => group.items).map(item => item.code)
    expect(codes).toEqual(['forecast', 'inventory'])
  })

  it('shows a page that has no single gate when any of its permissions is granted', () => {
    expect(visibleMenu(['mrp_calendar:view']).flatMap(group => group.items).map(item => item.label)).toEqual(['生产日历'])
    expect(visibleMenu(['capacity:view']).flatMap(group => group.items).map(item => item.label)).toEqual([])
    expect(visibleMenu(['audit:view']).flatMap(group => group.items).map(item => item.label)).toEqual(['操作日志'])
    expect(visibleMenu(['role:view', 'role:assign_permission']).flatMap(group => group.items).map(item => item.path))
      .toEqual(['/admin/roles'])
  })

  it('drops groups the user cannot see', () => {
    expect(visibleMenu([])).toEqual([])
  })
})
