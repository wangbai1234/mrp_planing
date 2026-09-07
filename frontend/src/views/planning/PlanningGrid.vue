<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, del } from '../../api/client'
import type { PlanVersion, PlanGridRow } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

// State
const factoryCode = ref('YH')
const searchQuery = ref('')
const factoryFilter = ref('all')
const issueFilter = ref('all')
const loading = ref(false)
const currentVersion = ref<PlanVersion | null>(null)
const gridData = ref<PlanGridRow[]>([])
const selectedMaterialId = ref('')
const drawerVisible = ref(false)
const compareVisible = ref(false)
const exportVisible = ref(false)
const guideVisible = ref(false)
const autoRecalcForecast = ref(false)
const autoRecalcInventory = ref(false)

// Drawer state
const drawerMaterial = ref<PlanGridRow | null>(null)
const drawerWeek = ref('')
const qtyInput = ref(0)
const exportFields = ref(['机型','版本','机头料号','整机料号','备注','各周排产数量'])

// Computed
const currentWeekStart = computed(() => {
  const now = new Date()
  const day = now.getDay()
  const diff = day === 0 ? 6 : day - 1
  const monday = new Date(now)
  monday.setDate(now.getDate() - diff)
  return monday.toISOString().split('T')[0]
})

const weeks = computed(() => {
  const weekSet = new Set<string>()
  gridData.value.forEach(r => weekSet.add(r.weekStartDate))
  return Array.from(weekSet).sort()
})

const filteredGrid = computed(() => {
  return gridData.value.filter(row => {
    const textMatch = !searchQuery.value ||
      row.materialId.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      row.materialName.toLowerCase().includes(searchQuery.value.toLowerCase())
    const factoryMatch = factoryFilter.value === 'all' || row.factoryCode === factoryFilter.value
    return textMatch && factoryMatch
  })
})

const materialGroups = computed(() => {
  const groups = new Map<string, PlanGridRow[]>()
  filteredGrid.value.forEach(row => {
    if (!groups.has(row.materialId)) groups.set(row.materialId, [])
    groups.get(row.materialId)!.push(row)
  })
  return groups
})

