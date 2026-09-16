<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, del, download } from '../../api/client'
import type { PlanVersion, PlanGridRow } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

// State
const factoryCode = ref('all')
const searchQuery = ref('')
const issueFilter = ref('all')
const loading = ref(false)
const currentVersion = ref<PlanVersion | null>(null)
const gridData = ref<PlanGridRow[]>([])
const selectedMaterialId = ref('')
const drawerVisible = ref(false)
const compareVisible = ref(false)
const exportVisible = ref(false)
const materialDetailVisible = ref(false)
const settingsVisible = ref(false)
const autoRecalcForecast = ref(false)
const autoRecalcInventory = ref(false)

// Grid pagination
const gridPage = ref(1)
const gridPageSize = ref(20)
const gridTotal = ref(0)

// History versions
const historyVersions = ref<PlanVersion[]>([])
const historyLoading = ref(false)
const historyPage = ref(1)
const historyTotal = ref(0)
const historyPageSize = ref(3)

// Compare state
const compareBaseId = ref<number | null>(null)
const compareCurrentId = ref<number | null>(null)
const compareData = ref<any>(null)

// Drawer state
const drawerMaterial = ref<PlanGridRow | null>(null)
const drawerWeek = ref('')
const qtyInput = ref(0)
const exportFields = ref(['机型','版本','机头料号','整机料号','备注','各周排产数量'])
const exportFactoryCode = ref('all')
const exportLoading = ref(false)

// Material detail for dialog
const materialDetail = ref<{
  materialId: string
  materialName: string
  factoryCode: string
  monthBalances: Array<{
    month: string
    total: number
    systemTotal: number
    diff: number
    isBalanced: boolean
  }>
} | null>(null)

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
    const issueMatch = issueFilter.value === 'all' ||
      (issueFilter.value === 'over' && row.capacityExceeded) ||
      (issueFilter.value === 'manual' && row.manualQuantity !== '' && row.manualQuantity !== undefined)
    return textMatch && issueMatch
  })
})

