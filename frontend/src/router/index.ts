import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/client'
import { usePermissionStore } from '../stores/permission'
import { get } from '../api/client'
import { defaultPage } from '../config/menu'
import { getPageLabel } from '../config/pageLabels'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
    { path: '/login/callback', name: 'feishu-callback', component: () => import('../views/login/FeishuCallback.vue') },
    { path: '/forbidden', name: 'forbidden', component: () => import('../views/login/ForbiddenView.vue') },
    { path: '/', redirect: '/planning' },
    {
      path: '/dashboard',
      name: 'dashboard',
      redirect: '/planning',
      meta: { permission: 'mrp_workspace:view' }
    },
    {
      path: '/planning',
      name: 'planning',
      component: () => import('../views/mrp/MrpWorkspace.vue'),
      meta: { permission: 'mrp_workspace:view' }
    },
    {
      path: '/planning/:id',
      name: 'planning-batch',
      component: () => import('../views/mrp/MrpWorkspace.vue'),
      meta: { permission: 'mrp_workspace:view' }
    },
    {
      path: '/shortage',
      name: 'shortage',
      component: () => import('../views/mrp/ShortageWorkspace.vue'),
      meta: { permission: 'mrp_shortage:view' }
    },
    {
      path: '/shortage/:id',
      name: 'shortage-batch',
      component: () => import('../views/mrp/ShortageWorkspace.vue'),
      meta: { permission: 'mrp_shortage:view' }
    },
    {
      path: '/execution',
      name: 'execution',
      component: () => import('../views/mrp/ExecutionWorkspace.vue'),
      meta: { permission: 'mrp_execution:confirm' }
    },
    {
      path: '/forecast',
      name: 'forecast',
      component: () => import('../views/forecast/ForecastImport.vue'),
      meta: { permission: 'forecast:view' }
    },
    {
      path: '/inventory',
      name: 'inventory',
      component: () => import('../views/inventory/InventoryImport.vue'),
      meta: { permission: 'inventory:view' }
    },
    {
      path: '/material',
      name: 'material',
      component: () => import('../views/material/MaterialView.vue'),
      meta: { permission: 'material:view' }
    },
    {
      path: '/material-category',
      name: 'material-category',
      component: () => import('../views/material/MaterialCategoryView.vue'),
      meta: { permission: 'material_category:view' }
    },
    {
      path: '/bom',
      name: 'bom',
      component: () => import('../views/bom/BomView.vue'),
      meta: { permission: 'bom:view' }
    },
    {
      path: '/capacity',
      name: 'capacity',
      redirect: '/mrp/production-calendars'
    },
    {
      path: '/mrp/config',
      name: 'mrp-config',
      component: () => import('../views/mrp/MrpConfigurationCenter.vue')
    },
    {
      path: '/mrp/factory-warehouse-mappings',
      name: 'mrp-factory-warehouse-mappings',
      redirect: '/mrp/warehouses'
    },
    {
      path: '/mrp/factories',
      name: 'mrp-factories',
      component: () => import('../views/mrp/FactoryMasterConfig.vue'),
      meta: { permission: 'mrp_factory:view' }
    },
    {
      path: '/mrp/warehouses',
      name: 'mrp-warehouses',
      component: () => import('../views/mrp/FactoryWarehouseMappingConfig.vue'),
      meta: { permission: 'mrp_warehouse_mapping:view' }
    },
    {
      path: '/mrp/stock-policies',
      name: 'mrp-stock-policies',
      component: () => import('../views/mrp/StockPolicyConfig.vue'),
      meta: { permission: 'mrp_stock_policy:view' }
    },
    {
      path: '/mrp/business-weeks',
      name: 'mrp-business-weeks',
      component: () => import('../views/mrp/BusinessWeekConfig.vue'),
      meta: { permission: 'mrp_business_week:view' }
    },
    {
      path: '/mrp/material-policies',
      name: 'mrp-material-policies',
      component: () => import('../views/mrp/MaterialPolicyConfig.vue'),
      meta: { permission: 'mrp_config:view' }
    },
    {
      path: '/mrp/production-calendars',
      name: 'mrp-production-calendars',
      component: () => import('../views/mrp/ProductionCalendarConfig.vue'),
      meta: { permission: 'mrp_calendar:view' }
    },
    {
      path: '/waiting-permission',
      name: 'waiting-permission',
      component: () => import('../views/login/WaitingPermission.vue')
    },
    {
      path: '/versions',
      name: 'versions',
      component: () => import('../views/versions/VersionList.vue'),
      meta: { permission: 'schedule:view' }
    },
    {
      path: '/export',
      name: 'export',
      component: () => import('../views/export/ExportConfig.vue'),
      meta: { permission: 'schedule:export' }
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('../views/admin/UserList.vue'),
      meta: { permission: 'user:view' }
    },
    {
      path: '/admin/roles',
      name: 'admin-roles',
      component: () => import('../views/admin/RoleList.vue'),
      meta: { permission: 'role:view' }
    },
    {
      path: '/admin/roles/:roleId/permissions',
      alias: '/roles/:roleId/permissions',
      name: 'admin-role-permissions',
      component: () => import('../views/admin/PermissionConfig.vue'),
      meta: { permission: 'role:assign_permission' },
      props: true
    },
    {
      path: '/admin/audit-logs',
      name: 'admin-audit-logs',
      component: () => import('../views/admin/AuditLog.vue'),
      meta: { permission: 'audit:view' }
    },
    {
      path: '/mrp/batches',
      name: 'mrp-batches',
      redirect: '/planning',
      meta: { permission: 'mrp_workspace:view' }
    },
    { path: '/mrp/batches/:id', redirect: to => `/planning/${String(to.params.id)}` },
    { path: '/mrp/workspace', redirect: '/planning' },
    { path: '/mrp/workspace/:id', redirect: to => `/planning/${String(to.params.id)}` }
  ]
})