// Load data
async function loadPlan() {
  loading.value = true
  try {
    const res = await get<any>(`/plans/current?factoryCode=${factoryCode.value}`)
    if (res.data) {
      currentVersion.value = res.data
      autoRecalcForecast.value = res.data.autoRecalcForecast
      autoRecalcInventory.value = res.data.autoRecalcInventory
      const gridRes = await get<any>(`/plans/${res.data.id}/grid`)
      if (gridRes.data) {
        gridData.value = gridRes.data.grid || []
        currentVersion.value = gridRes.data.version || currentVersion.value
      }
    } else {
      gridData.value = []
      currentVersion.value = null
    }
  } catch (e: any) {
    ElMessage.error('加载排产数据失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function handleRecalculate() {
  if (!currentVersion.value) return
  loading.value = true
  try {
    const res = await post<any>('/recalculations', {
      forecastVersionId: currentVersion.value.forecastVersionId,
      inventorySnapshotId: currentVersion.value.inventorySnapshotId,
      shipmentBatchId: currentVersion.value.shipmentBatchId,
      capacityVersionId: currentVersion.value.capacityVersionId,
      factoryCode: factoryCode.value,
      currentWeekStart: currentWeekStart.value
    })
    if (res.data?.status === 'SUCCEEDED') {
      ElMessage.success('重算完成，已生成新版本')
      await loadPlan()
    } else {
      ElMessage.warning('重算任务已提交: ' + res.data?.status)
    }
  } catch (e: any) {
    ElMessage.error('重算失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function handlePublish() {
  if (!currentVersion.value) return
  try {
    await ElMessageBox.confirm('确认发布排产？发布后仍可修改。', '发布排产', { type: 'warning' })
    const res = await post<any>(`/plans/${currentVersion.value.id}/publish`)
    if (res.success) {
      ElMessage.success('排产已发布')
      await loadPlan()
    } else {
      ElMessage.error(res.error?.message || '发布失败')
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('发布失败: ' + e.message)
  }
}

// Cell click - open drawer
function openCell(materialId: string, weekStart: string) {
  const row = gridData.value.find(r => r.materialId === materialId && r.weekStartDate === weekStart)
  if (!row || row.isLocked) return
  selectedMaterialId.value = materialId
  drawerMaterial.value = row
  drawerWeek.value = weekStart
  qtyInput.value = row.effectiveQuantity
  drawerVisible.value = true
}

async function saveOverride() {
  if (!drawerMaterial.value || !currentVersion.value) return
  try {
    const res = await post<any>(`/plans/${currentVersion.value.id}/overrides`, {
      materialId: drawerMaterial.value.materialId,
      weekStartDate: drawerWeek.value,
      manualQuantity: qtyInput.value,
      reason: null
    })
    if (res.success) {
      ElMessage.success('保存成功')
      drawerVisible.value = false
      await loadPlan()
    }
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

async function restoreAuto() {
  if (!drawerMaterial.value || !currentVersion.value) return
  try {
    await del(`/plans/${currentVersion.value.id}/overrides/${drawerMaterial.value.materialId}?weekStartDate=${drawerWeek.value}`)
    ElMessage.success('已恢复自动值')
    drawerVisible.value = false
    await loadPlan()
  } catch (e: any) {
    ElMessage.error('恢复失败: ' + e.message)
  }
}

function getWeekLabel(weekStart: string): string {
  const start = new Date(weekStart)
  const end = new Date(start)
  end.setDate(start.getDate() + 6)
  return `${start.getMonth()+1}/${start.getDate()}–${end.getMonth()+1}/${end.getDate()}`
}

function isCurrentWeek(weekStart: string): boolean {
  return weekStart === currentWeekStart.value
}

function getCellClass(row: PlanGridRow, week: string): string {
  const classes: string[] = []
  if (row.isLocked) classes.push('locked')
  if (row.capacityExceeded && row.weekStartDate === week) classes.push('over')
  if (row.manualQuantity !== '' && row.manualQuantity !== undefined && row.weekStartDate === week) classes.push('manual')
  if (isCurrentWeek(week)) classes.push('current')
  return classes.join(' ')
}

function scrollToCurrentWeek() {
  const el = document.querySelector('.current-week-header')
  el?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' })
}

onMounted(() => {
  setCrumb('计划管理', '12 周滚动排产')
  loadPlan()
})
</script>

<template>
  <div v-loading="loading">
    <!-- Page Head -->
    <div class="page-header">
      <h1 class="page-title">12 周滚动排产</h1>
      <div class="page-actions">
        <el-button @click="guideVisible = true"><el-icon><QuestionFilled /></el-icon>怎么操作</el-button>
        <el-button @click="compareVisible = true"><el-icon><Switch /></el-icon>版本对比</el-button>
        <el-button type="primary" @click="exportVisible = true"><el-icon><Download /></el-icon>导出</el-button>
        <el-button type="primary" plain @click="handleRecalculate" :loading="loading"><el-icon><Refresh /></el-icon>重算</el-button>
        <el-button type="primary" @click="handlePublish" :disabled="!currentVersion || currentVersion.status === 'PUBLISHED'"><el-icon><Promotion /></el-icon>{{ currentVersion?.status === 'PUBLISHED' ? '已发布' : '发布' }}</el-button>
      </div>
    </div>

    <!-- 版本信息 -->
    <div class="version-info">
      经营计划 <strong>{{ currentVersion?.forecastVersionId ? 'V' + currentVersion.forecastVersionId : '-' }}</strong> ·
      排产版本 <strong>{{ currentVersion ? 'V' + currentVersion.versionNo : '-' }}</strong> ·
      <el-tag :type="currentVersion?.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
        {{ currentVersion?.status === 'PUBLISHED' ? '已发布' : currentVersion?.status === 'READY' ? '草稿' : currentVersion?.status || '无数据' }}
      </el-tag>
    </div>

    <!-- Auto Recalc Switches -->
    <el-row :gutter="8" style="margin-bottom: 10px">
      <el-col :span="12">
        <el-card shadow="never" body-style="padding: 9px 11px; display: flex; justify-content: space-between; align-items: center">
          <div><div style="font-size: 12px; font-weight: 600">经营计划导入后自动重算</div><div style="font-size: 10px; color: #748096; margin-top: 2px">导入生成经营计划新版本后，是否立即生成排产新版本</div></div>
          <el-switch v-model="autoRecalcForecast" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" body-style="padding: 9px 11px; display: flex; justify-content: space-between; align-items: center">
          <div><div style="font-size: 12px; font-weight: 600">库存快照导入后自动重算</div><div style="font-size: 10px; color: #748096; margin-top: 2px">库存快照更新后，是否立即按最新库存生成排产新版本</div></div>
          <el-switch v-model="autoRecalcInventory" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Toolbar -->
    <el-card shadow="never" body-style="padding: 0">
      <div style="min-height: 44px; padding: 6px 9px; border-bottom: 1px solid #dfe4ea; display: flex; align-items: center; justify-content: space-between; gap: 10px">
        <div style="display: flex; align-items: center; gap: 7px">
          <el-input v-model="searchQuery" placeholder="搜索整机料号 / 机型" style="width: 220px" size="small" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="factoryFilter" style="width: 100px" size="small">
            <el-option label="全部工厂" value="all" />
            <el-option label="永惠" value="永惠" />
            <el-option label="APK" value="APK" />
          </el-select>
          <el-select v-model="issueFilter" style="width: 120px" size="small">
            <el-option label="全部状态" value="all" />
            <el-option label="仅看超产能" value="over" />
            <el-option label="仅看人工调整" value="manual" />
          </el-select>
        </div>
        <div style="display: flex; align-items: center; gap: 7px">
          <span style="color: #4b5870; font-size: 12px">窗口：<strong>{{ weeks.length }} 周</strong></span>
          <el-button size="small" @click="scrollToCurrentWeek"><el-icon><Calendar /></el-icon>定位当前周</el-button>
        </div>
      </div>

      <!-- Legend -->
      <div style="min-height: 34px; padding: 6px 10px; border-bottom: 1px solid #dfe4ea; display: flex; align-items: center; gap: 14px; color: #4b5870; font-size: 12px">
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; border: 1px solid #6d97f1; background: #edf4ff; display: inline-block"></span>当前周</span>
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; border: 1px solid #3573ed; box-shadow: inset 0 -3px #3573ed; display: inline-block"></span>人工调整</span>
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; background: repeating-linear-gradient(135deg,#f4f5f7,#f4f5f7 3px,#e7e9ed 3px,#e7e9ed 6px); border: 1px solid #c7d0dc; display: inline-block"></span>已执行 / 锁定</span>
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; border: 1px solid #df8a8e; background: #fff0f0; display: inline-block"></span>超产能</span>
        <span>点击可编辑数量查看自动值和修改历史</span>
      </div>

      <!-- Grid -->
      <div style="overflow: auto; max-height: 418px">
        <table style="border-collapse: separate; border-spacing: 0; min-width: 100%; table-layout: fixed">
          <thead>
            <tr>
              <th style="position: sticky; left: 0; z-index: 9; width: 200px; background: #f6f8fb; height: 62px; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 0 10px; font-size: 12px; color: #4b5870; font-weight: 600">整机料号 / 名称</th>
              <th v-for="week in weeks" :key="week"
                  :class="{ 'current-week-header': isCurrentWeek(week) }"
                  style="width: 94px; min-width: 94px; background: #f6f8fb; height: 62px; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 0; text-align: center; font-size: 12px; color: #4b5870; font-weight: 600; vertical-align: middle">
                <div style="display: flex; flex-direction: column; align-items: center; gap: 2px">
                  <span style="font-family: monospace; font-weight: 600; font-size: 12px">{{ week }}</span>
                  <span style="font-size: 10px; color: #748096">{{ getWeekLabel(week) }}</span>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="[materialId, rows] in materialGroups" :key="materialId" @click="selectedMaterialId = materialId">
              <td style="position: sticky; left: 0; z-index: 5; background: #fff; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 8px 10px; width: 200px">
                <div style="color: #2563eb; font-family: monospace; font-size: 12px">{{ materialId }}</div>
                <div style="font-weight: 600; font-size: 13px; margin-top: 2px">{{ rows[0]?.materialName || '' }}</div>
                <div style="color: #748096; font-size: 11px; margin-top: 2px">{{ rows[0]?.factoryCode }}</div>
              </td>
              <td v-for="week in weeks" :key="week"
                  style="width: 94px; height: 64px; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 0; background: #fff">
                <el-button v-if="rows.find(r => r.weekStartDate === week)" link
                    :class="getCellClass(rows.find(r => r.weekStartDate === week)!, week)"
                    style="width: 100%; height: 64px; border: 0; background: transparent; padding: 6px 5px; display: flex; flex-direction: column; align-items: flex-end; justify-content: center"
                    @click.stop="openCell(materialId, week)">
                  <span style="font-family: monospace; font-weight: 650; font-size: 14px">{{ (rows.find(r => r.weekStartDate === week)?.effectiveQuantity || 0).toLocaleString('zh-CN') }}</span>
                  <span style="font-size: 10px; color: #748096; margin-top: 4px">
                    {{ rows.find(r => r.weekStartDate === week)?.manualQuantity !== '' && rows.find(r => r.weekStartDate === week)?.manualQuantity !== undefined ? '人工' : rows.find(r => r.weekStartDate === week)?.capacityExceeded ? '超限' : '系统' }}
                  </span>
                </el-button>
              </td>
            </tr>
            <tr v-if="materialGroups.size === 0">
              <td :colspan="1 + weeks.length" style="height: 120px; text-align: center; color: #748096">
                <el-empty description="暂无排产数据" :image-size="60" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-card>

    <!-- Drawer -->
    <el-drawer v-model="drawerVisible" title="调整周排产数量" size="390px" :destroy-on-close="true">
      <template v-if="drawerMaterial">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="整机料号"><span style="font-family: monospace">{{ drawerMaterial.materialId }}</span></el-descriptions-item>
          <el-descriptions-item label="名称">{{ drawerMaterial.materialName }}</el-descriptions-item>
          <el-descriptions-item label="显示周">{{ drawerWeek }}</el-descriptions-item>
          <el-descriptions-item label="来源月份">{{ drawerMaterial.sourceMonth }}</el-descriptions-item>
          <el-descriptions-item label="工厂">{{ drawerMaterial.factoryCode }}</el-descriptions-item>
          <el-descriptions-item label="系统自动值"><span style="font-family: monospace">{{ drawerMaterial.systemQuantity }}</span></el-descriptions-item>
        </el-descriptions>
        <el-form label-width="80px">
          <el-form-item label="周排产数量">
            <el-input-number v-model="qtyInput" :min="0" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="drawerMaterial.capacityExceeded" label="超限">
            <el-alert :title="`超出产能 ${drawerMaterial.capacityExcessQty}`" type="error" :closable="false" show-icon />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="restoreAuto" :disabled="!drawerMaterial || drawerMaterial.manualQuantity === ''">恢复自动值</el-button>
        <el-button type="primary" @click="saveOverride">保存调整</el-button>
      </template>
    </el-drawer>

    <!-- Compare Modal -->
    <el-dialog v-model="compareVisible" title="排产版本对比（只读）" width="680px">
      <el-alert title="重算生成版本；人工修改只作为当前版本内的覆盖与日志，不单独生成版本。" type="info" :closable="false" style="margin-bottom: 12px" />
      <el-table :data="[]" border size="small">
        <el-table-column prop="item" label="对比项" width="120" />
        <el-table-column prop="base" label="基线版本" />
        <el-table-column prop="current" label="当前版本" />
        <el-table-column prop="diff" label="差异说明" />
      </el-table>
      <el-empty description="选择两个版本后显示对比结果" :image-size="40" />
    </el-dialog>

    <!-- Export Modal -->
    <el-dialog v-model="exportVisible" title="选择导出字段" width="500px">
      <el-checkbox-group v-model="exportFields">
        <el-checkbox v-for="f in ['机型','版本','机头料号','整机料号','备注','各周排产数量','优先级','实际完成数量']" :key="f" :label="f">{{ f }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" @click="exportVisible = false; ElMessage.success('导出功能待实现')">生成 Excel</el-button>
      </template>
    </el-dialog>

    <!-- Guide Modal -->
    <el-dialog v-model="guideVisible" title="排产页怎么操作" width="600px">
      <el-alert title="推荐顺序：确认数据版本 → 处理红色超产能 → 将月度差异调为 0 → 必要时重算 → 发布 → 导出。" type="info" :closable="false" style="margin-bottom: 12px" />
      <el-steps direction="vertical" :active="-1">
        <el-step title="横向查看与定位当前周" description="左右滑动查看固定 12 周；定位按钮只让表格返回最左侧。" />
        <el-step title="点击周数量" description="打开右侧调整面板；保存后会留痕，但排产版本号不变。" />
        <el-step title="重算并生成新版本" description="Forecast、库存或已出货更新后使用；版本号会增加。" />
        <el-step title="发布排产" description="核验完成后使用；状态变为已发布，之后仍能修改并留痕。" />
      </el-steps>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}

.page-actions {
  display: flex;
  gap: 8px;
}

.version-info {
  font-size: 13px;
  color: #4b5870;
  margin-bottom: 12px;
}

.current-week-header {
  background: #eaf2ff !important;
  box-shadow: inset 0 3px #2563eb;
}
.locked {
  cursor: not-allowed;
  background: repeating-linear-gradient(135deg,#fafafa,#fafafa 5px,#f1f2f4 5px,#f1f2f4 10px) !important;
  color: #687386 !important;
}
.over {
  background: #fff1f1 !important;
  color: #a61f26 !important;
}
.manual {
  box-shadow: inset 0 -3px #3573ed !important;
}
.current {
  background: #f3f7ff !important;
}
</style>