const materialGroups = computed(() => {
  const groups = new Map<string, PlanGridRow[]>()
  filteredGrid.value.forEach(row => {
    // 按 materialId + factoryCode 分组，确保每个工厂的每个物料都是独立的一行
    const key = factoryCode.value !== 'all' ? row.materialId : `${row.materialId}__${row.factoryCode}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(row)
  })
  return groups
})

const pagedMaterialGroups = computed(() => {
  const entries = Array.from(materialGroups.value.entries())
  const start = (gridPage.value - 1) * gridPageSize.value
  const end = start + gridPageSize.value
  return new Map(entries.slice(start, end))
})

const gridTotalMaterials = computed(() => materialGroups.value.size)

// Format time to Beijing time
function formatBeijingTime(utcTime: string): string {
  if (!utcTime) return '-'
  const date = new Date(utcTime)
  const beijingDate = new Date(date.getTime() + 8 * 60 * 60 * 1000)
  const year = beijingDate.getUTCFullYear()
  const month = String(beijingDate.getUTCMonth() + 1).padStart(2, '0')
  const day = String(beijingDate.getUTCDate()).padStart(2, '0')
  const hours = String(beijingDate.getUTCHours()).padStart(2, '0')
  const minutes = String(beijingDate.getUTCMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

// Load data
async function loadPlan() {
  loading.value = true
  try {
    const url = factoryCode.value !== 'all'
      ? `/plans/current?factoryCode=${encodeURIComponent(factoryCode.value)}`
      : '/plans/current'
    const res = await get<any>(url)
    if (res && res.length > 0) {
      // 加载所有版本的网格数据
      const allGridData: PlanGridRow[] = []
      let lastVersion = res[0]
      for (const version of res) {
        const gridRes = await get<any>(`/plans/${version.id}/grid`)
        if (gridRes?.grid) {
          allGridData.push(...gridRes.grid)
        }
        if (version.id > lastVersion.id) {
          lastVersion = version
        }
      }
      gridData.value = allGridData
      currentVersion.value = lastVersion
      autoRecalcForecast.value = lastVersion.autoRecalcForecast
      autoRecalcInventory.value = lastVersion.autoRecalcInventory
      gridTotal.value = allGridData.length
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

async function loadHistoryVersions() {
  historyLoading.value = true
  try {
    const url = factoryCode.value !== 'all'
      ? `/plans?factoryCode=${encodeURIComponent(factoryCode.value)}&page=${historyPage.value}&pageSize=${historyPageSize.value}`
      : `/plans?page=${historyPage.value}&pageSize=${historyPageSize.value}`
    const res = await get<any>(url)
    if (res) {
      historyVersions.value = res
      historyTotal.value = res.length >= historyPageSize.value ? (historyPage.value + 1) * historyPageSize.value : historyPage.value * historyPageSize.value
    }
  } catch (e: any) {
    ElMessage.error('加载历史版本失败: ' + e.message)
  } finally {
    historyLoading.value = false
  }
}

async function handleFirstRecalculate() {
  loading.value = true
  try {
    const now = new Date()
    const day = now.getDay()
    const diff = day === 0 ? 6 : day - 1
    const monday = new Date(now)
    monday.setDate(now.getDate() - diff)
    const currentWeekStart = monday.toISOString().split('T')[0]

    const res = await post<any>('/recalculations', {
      currentWeekStart
    })
    if (res?.status === 'SUCCEEDED') {
      ElMessage.success('排产计算完成，已为所有工厂生成版本')
      await loadPlan()
      await loadHistoryVersions()
    } else {
      ElMessage.warning('任务已提交: ' + res?.status)
    }
  } catch (e: any) {
    ElMessage.error('计算失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function handleRecalculate() {
  if (!currentVersion.value) {
    ElMessage.warning('暂无排产数据，请先导入经营计划和库存快照')
    return
  }
  loading.value = true
  try {
    const res = await post<any>('/recalculations', {
      forecastVersionId: currentVersion.value.forecastVersionId,
      inventorySnapshotId: currentVersion.value.inventorySnapshotId,
      shipmentBatchId: currentVersion.value.shipmentBatchId,
      capacityVersionId: currentVersion.value.capacityVersionId,
      currentWeekStart: currentWeekStart.value
    })
    if (res?.status === 'SUCCEEDED') {
      ElMessage.success('重算完成，已为所有工厂生成新版本')
      await loadPlan()
      await loadHistoryVersions()
    } else {
      ElMessage.warning('重算任务已提交: ' + res?.status)
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
      await loadHistoryVersions()
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
  if (!row || isWeekLocked(weekStart)) return
  selectedMaterialId.value = materialId
  drawerMaterial.value = row
  drawerWeek.value = weekStart
  qtyInput.value = row.effectiveQuantity
  drawerVisible.value = true
}

// Material click - open detail dialog
function openMaterialDetail(materialId: string, factoryCode?: string) {
  selectedMaterialId.value = materialId
  const rows = gridData.value.filter(r => r.materialId === materialId && (!factoryCode || r.factoryCode === factoryCode))
  if (rows.length === 0) return

  const sourceMonths = [...new Set(rows.map(r => r.sourceMonth))].slice(0, 3)
  const monthBalances = sourceMonths.map(month => {
    const monthRows = rows.filter(r => r.sourceMonth === month)
    const total = monthRows.reduce((sum, r) => sum + r.effectiveQuantity, 0)
    const systemTotal = monthRows.reduce((sum, r) => sum + r.systemQuantity, 0)
    return {
      month,
      total,
      systemTotal,
      diff: total - systemTotal,
      isBalanced: total === systemTotal
    }
  })

  materialDetail.value = {
    materialId,
    materialName: rows[0]?.materialName || '',
    factoryCode: rows[0]?.factoryCode || '',
    monthBalances
  }
  materialDetailVisible.value = true
}

async function saveOverride() {
  if (!drawerMaterial.value) return
  try {
    const res = await post<any>(`/plans/${drawerMaterial.value.planVersionId}/overrides`, {
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
  if (!drawerMaterial.value) return
  try {
    await del(`/plans/${drawerMaterial.value.planVersionId}/overrides/${drawerMaterial.value.materialId}?weekStartDate=${drawerWeek.value}`)
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

function isWeekLocked(weekStart: string): boolean {
  return weekStart < currentWeekStart.value
}

function getCellClass(row: PlanGridRow, week: string): string {
  const classes: string[] = []
  if (isWeekLocked(week)) classes.push('locked')
  if (row.capacityExceeded && row.weekStartDate === week) classes.push('over')
  if (row.manualQuantity !== '' && row.manualQuantity !== undefined && row.weekStartDate === week) classes.push('manual')
  if (isCurrentWeek(week)) classes.push('current')
  return classes.join(' ')
}

async function openCompare(version: PlanVersion) {
  compareBaseId.value = version.id
  compareCurrentId.value = currentVersion.value?.id || null
  compareData.value = null
  compareVisible.value = true
}

async function doCompare() {
  if (!compareBaseId.value || !compareCurrentId.value) return
  try {
    const res = await get<any>(`/plans/compare?baseId=${compareBaseId.value}&currentId=${compareCurrentId.value}`)
    compareData.value = res
  } catch (e: any) {
    ElMessage.error('对比失败: ' + e.message)
  }
}

async function handleExport() {
  if (!currentVersion.value) {
    ElMessage.warning('暂无排产数据')
    return
  }
  exportLoading.value = true
  try {
    const res = await post<any>(`/plans/${currentVersion.value.id}/exports`, {
      fields: exportFields.value,
      factoryCode: exportFactoryCode.value !== 'all' ? exportFactoryCode.value : null,
      includePriority: false,
      includeActual: false
    })
    if (res.id) {
      ElMessage.success('导出成功，正在下载...')
      exportVisible.value = false
      await download(`/export-tasks/${res.id}/download`, `排产计划_${new Date().toISOString().slice(0,10)}.xlsx`)
    }
  } catch (e: any) {
    ElMessage.error('导出失败: ' + e.message)
  } finally {
    exportLoading.value = false
  }
}

function handleHistorySizeChange() {
  historyPage.value = 1
  loadHistoryVersions()
}

function handleHistoryPageChange() {
  loadHistoryVersions()
}

function handleGridSizeChange() {
  gridPage.value = 1
}

onMounted(() => {
  setCrumb('计划管理', '排产计划')
  loadPlan()
  loadHistoryVersions()
})
</script>

<template>
  <div v-loading="loading">
    <!-- Page Head -->
    <div class="page-header">
      <h1 class="page-title">排产计划</h1>
      <div class="page-actions">
        <el-button @click="settingsVisible = true"><el-icon><Setting /></el-icon>设置</el-button>
        <el-button @click="compareVisible = true"><el-icon><Switch /></el-icon>版本对比</el-button>
        <el-button type="primary" @click="exportVisible = true"><el-icon><Download /></el-icon>导出</el-button>
        <el-button type="primary" @click="handleRecalculate" :loading="loading"><el-icon><Refresh /></el-icon>重算</el-button>
        <el-button type="primary" @click="handlePublish" :disabled="!currentVersion || currentVersion.status === 'PUBLISHED'"><el-icon><Promotion /></el-icon>{{ currentVersion?.status === 'PUBLISHED' ? '已发布' : '发布' }}</el-button>
      </div>
    </div>

    <!-- 无排产版本提示 -->
    <el-alert v-if="!currentVersion && !loading" title="暂无排产数据" type="warning" :closable="false" style="margin-bottom: 12px">
      <template #default>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>已导入经营计划和库存快照，请点击「重算」生成排产版本</span>
          <el-button type="primary" size="small" @click="handleFirstRecalculate" :loading="loading">
            <el-icon><Refresh /></el-icon>立即重算
          </el-button>
        </div>
      </template>
    </el-alert>

    <!-- Toolbar -->
    <el-card shadow="never" body-style="padding: 0">
      <div class="toolbar">
        <div style="display: flex; align-items: center; gap: 7px">
          <el-select v-model="factoryCode" style="width: 140px" size="small" @change="loadPlan(); loadHistoryVersions()">
            <el-option label="全部工厂" value="all" />
            <el-option label="永惠" value="永惠" />
            <el-option label="爱培科" value="爱培科" />
          </el-select>
          <el-input v-model="searchQuery" placeholder="搜索整机料号 / 机型" style="width: 220px" size="small" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="issueFilter" style="width: 140px" size="small">
            <el-option label="全部状态" value="all" />
            <el-option label="仅看超产能" value="over" />
            <el-option label="仅看人工调整" value="manual" />
          </el-select>
        </div>
      </div>

      <!-- Legend -->
      <div class="legend">
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; border: 1px solid #6d97f1; background: #edf4ff; display: inline-block"></span>当前周</span>
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; border: 1px solid #3573ed; box-shadow: inset 0 -3px #3573ed; display: inline-block"></span>人工调整</span>
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; background: repeating-linear-gradient(135deg,#f4f5f7,#f4f5f7 3px,#e7e9ed 3px,#e7e9ed 6px); border: 1px solid #c7d0dc; display: inline-block"></span>已执行 / 锁定</span>
        <span style="display: inline-flex; align-items: center; gap: 5px"><span style="width: 14px; height: 10px; border: 1px solid #df8a8e; background: #fff0f0; display: inline-block"></span>超产能</span>
        <span>点击数量可编辑，点击料号查看详情</span>
      </div>

      <!-- Grid -->
      <div style="overflow: auto; max-height: 418px">
        <table style="border-collapse: separate; border-spacing: 0; min-width: 100%; table-layout: fixed">
          <thead>
            <tr>
              <th style="position: sticky; left: 0; z-index: 9; width: 200px; background: #f6f8fb; height: 62px; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 0 10px; font-size: 12px; color: #4b5870; font-weight: 600">整机料号 / 名称</th>
              <th style="position: sticky; left: 200px; z-index: 9; width: 80px; background: #f6f8fb; height: 62px; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 0 10px; font-size: 12px; color: #4b5870; font-weight: 600">工厂</th>
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
            <tr v-for="[groupKey, rows] in pagedMaterialGroups" :key="groupKey">
              <td style="position: sticky; left: 0; z-index: 5; background: #fff; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 8px 10px; width: 200px; cursor: pointer" @click="openMaterialDetail(rows[0]?.materialId || '', rows[0]?.factoryCode || '')">
                <div style="font-family: monospace; font-size: 12px; color: #2563eb">{{ rows[0]?.materialId || '' }} <span style="color: #4b5870; font-family: inherit">{{ rows[0]?.materialName || '' }}</span></div>
              </td>
              <td style="position: sticky; left: 200px; z-index: 5; background: #fff; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 8px 10px; width: 80px; font-size: 12px; color: #4b5870">
                {{ rows[0]?.factoryCode || '' }}
              </td>
              <td v-for="week in weeks" :key="week"
                  style="width: 94px; height: 64px; border-right: 1px solid #dfe4ea; border-bottom: 1px solid #dfe4ea; padding: 0; background: #fff">
                <el-button v-if="rows.find(r => r.weekStartDate === week)" link
                    :class="getCellClass(rows.find(r => r.weekStartDate === week)!, week)"
                    style="width: 100%; height: 64px; border: 0; background: transparent; padding: 6px 5px; display: flex; flex-direction: column; align-items: flex-end; justify-content: center"
                    @click.stop="openCell(rows[0]?.materialId || '', week)">
                  <span style="font-family: monospace; font-weight: 650; font-size: 14px">{{ (rows.find(r => r.weekStartDate === week)?.effectiveQuantity || 0).toLocaleString('zh-CN') }}</span>
                  <span v-if="rows.find(r => r.weekStartDate === week)?.manualQuantity !== '' && rows.find(r => r.weekStartDate === week)?.manualQuantity !== undefined" style="font-size: 10px; color: #3573ed; margin-top: 4px">人工</span>
                </el-button>
              </td>
            </tr>
            <tr v-if="pagedMaterialGroups.size === 0">
              <td :colspan="1 + weeks.length" style="height: 120px; text-align: center; color: #748096">
                <el-empty description="暂无排产数据" :image-size="60" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Grid Pagination -->
      <div v-if="gridTotalMaterials > 0" style="padding: 12px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="gridPage"
          v-model:page-size="gridPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="gridTotalMaterials"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleGridSizeChange"
          @current-change="loadPlan"
        />
      </div>
    </el-card>

    <!-- 历史版本 -->
    <el-card shadow="never" body-style="padding: 0" style="margin-top: 10px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">历史版本</span>
          <el-tag type="warning" size="small" effect="plain">禁止回滚</el-tag>
        </div>
      </template>
      <el-table :data="historyVersions" size="default" border v-loading="historyLoading">
        <el-table-column label="排产版本" width="120">
          <template #default="{ row, $index }">
            <span style="font-family: monospace">V{{ row.versionNo }}</span>
            <el-tag v-if="$index === 0" type="primary" size="small" style="margin-left: 4px">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工厂" width="100">
          <template #default="{ row }">
            {{ row.factoryCode }}
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="160">
          <template #default="{ row }">
            {{ formatBeijingTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="经营计划版本" width="140">
          <template #default="{ row }">
            <span style="font-family: monospace">V{{ row.forecastVersionId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 'PUBLISHED' ? '已发布' : row.status === 'READY' ? '草稿' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openCompare(row)">与当前对比</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyLoading && historyVersions.length === 0" description="暂无历史版本" />
      <div v-if="historyTotal > 0" style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="historyPage"
          v-model:page-size="historyPageSize"
          :page-sizes="[3, 10, 20, 50]"
          :total="historyTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleHistorySizeChange"
          @current-change="handleHistoryPageChange"
        />
      </div>
    </el-card>

    <!-- Settings Dialog -->
    <el-dialog v-model="settingsVisible" title="排产设置" width="500px">
      <el-card shadow="never" body-style="padding: 12px; display: flex; justify-content: space-between; align-items: center" style="margin-bottom: 12px">
        <div>
          <div style="font-size: 13px; font-weight: 600">经营计划导入后自动重算</div>
          <div style="font-size: 11px; color: #748096; margin-top: 2px">导入生成经营计划新版本后，是否立即生成排产新版本</div>
        </div>
        <el-switch v-model="autoRecalcForecast" />
      </el-card>
      <el-card shadow="never" body-style="padding: 12px; display: flex; justify-content: space-between; align-items: center">
        <div>
          <div style="font-size: 13px; font-weight: 600">库存快照导入后自动重算</div>
          <div style="font-size: 11px; color: #748096; margin-top: 2px">库存快照更新后，是否立即按最新库存生成排产新版本</div>
        </div>
        <el-switch v-model="autoRecalcInventory" />
      </el-card>
      <template #footer>
        <el-button type="primary" @click="settingsVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <!-- Material Detail Dialog -->
    <el-dialog v-model="materialDetailVisible" title="物料详情" width="500px">
      <template v-if="materialDetail">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="整机料号"><span style="font-family: monospace">{{ materialDetail.materialId }}</span></el-descriptions-item>
          <el-descriptions-item label="名称">{{ materialDetail.materialName }}</el-descriptions-item>
          <el-descriptions-item label="工厂">{{ materialDetail.factoryCode }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-bottom: 8px; font-weight: 600">来源月份核验</div>
        <el-row :gutter="10">
          <el-col v-for="balance in materialDetail.monthBalances" :key="balance.month" :span="8">
            <el-card shadow="never" :body-style="{ padding: '10px', background: balance.isBalanced ? '#f6f8fb' : '#fff6f6' }">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
                <span style="font-weight: 600">{{ balance.month }}</span>
                <el-tag :type="balance.isBalanced ? 'success' : 'danger'" size="small">
                  {{ balance.isBalanced ? '已平衡' : '待调整' }}
                </el-tag>
              </div>
              <el-row :gutter="8" style="font-size: 11px; color: #748096">
                <el-col :span="8">可排量<div style="font-weight: 600; color: #1F2329; font-family: monospace">{{ balance.systemTotal }}</div></el-col>
                <el-col :span="8">当前<div style="font-weight: 600; color: #1F2329; font-family: monospace">{{ balance.total }}</div></el-col>
                <el-col :span="8">差异<div style="font-weight: 600; font-family: monospace" :style="{ color: balance.diff === 0 ? '#00B42A' : '#F53F3F' }">{{ balance.diff > 0 ? '+' : '' }}{{ balance.diff }}</div></el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>
      </template>
      <template #footer>
        <el-button @click="materialDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Drawer -->
    <el-drawer v-model="drawerVisible" title="调整周排产数量" size="550px" :destroy-on-close="true">
      <template v-if="drawerMaterial">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 12px">
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
    <el-dialog v-model="compareVisible" title="排产版本对比" width="700px">
      <el-row :gutter="12" style="margin-bottom: 16px">
        <el-col :span="12">
          <div style="margin-bottom: 4px; font-size: 12px; color: #748096">基线版本</div>
          <el-select v-model="compareBaseId" style="width: 100%">
            <el-option v-for="v in historyVersions" :key="v.id" :label="'V' + v.versionNo + ' - ' + formatBeijingTime(v.createdAt)" :value="v.id" />
          </el-select>
        </el-col>
        <el-col :span="12">
          <div style="margin-bottom: 4px; font-size: 12px; color: #748096">当前版本</div>
          <el-select v-model="compareCurrentId" style="width: 100%">
            <el-option v-for="v in historyVersions" :key="v.id" :label="'V' + v.versionNo + ' - ' + formatBeijingTime(v.createdAt)" :value="v.id" />
          </el-select>
        </el-col>
      </el-row>
      <el-button type="primary" @click="doCompare" :disabled="!compareBaseId || !compareCurrentId || compareBaseId === compareCurrentId">开始对比</el-button>
      <el-table v-if="compareData" :data="compareData.differences || []" border size="small" style="margin-top: 12px">
        <el-table-column prop="materialId" label="整机料号" width="140" />
        <el-table-column prop="weekStartDate" label="周" width="120" />
        <el-table-column label="基线数量" width="100">
          <template #default="{ row }">{{ row.baseQty }}</template>
        </el-table-column>
        <el-table-column label="当前数量" width="100">
          <template #default="{ row }">{{ row.currentQty }}</template>
        </el-table-column>
        <el-table-column label="差异" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.diff > 0 ? '#00B42A' : row.diff < 0 ? '#F53F3F' : '#1F2329' }">
              {{ row.diff > 0 ? '+' : '' }}{{ row.diff }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="compareData && (!compareData.differences || compareData.differences.length === 0)" description="两个版本无差异" :image-size="40" />
      <template #footer>
        <el-button @click="compareVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Export Modal -->
    <el-dialog v-model="exportVisible" title="选择导出字段" width="500px">
      <el-form label-width="80px">
        <el-form-item label="工厂">
          <el-select v-model="exportFactoryCode" style="width: 100%">
            <el-option label="全部工厂" value="all" />
            <el-option label="永惠" value="永惠" />
            <el-option label="爱培科" value="爱培科" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出字段">
          <el-checkbox-group v-model="exportFields">
            <el-checkbox v-for="f in ['机型','版本','机头料号','整机料号','备注','各周排产数量','优先级','实际完成数量']" :key="f" :label="f">{{ f }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" @click="handleExport" :loading="exportLoading">生成 Excel</el-button>
      </template>
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

.toolbar {
  min-height: 44px;
  padding: 6px 9px;
  border-bottom: 1px solid #dfe4ea;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.legend {
  min-height: 34px;
  padding: 6px 10px;
  border-bottom: 1px solid #dfe4ea;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #4b5870;
  font-size: 12px;
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
