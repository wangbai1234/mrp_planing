import { get, post, put, del } from './client'
import type { User, UserPermissionDetail } from '../types/permission'

interface UserListResponse {
  items: User[]
  total: number
  page: number
  pageSize: number
}

interface CreateUserRequest {
  username: string
  displayName?: string
  department?: string
  email?: string
  phone?: string
  roleIds?: number[]
}

interface UpdateUserRequest {
  displayName?: string
  department?: string
  email?: string
  phone?: string
}

export function getUsers(params?: {
  keyword?: string
  isActive?: boolean
  roleId?: number
  page?: number
  pageSize?: number
}) {
  const query = new URLSearchParams()
  if (params?.keyword) query.set('keyword', params.keyword)
  if (params?.isActive !== undefined) query.set('isActive', String(params.isActive))
  if (params?.roleId) query.set('roleId', String(params.roleId))
  if (params?.page) query.set('page', String(params.page))
  if (params?.pageSize) query.set('pageSize', String(params.pageSize))
  const qs = query.toString()
  return get<UserListResponse>(`/users${qs ? '?' + qs : ''}`)
}

export function getUser(id: number) {
  return get<User>(`/users/${id}`)
}

export function createUser(data: CreateUserRequest) {
  return post<User>('/users', data)
}

export function updateUser(id: number, data: UpdateUserRequest) {
  return put<User>(`/users/${id}`, data)
}

export function deleteUser(id: number) {
  return del<void>(`/users/${id}`)
}

export function updateUserStatus(id: number, isActive: boolean) {
  return put<void>(`/users/${id}/status`, { isActive })
}

export function assignUserRoles(id: number, roleIds: number[]) {
  return put<void>(`/users/${id}/roles`, { roleIds })
}

export function getUserPermissions(id: number) {
  return get<UserPermissionDetail>(`/users/${id}/permissions`)
}

export function updateUserPermissions(id: number, permissions: Array<{
  permissionId: number
  effect: 'ALLOW' | 'DENY'
}>) {
  return put<void>(`/users/${id}/permissions`, { permissions })
}

export function resetUserPassword(id: number) {
  return put<void>(`/users/${id}/reset-password`, {})
}
