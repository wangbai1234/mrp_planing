import { get, post, put, del } from './client'
import type { Role } from '../types/permission'

export function getRoles() {
  return get<Role[]>('/roles')
}

export function getRolePage(keyword: string, page: number, pageSize: number) {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (keyword.trim()) query.set('keyword', keyword.trim())
  return get<{ items: Role[]; total: number; page: number; pageSize: number }>(`/roles/page?${query}`)
}

export function getRoleMembers(roleId: number, page: number) {
  return get<{ items: import('../types/permission').User[]; total: number }>(`/roles/${roleId}/members?page=${page}&pageSize=20`)
}

export function getActiveRoles() {
  return get<Role[]>('/roles/active')
}

export function getRole(id: number) {
  return get<Role>(`/roles/${id}`)
}

export function createRole(data: { code: string; name: string; description?: string }) {
  return post<Role>('/roles', data)
}

export function updateRole(id: number, data: { name?: string; description?: string; status?: string }) {
  return put<Role>(`/roles/${id}`, data)
}

export function deleteRole(id: number) {
  return del<void>(`/roles/${id}`)
}

export function getRolePermissions(id: number) {
  return get<number[]>(`/roles/${id}/permissions`)
}

export function updateRolePermissions(id: number, permissionIds: number[]) {
  return put<void>(`/roles/${id}/permissions`, { permissionIds })
}

export interface RoleDataScope {
  scopeType: 'ALL' | 'FACTORY' | 'NONE'
  factoryCodes: string[]
}

export function getRoleFactoryOptions() {
  return get<string[]>('/roles/factories')
}

export function getRoleDataScope(id: number) {
  return get<RoleDataScope>(`/roles/${id}/data-scope`)
}

export function updateRoleConfiguration(id: number, permissionIds: number[], dataScope: RoleDataScope) {
  return put<void>(`/roles/${id}/configuration`, { permissionIds, dataScope })
}

export function copyRole(id: number, data: { name: string; code: string }) {
  return post<Role>(`/roles/${id}/copy`, data)
}
