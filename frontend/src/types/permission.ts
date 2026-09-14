export interface Permission {
  id: number
  code: string
  name: string
  type: 'MENU' | 'PAGE' | 'BUTTON' | 'DATA'
  parentId: number | null
  resource?: string
  action?: string
  sort: number
  status: string
  description?: string
  createdAt: string
  updatedAt: string
}

export interface Role {
  id: number
  code: string
  name: string
  description?: string
  status: string
  userCount?: number
  createdAt: string
  updatedAt: string
}

export interface User {
  id: number
  username: string
  displayName?: string
  department?: string
  email?: string
  phone?: string
  isActive: boolean
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
  roles?: Role[]
}

export interface UserPermission {
  id?: number
  userId: number
  permissionId: number
  effect: 'ALLOW' | 'DENY'
  permissionCode?: string
  permissionName?: string
  createdBy?: number
}

export interface UserPermissionDetail {
  rolePermissions: string[]
  userAllowPermissions: string[]
  userDenyPermissions: string[]
  effectivePermissions: string[]
}

export interface PermissionTreeNode extends Permission {
  children?: PermissionTreeNode[]
  checked?: boolean
  indeterminate?: boolean
}