interface LoginResponse {
  token: string
  userId: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

router.beforeEach(async (to, _from, next) => {
  if (to.name === 'login' || to.name === 'feishu-callback' || to.name === 'waiting-permission' || to.name === 'forbidden') {
    next()
    return
  }

  const token = getToken()
  if (!token) {
    next({ name: 'login' })
    return
  }

  // Load permissions if not loaded
  const permissionStore = usePermissionStore()
  if (!permissionStore.loaded) {
    try {
      const current = await get<Pick<LoginResponse, 'permissions' | 'roles'>>('/auth/me')
      permissionStore.setPermissions(current.permissions || [])
      permissionStore.setRoles(current.roles || [])
      const cached = localStorage.getItem('mrp_user_info')
      if (cached) localStorage.setItem('mrp_user_info', JSON.stringify({
        ...JSON.parse(cached), permissions: current.permissions, roles: current.roles
      }))
    } catch (e) {
      console.error('Failed to load permissions', e)
      next({ name: 'login' })
      return
    }
  }

  // Check page permission
  const requiredPermission = to.meta.permission as string | undefined
  const alternativePermissions = to.meta.permissions as string[] | undefined
  const denied = (requiredPermission && !permissionStore.hasPermission(requiredPermission)) ||
    (alternativePermissions && !permissionStore.hasAnyPermission(alternativePermissions))
  if (denied) {
    if (to.redirectedFrom?.path === '/') {
      next(defaultPage(permissionStore.permissions))
      return
    }
    // 如果用户没有任何权限，跳转到等待权限页面
    if (permissionStore.permissions.length === 0) {
      next({ name: 'waiting-permission' })
      return
    }
    next({ name: 'forbidden', query: { from: to.fullPath } })
    return
  }

  next()
})

router.afterEach(to => {
  const label = getPageLabel(to.path)
  document.title = label ? `${label} | MRP 计划协同` : 'MRP 计划协同'
})

export default router
