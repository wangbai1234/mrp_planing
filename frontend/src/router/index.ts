import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/client'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
    { path: '/', redirect: '/planning' },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/dashboard/DashboardView.vue') },
    { path: '/planning', name: 'planning', component: () => import('../views/planning/PlanningGrid.vue') },
    { path: '/forecast', name: 'forecast', component: () => import('../views/forecast/ForecastImport.vue') },
    { path: '/inventory', name: 'inventory', component: () => import('../views/inventory/InventoryImport.vue') },
    { path: '/material', name: 'material', component: () => import('../views/material/MaterialView.vue') },
    { path: '/bom', name: 'bom', component: () => import('../views/bom/BomView.vue') },
    { path: '/capacity', name: 'capacity', component: () => import('../views/capacity/CapacityConfig.vue') },
    { path: '/versions', name: 'versions', component: () => import('../views/versions/VersionList.vue') },
    { path: '/manual', name: 'manual', component: () => import('../views/manual/ManualView.vue') },
  ]
})

router.beforeEach((to, _from, next) => {
  const token = getToken()
  if (to.name !== 'login' && !token) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
