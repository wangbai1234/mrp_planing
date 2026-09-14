import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Permission } from '../types/permission'
import { getPermissionTree } from '../api/permission'

export const usePermissionStore = defineStore('permission', () => {
  const permissions = ref<string[]>([])
  const roles = ref<string[]>([])
  const permissionTree = ref<Record<number, Permission[]>>({})
  const loaded = ref(false)

  function setPermissions(perms: string[]) {
    permissions.value = perms
    loaded.value = true
  }

  function setRoles(roleList: string[]) {
    roles.value = roleList
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  function hasAnyPermission(codes: string[]): boolean {
    return codes.some(code => permissions.value.includes(code))
  }

  function hasAllPermissions(codes: string[]): boolean {
    return codes.every(code => permissions.value.includes(code))
  }

  async function loadPermissionTree() {
    try {
      permissionTree.value = await getPermissionTree()
    } catch (e) {
      console.error('Failed to load permission tree', e)
    }
  }

  function clear() {
    permissions.value = []
    roles.value = []
    permissionTree.value = {}
    loaded.value = false
  }

  return {
    permissions,
    roles,
    permissionTree,
    loaded,
    setPermissions,
    setRoles,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    loadPermissionTree,
    clear
  }
})
