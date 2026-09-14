import { get } from './client'
import type { Permission } from '../types/permission'

export function getPermissions() {
  return get<Permission[]>('/permissions')
}

export function getPermissionTree() {
  return get<Record<number, Permission[]>>('/permissions/tree')
}
