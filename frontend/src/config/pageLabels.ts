const PAGE_LABELS: Record<string, string> = {
  '/dashboard': '工作台',
  '/planning': '排产计划',
  '/shortage': '物料缺料',
  '/execution': '生产执行',
  '/forecast': '经营计划',
  '/inventory': '库存快照',
  '/material': '物料管理',
  '/material-category': '物料分类',
  '/bom': 'BOM管理',
  '/capacity': '产能配置',
  '/mrp/material-policies': '物料策略',
  '/mrp/production-calendars': '生产日历',
  '/mrp/config': '基础规则',
  '/mrp/factories': '工厂管理',
  '/mrp/warehouses': '仓库管理',
  '/mrp/factory-warehouse-mappings': '仓库管理',
  '/mrp/stock-policies': '库存控制策略',
  '/mrp/business-weeks': '业务周期间',
  '/versions': '版本记录',
  '/export': '导出',
  '/mrp/batches': '排产计划',
  '/mrp/workspace': '排产计划',
  '/admin/users': '用户与权限',
  '/admin/roles': '角色管理',
  '/admin/audit-logs': '操作日志',
  '/login': '登录',
  '/login/callback': '登录中',
  '/waiting-permission': '等待权限',
  '/forbidden': '无访问权限'
}

export function getPageLabel(path: string): string {
  if (path === '/' || path === '') {
    return ''
  }

  if (PAGE_LABELS[path]) {
    return PAGE_LABELS[path]
  }

  if (path.startsWith('/admin/roles/') && path.endsWith('/permissions')) {
    return '权限配置'
  }

  const batchMatch = path.match(/^\/(?:planning|mrp\/workspace|mrp\/batches)\/(\d+)$/)
  if (batchMatch) {
    return `排产计划 #${batchMatch[1]}`
  }

  const shortageMatch = path.match(/^\/shortage\/(\d+)$/)
  if (shortageMatch) {
    return `物料缺料 #${shortageMatch[1]}`
  }

  const segments = path.split('/').filter(Boolean)
  const last = segments[segments.length - 1]
  // Bare ids have no readable name; skip them instead of showing a raw number.
  return last && !/^\d+$/.test(last) ? last : ''
}
