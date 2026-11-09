export interface MenuItem {
  // Permission code prefix. The permission page groups every permission whose
  // code starts with `${code}:` under this item, so a new page only needs one entry here.
  code: string
  label: string
  path: string
  icon: string
  group: string
  order: number
  // Permission required to see the item. Omit for pages every signed-in user can open.
  permission?: string
  // Extra permission prefixes that belong to this page even though they do not
  // share `code`, e.g. batch and decision actions that live inside 排产计划.
  permissionPrefixes?: string[]
}

export const MENU_GROUPS = ['计划', '基础信息', '基础规则', '系统']

// The single source for the sidebar and the permission-configuration page.
// Adding a page means adding one entry; both places derive from it.
export const menuItems: MenuItem[] = [
  { code: 'mrp_workspace', label: '排产计划', path: '/planning', icon: 'Calendar', group: '计划', order: 10, permission: 'mrp_workspace:view', permissionPrefixes: ['mrp_batch', 'mrp_plan', 'mrp_history', 'schedule'] },
  { code: 'mrp_shortage', label: '物料缺料', path: '/shortage', icon: 'Box', group: '计划', order: 15, permission: 'mrp_shortage:view', permissionPrefixes: ['mrp_decision', 'mrp_inventory'] },
  { code: 'mrp_execution', label: '生产执行', path: '/execution', icon: 'Calendar', group: '计划', order: 18, permission: 'mrp_execution:confirm' },
  { code: 'forecast', label: '经营计划', path: '/forecast', icon: 'Document', group: '计划', order: 20, permission: 'forecast:view' },
  { code: 'inventory', label: '库存快照', path: '/inventory', icon: 'Box', group: '计划', order: 30, permission: 'inventory:view' },
  { code: 'mrp_factory', label: '工厂管理', path: '/mrp/factories', icon: 'OfficeBuilding', group: '基础信息', order: 10, permission: 'mrp_factory:view' },
  { code: 'mrp_warehouse_mapping', label: '仓库管理', path: '/mrp/warehouses', icon: 'House', group: '基础信息', order: 20, permission: 'mrp_warehouse_mapping:view' },
  { code: 'material', label: '物料管理', path: '/material', icon: 'Files', group: '基础信息', order: 30, permission: 'material:view' },
  { code: 'material_category', label: '物料分类', path: '/material-category', icon: 'Folder', group: '基础信息', order: 40, permission: 'material_category:view' },
  { code: 'bom', label: 'BOM管理', path: '/bom', icon: 'Share', group: '基础信息', order: 50, permission: 'bom:view' },
  { code: 'mrp_config', label: '物料策略', path: '/mrp/material-policies', icon: 'SetUp', group: '基础规则', order: 10, permission: 'mrp_config:view', permissionPrefixes: ['capacity'] },
  { code: 'mrp_stock_policy', label: '库存控制策略', path: '/mrp/stock-policies', icon: 'Box', group: '基础规则', order: 20, permission: 'mrp_stock_policy:view' },
  { code: 'mrp_calendar', label: '生产日历', path: '/mrp/production-calendars', icon: 'Calendar', group: '基础规则', order: 30, permission: 'mrp_calendar:view' },
  { code: 'mrp_business_week', label: '业务周期间', path: '/mrp/business-weeks', icon: 'Date', group: '基础规则', order: 40, permission: 'mrp_business_week:view' },
  { code: 'user', label: '用户与权限', path: '/admin/users', icon: 'UserFilled', group: '系统', order: 10, permissionPrefixes: ['role', 'permission', 'audit', 'system'] }
]

function itemsIn(group: string): MenuItem[] {
  return menuItems.filter(item => item.group === group)
}

export interface MenuGroup {
  group: string
  items: MenuItem[]
}

// Sidebar entries the granted permission codes allow. A group with nothing visible is dropped.
export function visibleMenu(granted: string[]): MenuGroup[] {
  return MENU_GROUPS.flatMap(group => {
    const items = itemsIn(group)
      .filter(item => isVisible(item, granted))
      .sort((a, b) => a.order - b.order)
      .map(item => {
        if (item.code !== 'user' || granted.includes('user:view')) return item
        if (granted.includes('role:view')) return { ...item, path: '/admin/roles', label: '角色管理' }
        if (granted.includes('audit:view')) return { ...item, path: '/admin/audit-logs', label: '操作日志' }
        return item
      })
    return items.length ? [{ group, items }] : []
  })
}

export function defaultPage(granted: string[]): string {
  const firstPage = visibleMenu(granted).flatMap(group => group.items)
    .find(item => item.permission && granted.includes(item.permission))
  if (firstPage) return firstPage.path
  if (granted.includes('user:view')) return '/admin/users'
  if (granted.includes('role:view')) return '/admin/roles'
  if (granted.includes('audit:view')) return '/admin/audit-logs'
  return '/waiting-permission'
}

function isVisible(item: MenuItem, granted: string[]): boolean {
  if (item.permission) {
    return granted.includes(item.permission)
  }
  // No single gate permission: visible when the user holds any permission of this page.
  // Retain historical capacity permissions in the administration grouping only.
  const prefixes = [item.code, ...(item.permissionPrefixes ?? [])].filter(prefix => prefix !== 'capacity')
  return granted.some(code => prefixes.some(prefix => code === prefix || code.startsWith(`${prefix}:`)))
}

export interface PermissionSection {
  code: string
  label: string
  order: number
}

export interface PermissionGrouping {
  groups: { group: string; sections: PermissionSection[] }[]
  // Longest prefix first, so `material_category` wins over `material`.
  prefixes: { prefix: string; section: string }[]
}

// What the permission page needs to lay permissions out the same way as the menu.
export function permissionGrouping(): PermissionGrouping {
  const groups = MENU_GROUPS.flatMap(group => {
    const sections = itemsIn(group)
      .sort((a, b) => a.order - b.order)
      .map(item => ({ code: item.code, label: item.label, order: item.order }))
    return sections.length ? [{ group, sections }] : []
  })

  const prefixes = menuItems
    .flatMap(item => [item.code, ...(item.permissionPrefixes ?? [])].map(prefix => ({ prefix, section: item.code })))
    .sort((a, b) => b.prefix.length - a.prefix.length)

  return { groups, prefixes }
}
