import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePermissionStore } from '@/stores/permission'

describe('Permission Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should initialize with empty permissions', () => {
    const store = usePermissionStore()
    expect(store.permissions).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.loaded).toBe(false)
  })

  it('should set permissions correctly', () => {
    const store = usePermissionStore()
    const perms = ['user:view', 'user:create', 'schedule:view']
    
    store.setPermissions(perms)
    
    expect(store.permissions).toEqual(perms)
    expect(store.loaded).toBe(true)
  })

  it('should set roles correctly', () => {
    const store = usePermissionStore()
    const roles = ['ADMIN', 'MANAGER']
    
    store.setRoles(roles)
    
    expect(store.roles).toEqual(roles)
  })

  it('should check single permission correctly', () => {
    const store = usePermissionStore()
    store.setPermissions(['user:view', 'user:create'])
    
    expect(store.hasPermission('user:view')).toBe(true)
    expect(store.hasPermission('user:delete')).toBe(false)
  })

  it('should check any permission correctly', () => {
    const store = usePermissionStore()
    store.setPermissions(['user:view', 'user:create'])
    
    expect(store.hasAnyPermission(['user:view', 'user:delete'])).toBe(true)
    expect(store.hasAnyPermission(['user:delete', 'user:update'])).toBe(false)
  })

  it('should check all permissions correctly', () => {
    const store = usePermissionStore()
    store.setPermissions(['user:view', 'user:create', 'user:update'])
    
    expect(store.hasAllPermissions(['user:view', 'user:create'])).toBe(true)
    expect(store.hasAllPermissions(['user:view', 'user:delete'])).toBe(false)
  })

  it('should clear store correctly', () => {
    const store = usePermissionStore()
    store.setPermissions(['user:view'])
    store.setRoles(['ADMIN'])
    
    store.clear()
    
    expect(store.permissions).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.loaded).toBe(false)
  })
})
