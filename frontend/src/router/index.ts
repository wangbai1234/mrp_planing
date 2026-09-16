import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/client'
import { usePermissionStore } from '../stores/permission'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
    { path: '/', redirect: '/planning' },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/dashboard/DashboardView.vue'),
      meta: { permission: 'schedule:view' }
    },
    {
      path: '/planning',
      name: 'planning',
      component: () => import('../views/planning/PlanningGrid.vue'),
      meta: { permission: 'schedule:view' }
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
      meta: { permission: 'schedule:view' }
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
      meta: { permission: 'schedule:view' }
    },
    {
      path: '/capacity',
      name: 'capacity',
      component: () => import('../views/capacity/CapacityConfig.vue'),
      meta: { permission: 'capacity:view' }
    },
    {
      path: '/manual',
      name: 'manual',
      component: () => import('../views/manual/ManualView.vue')
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
    }
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
  if (to.name === 'login') {
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
      // Get user info from token/cache
      const userInfoStr = localStorage.getItem('mrp_user_info')
      if (userInfoStr) {
        const userInfo: LoginResponse = JSON.parse(userInfoStr)
        permissionStore.setPermissions(userInfo.permissions || [])
        permissionStore.setRoles(userInfo.roles || [])
      }
    } catch (e) {
      console.error('Failed to load permissions', e)
    }
  }

  // Check page permission
  const requiredPermission = to.meta.permission as string | undefined
  if (requiredPermission && !permissionStore.hasPermission(requiredPermission)) {
    // Redirect to dashboard if no permission
    next({ name: 'dashboard' })
    return
  }

  next()
})

export default router
