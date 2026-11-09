import type { Permission } from '../types/permission'
import { permissionGrouping } from '../config/menu'

export interface PermissionNode extends Permission {
  children: PermissionNode[]
  checked: boolean
  indeterminate: boolean
}

export function permissionIds(node: PermissionNode): number[] {
  return [...(node.id > 0 ? [node.id] : []), ...node.children.flatMap(permissionIds)]
}

// Group by menu prefixes even when a new permission has no module parent.
// Keep ungrouped historical permissions editable and preserve existing grants.
export function buildPermissionTree(catalog: Permission[], selected: Set<number>): PermissionNode[] {
  const grouping = permissionGrouping()
  const unique = [...new Map(catalog.map(p => [p.id, p])).values()]
  const sectionOf = (p: Permission) => grouping.prefixes.find(
    entry => p.code === entry.prefix || p.code.startsWith(`${entry.prefix}:`)
  )?.section ?? 'other'
  const sections = grouping.groups.flatMap(group => group.sections.map(section => ({
    ...section, label: `${group.group} / ${section.label}`
  })))
  sections.push({ code: 'other', label: '其他权限', order: 0 })
  return sections.flatMap((section, index) => {
    const members = unique.filter(p => sectionOf(p) === section.code)
      .sort((a, b) => a.sort - b.sort || a.id - b.id)
    const memberIds = new Set(members.map(p => p.id))
    const decorate = (node: PermissionNode): PermissionNode => {
      const ids = permissionIds(node)
      const count = ids.filter(id => selected.has(id)).length
      return { ...node, checked: ids.length > 0 && count === ids.length, indeterminate: count > 0 && count < ids.length }
    }
    const build = (p: Permission, ancestors: Set<number>): PermissionNode => decorate({
      ...p,
      children: members.filter(child => child.parentId === p.id && !ancestors.has(child.id))
        .map(child => build(child, new Set([...ancestors, child.id]))),
      checked: false, indeterminate: false
    })
    const children = members.filter(p => p.parentId === null || !memberIds.has(p.parentId))
      .map(p => build(p, new Set([p.id])))
    if (!children.length) return []
    return [decorate({ id: -(index + 1), code: section.code, name: section.label,
      type: 'MENU', parentId: null, sort: section.order, status: 'ACTIVE', createdAt: '', updatedAt: '',
      children, checked: false, indeterminate: false })]
  })
}

export function togglePermissionNode(tree: PermissionNode[], id: number, selected: Set<number>): Set<number> {
  const find = (nodes: PermissionNode[]): PermissionNode | undefined => {
    for (const node of nodes) {
      if (node.id === id) return node
      const child = find(node.children)
      if (child) return child
    }
    return undefined
  }
  const node = find(tree)
  const next = new Set(selected)
  if (!node) return next
  for (const permissionId of permissionIds(node)) {
    if (node.checked) next.delete(permissionId)
    else next.add(permissionId)
  }
  return next
}

export function filterPermissionTree(tree: PermissionNode[], keyword: string, onlySelected: boolean,
                                     selected: Set<number>): PermissionNode[] {
  const kw = keyword.trim().toLowerCase()
  const visit = (nodes: PermissionNode[], parentMatches = false): PermissionNode[] => nodes.flatMap(node => {
    const matches = parentMatches || !kw || `${node.name} ${node.code}`.toLowerCase().includes(kw)
    const children = visit(node.children, matches)
    const visible = matches && (!onlySelected || (node.id > 0 && selected.has(node.id)))
    return visible || children.length ? [{ ...node, children }] : []
  })
  return visit(tree)
}
