<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, CaretRight, CircleCheck, Clock, Document } from '@element-plus/icons-vue'
import type { AuditLogRecord } from '../api/audit'
import { auditChanges, auditSummary, auditTime } from '../utils/auditPresentation'

const props = withDefaults(defineProps<{
  logs: AuditLogRecord[]
  loading?: boolean
  error?: string
  total?: number
  page?: number
  pageSize?: number
}>(), { loading: false, error: '', total: 0, page: 1, pageSize: 20 })

const emit = defineEmits<{ pageChange: [page: number]; retry: [] }>()
const entries = computed(() => props.logs.map(log => ({
  log,
  changes: auditChanges(log),
  summary: auditSummary(log),
  mode: log.after_value == null ? 'before' : log.before_value == null ? 'after' : 'compare',
})))
const expanded = ref<Array<string | number>>([])
watch(entries, rows => { expanded.value = rows.map(row => row.log.id) }, { immediate: true })

const avatarColors = ['#3370ff', '#36a66a', '#c86a40', '#9a62b1', '#368fa3']
const stateValues = new Set(['启用', '停用', '生产', '停产', '自制', '外购', '委外', '继续下钻', '停止下钻', '待维护', '已发布', '草稿'])

function operatorName(log: AuditLogRecord): string {
  return log.operator_name || (log.operator_id == null ? '系统' : `用户 ${log.operator_id}`)
}

function avatarColor(log: AuditLogRecord): string {
  return log.operator_id == null ? '#86909c' : avatarColors[Math.abs(log.operator_id) % avatarColors.length]
}

function fieldIcon(key: string) {
  if (/date|time|monthsJson/i.test(key)) return Clock
  if (/status|enabled|productionDay|goodWarehouse|qualityConfigured/i.test(key)) return CircleCheck
  return Document
}

function stateTagType(value: string): 'success' | 'warning' | 'info' | 'primary' {
  if (['启用', '生产', '自制', '已发布'].includes(value)) return 'success'
  if (['停用', '停产', '停止下钻', '待维护'].includes(value)) return 'warning'
  if (value === '草稿') return 'info'
  return 'primary'
}
</script>

<template>
  <div class="audit-viewer" v-loading="loading">
    <el-alert v-if="error" :title="`日志加载失败：${error}`" type="error" :closable="false">
      <template #default><el-button type="primary" link @click="emit('retry')">重新加载</el-button></template>
    </el-alert>
    <el-collapse v-if="entries.length" v-model="expanded" class="audit-list">
      <el-collapse-item v-for="entry in entries" :key="entry.log.id" :name="entry.log.id">
        <template #title>
          <div class="audit-head">
            <el-icon class="audit-toggle" :class="{ 'is-open': expanded.includes(entry.log.id) }"><CaretRight /></el-icon>
            <span class="audit-actor">
              <el-avatar :size="24" :src="entry.log.operator_avatar_url || undefined" :style="{ backgroundColor: avatarColor(entry.log) }">
                {{ operatorName(entry.log).slice(0, 1) }}
              </el-avatar>
              <span class="audit-actor-name" :title="operatorName(entry.log)">{{ operatorName(entry.log) }}</span>
            </span>
            <span class="audit-summary">{{ entry.summary }}</span>
            <time>{{ auditTime(entry.log.created_at) }}</time>
          </div>
        </template>
        <div v-if="entry.changes.length" class="audit-changes">
          <div v-for="change in entry.changes" :key="change.key" class="audit-change">
            <span class="audit-label" :title="change.label">
              <el-icon><component :is="fieldIcon(change.key)" /></el-icon>
              <span>{{ change.label }}</span>
            </span>
            <span class="audit-values">
              <span v-if="entry.mode !== 'after'" class="audit-value">
                <el-tag v-if="stateValues.has(change.before)" size="small" :type="stateTagType(change.before)">{{ change.before }}</el-tag>
                <template v-else>{{ change.before }}</template>
              </span>
              <el-icon v-if="entry.mode === 'compare'" class="audit-arrow"><ArrowRight /></el-icon>
              <span v-if="entry.mode !== 'before'" class="audit-value">
                <el-tag v-if="stateValues.has(change.after)" size="small" :type="stateTagType(change.after)">{{ change.after }}</el-tag>
                <template v-else>{{ change.after }}</template>
              </span>
            </span>
          </div>
        </div>
        <el-empty v-else description="本次操作无字段明细" :image-size="48" />
      </el-collapse-item>
    </el-collapse>
    <el-empty v-else-if="!loading && !error" description="暂无操作记录" :image-size="72" />
    <el-pagination v-if="total > pageSize" class="audit-pagination" :current-page="page" :page-size="pageSize"
      :total="total" layout="total, prev, pager, next" @current-change="emit('pageChange', $event)" />
  </div>
</template>

<style scoped>
.audit-viewer { min-height: 100px; }
.audit-list { max-height: min(64vh, 640px); overflow-y: auto; border-top: 0; }
.audit-list :deep(.el-collapse-item__header) { height: auto; min-height: 46px; padding: 7px 2px; line-height: 1.4; border-bottom: 0; }
.audit-list :deep(.el-collapse-item__arrow) { display: none; }
.audit-list :deep(.el-collapse-item__wrap) { border-bottom: 0; }
.audit-list :deep(.el-collapse-item__content) { padding-bottom: 8px; }
.audit-list :deep(.el-collapse-item) { border-bottom: 1px solid #e5e6eb; }
.audit-head { display: flex; align-items: center; gap: 10px; width: 100%; min-width: 0; color: #1f2329; font-size: 13px; }
.audit-toggle { flex: none; color: #86909c; transition: transform 0.18s; }
.audit-toggle.is-open { transform: rotate(90deg); }
.audit-actor { display: inline-flex; align-items: center; flex: none; max-width: 180px; padding: 2px 8px 2px 2px; border-radius: 16px; background: #f2f3f5; gap: 5px; }
.audit-actor :deep(.el-avatar) { flex: none; color: #fff; font-size: 12px; }
.audit-actor-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.audit-summary { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.audit-head time { flex: none; margin-left: auto; color: #86909c; font-size: 12px; white-space: nowrap; }
.audit-changes { margin-left: 24px; padding: 5px 14px; border-radius: 6px; background: #f7f8fa; }
.audit-change { display: grid; grid-template-columns: minmax(135px, 190px) minmax(0, 1fr); gap: 12px; align-items: center; min-height: 36px; padding: 5px 0; }
.audit-label { display: inline-flex; align-items: flex-start; gap: 7px; min-width: 0; color: #4e5969; overflow-wrap: anywhere; }
.audit-label .el-icon { flex: none; margin-top: 2px; color: #86909c; }
.audit-values { display: flex; align-items: center; gap: 10px; min-width: 0; }
.audit-value { min-width: 0; color: #1f2329; overflow-wrap: anywhere; white-space: pre-wrap; }
.audit-arrow { flex: none; color: #86909c; }
.audit-pagination { justify-content: flex-end; margin-top: 12px; }
@media (max-width: 600px) {
  .audit-head { display: grid; grid-template-columns: 14px minmax(0, 1fr); gap: 3px 8px; }
  .audit-toggle { grid-row: 1 / 4; }
  .audit-actor { grid-column: 2; width: fit-content; max-width: 100%; }
  .audit-summary, .audit-head time { grid-column: 2; margin-left: 0; }
  .audit-changes { margin-left: 0; padding: 5px 10px; }
  .audit-change { grid-template-columns: minmax(0, 1fr); gap: 3px; padding: 6px 0; }
  .audit-values { margin-left: 21px; }
}
</style>
