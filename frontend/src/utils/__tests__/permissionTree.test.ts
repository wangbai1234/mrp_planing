import { describe, it, expect } from 'vitest'
import { buildPermissionTree, filterPermissionTree, permissionIds, togglePermissionNode } from '../permissionTree'
import type { Permission } from '../../types/permission'

const p = (id: number, code: string, parentId: number | null = null): Permission => ({
  id, code, parentId, name: code, type: 'BUTTON', sort: id, status: 'ACTIVE', createdAt: '', updatedAt: ''
})
const catalog = [p(1, 'mrp_workspace'), p(2, 'mrp_workspace:view', 1),
  p(3, 'mrp_execution:confirm', 1), p(4, 'mrp_history:view'), p(5, 'mrp_config'),
  p(6, 'mrp_calendar:view', 5), p(7, 'mrp_calendar:manage', 6), p(8, 'user'),
  p(9, 'user:view', 8), p(10, 'legacy:view')]

describe('permission configuration tree', () => {
  it('shows new actions without module roots exactly once, with unique section IDs', () => {
    const tree = buildPermissionTree(catalog, new Set())
    const ids = tree.flatMap(permissionIds)
    expect(ids.sort((a, b) => a - b)).toEqual(catalog.map(p => p.id))
    expect(new Set(tree.map(p => p.id)).size).toBe(tree.length)
    expect(permissionIds(tree.find(n => n.code === 'mrp_workspace')!)).toEqual([1, 2, 4])
    expect(permissionIds(tree.find(n => n.code === 'mrp_execution')!)).toEqual([3])
    expect(permissionIds(tree.find(n => n.code === 'mrp_config')!)).toEqual([5])
    expect(permissionIds(tree.find(n => n.code === 'mrp_calendar')!)).toEqual([6, 7])
  })

  it('group selection grants real IDs only and retains hidden existing grants', () => {
    const selected = new Set([9, 999])
    const tree = buildPermissionTree(catalog, selected)
    const group = tree.find(n => n.code === 'mrp_calendar')!
    const next = togglePermissionNode(tree, group.id, selected)
    expect([...next].sort((a, b) => a - b)).toEqual([6, 7, 9, 999])
    expect([...next].every(id => id > 0)).toBe(true)
    const checkedTree = buildPermissionTree(catalog, next)
    expect(checkedTree.find(n => n.id === group.id)?.checked).toBe(true)
    expect(togglePermissionNode(checkedTree, group.id, next)).toEqual(selected)
  })

  it('shows partial selection at every ancestor and filters without losing grants', () => {
    const selected = new Set([7, 9, 999])
    const tree = buildPermissionTree(catalog, selected)
    expect(tree.find(n => n.code === 'mrp_calendar')?.indeterminate).toBe(true)
    const filtered = filterPermissionTree(tree, 'mrp_calendar:manage', true, selected)
    expect(filtered).toHaveLength(1)
    expect(permissionIds(filtered[0]!)).toEqual([6, 7])
    expect(selected).toEqual(new Set([7, 9, 999]))
    expect(filterPermissionTree(tree, 'no-such-permission', false, selected)).toEqual([])
  })
})
