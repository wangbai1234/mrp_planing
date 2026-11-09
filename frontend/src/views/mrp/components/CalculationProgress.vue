<script setup lang="ts">
import { computed } from 'vue'
import type { CalculationProgress, LifecycleProgress } from '../../../api/mrp'
const props = defineProps<{ batchStatus: string; progress: CalculationProgress | null; retrying: boolean; lifecycle?: LifecycleProgress | null; lifecycleRetrying?: boolean }>()
const lifecycleVisible = computed(() => Boolean(props.lifecycleRetrying || (props.lifecycle && ['PENDING', 'RUNNING', 'FAILED'].includes(props.lifecycle.status))))
const lifecycleName = computed(() => props.lifecycle?.taskType === 'MRP_REVISION' || props.batchStatus === 'PUBLISHED' ? '修订' : '发布')
const checking = computed(() => ['CHECKING', 'ANALYZING', 'REANALYZING'].includes(props.batchStatus))
const completed = computed(() => props.batchStatus === 'READY' && Boolean(props.progress))
const visible = computed(() => lifecycleVisible.value || checking.value || ['CALCULATING', 'WRITING', 'WAITING_DAILY_DISTRIBUTION'].includes(props.batchStatus) || completed.value || (props.batchStatus === 'READY_TO_CALCULATE' && ['PENDING', 'RUNNING'].includes(props.progress?.status || '')))
const determinate = computed(() => lifecycleVisible.value ? (props.lifecycle?.progressTotal || 0) > 0 : !checking.value && (completed.value || (props.progress?.progressTotal || 0) > 0))
const percentage = computed(() => {
  if (lifecycleVisible.value) return determinate.value ? Math.max(0, Math.min(99, Math.round((props.lifecycle?.progressCurrent || 0) * 100 / props.lifecycle!.progressTotal))) : 0
  return completed.value ? 100 : determinate.value ? Math.max(0, Math.min(100, Math.round((props.progress?.progressCurrent || 0) * 100 / props.progress!.progressTotal))) : 0
})
const phase = computed(() => {
  if (lifecycleVisible.value) {
    if (props.lifecycle?.status === 'FAILED') return lifecycleName.value + '失败：' + (props.lifecycle.errorMessage || '请重新提交')
    if (props.lifecycle?.status === 'PENDING') return lifecycleName.value + '任务排队中'
    return props.lifecycle?.phase || '正在' + lifecycleName.value
  }
  if (checking.value) return '正在检查经营计划与库存配置'
  if (completed.value) return '计算完成'
  if (props.progress?.status === 'FAILED') return props.progress.errorMessage || '计算失败'
  if (props.progress?.status === 'PENDING' || props.progress?.phase === 'QUEUED') return '等待计算'
  if (props.progress?.phase === 'COMPLETED') return '正在确认计算结果'
  if (props.progress?.phase) return props.progress.phase
  if (props.batchStatus === 'WAITING_DAILY_DISTRIBUTION') return '正在生成日排产'
  if (props.batchStatus === 'WRITING') return '正在保存计算结果'
  return '正在计算物料需求'
})
</script>

<template>
  <div v-if="visible" class="calculation-progress" aria-live="polite" :data-testid="lifecycleVisible ? 'lifecycle-progress' : 'calculation-progress'">
    <span v-if="!lifecycleVisible && progress?.taskId" class="task-id">任务 #{{ progress.taskId }}</span>
    <el-progress class="progress-bar" :percentage="percentage" :indeterminate="!determinate" :stroke-width="8" :show-text="false" :status="lifecycleVisible && lifecycle?.status === 'FAILED' ? 'exception' : !lifecycleVisible && completed ? 'success' : !lifecycleVisible && progress?.status === 'FAILED' ? 'exception' : undefined" />
    <span v-if="determinate" class="progress-percent">{{ percentage }}%</span>
    <span>{{ phase }}<span v-if="lifecycleVisible && lifecycleRetrying || !lifecycleVisible && retrying && !completed">（进度更新重试中）</span><span v-else-if="!determinate && !checking">（读取进度中）</span></span>
  </div>
</template>

<style scoped>
.calculation-progress { display:flex; align-items:center; flex-wrap:wrap; gap:8px; color:#4E5969; font-size:13px; }
.progress-bar { width:180px; max-width:100%; flex-shrink:0; }
.progress-percent { font-variant-numeric:tabular-nums; }
</style>
