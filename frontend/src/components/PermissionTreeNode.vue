<script setup lang="ts">
import { ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import type { Permission } from '../types/permission'

interface TreeNode extends Permission {
  children: TreeNode[]
  checked: boolean
  indeterminate: boolean
}

const props = defineProps<{
  nodes: TreeNode[]
  expandedKeys: Set<number>
}>()

const emit = defineEmits<{
  toggle: [id: number]
  expand: [id: number]
}>()

function handleToggle(id: number) {
  emit('toggle', id)
}

function handleExpand(id: number) {
  emit('expand', id)
}
</script>

<template>
  <div v-for="node in nodes" :key="node.id" class="tree-node">
    <div class="node-row" :class="{ 'is-leaf': !node.children?.length }">
      <span class="expand-icon" @click="handleExpand(node.id)">
        <el-icon v-if="node.children?.length">
          <ArrowDown v-if="expandedKeys.has(node.id)" />
          <ArrowRight v-else />
        </el-icon>
      </span>
      <el-checkbox
        :model-value="node.checked"
        :indeterminate="node.indeterminate"
        @change="handleToggle(node.id)"
      >
        {{ node.name }}
        <span class="permission-code">{{ node.code }}</span>
      </el-checkbox>
    </div>
    <div v-if="node.children?.length && expandedKeys.has(node.id)" class="children">
      <PermissionTreeNode
        :nodes="node.children"
        :expanded-keys="expandedKeys"
        @toggle="handleToggle"
        @expand="handleExpand"
      />
    </div>
  </div>
</template>

<style scoped>
.tree-node {
  margin: 2px 0;
}

.node-row {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
}

.node-row:hover {
  background: #f0f1f2;
}

.expand-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  margin-right: 4px;
}

.children {
  padding-left: 24px;
}

.permission-code {
  margin-left: 8px;
  color: #999;
  font-size: 12px;
}
</style>
