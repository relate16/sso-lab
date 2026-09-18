import type { Group } from '../types.ts'

export type GroupNode = Group & { children: GroupNode[] }

export function buildGroupTree(groups: Group[]): GroupNode[] {
  const nodes = new Map(groups.map(group => [
    group.id,
    { ...group, children: [] as GroupNode[] },
  ]))
  const roots: GroupNode[] = []
  nodes.forEach(node => {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  const sort = (items: GroupNode[]) => {
    items.sort((a, b) => a.name.localeCompare(b.name, 'ko'))
    items.forEach(item => sort(item.children))
  }
  sort(roots)
  return roots
}
