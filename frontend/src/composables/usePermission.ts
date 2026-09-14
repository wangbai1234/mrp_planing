import { usePermissionStore } from '../stores/permission'

export function usePermission() {
  const store = usePermissionStore()

  return {
    hasPermission: (code: string) => store.hasPermission(code),
    hasAnyPermission: (codes: string[]) => store.hasAnyPermission(codes),
    hasAllPermissions: (codes: string[]) => store.hasAllPermissions(codes)
  }
}
