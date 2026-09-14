import { get, post, put, del } from './client'
import type { Role } from '../types/permission'

export function getRoles() {
  return get<Role[]>('/roles')
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

export function copyRole(id: number, data: { name: string; code: string }) {
  return post<Role>(`/roles/${id}/copy`, data)
}
