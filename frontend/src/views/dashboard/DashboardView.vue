<script setup lang="ts">
import { inject, onMounted } from 'vue'
import { Calendar, Warning, Document, Box } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!
const router = useRouter()

onMounted(() => setCrumb('计划管理', '工作台'))

function goToPlanning() {
  router.push('/planning')
}
</script>

<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h1 class="page-title">MRP 工作台</h1>
      <el-button type="primary" @click="goToPlanning">
        <el-icon><Calendar /></el-icon>进入滚动排产
      </el-button>
    </div>

    <div class="summary-grid">
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">当前排产版本</div>
        <div class="summary-number">V3.2</div>
        <div class="summary-note">草稿 · 绑定最新经营计划</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">待处理超产能周</div>
        <div class="summary-number">0</div>
        <div class="summary-note">需要人工调整后再发布</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">数据新鲜度</div>
        <div class="summary-number">3 / 3</div>
        <div class="summary-note">经营计划、库存、CRM 均已更新</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">待平衡月份</div>
        <div class="summary-number">0</div>
        <div class="summary-note">来源月份当前合计与可排量</div>
      </el-card>
    </div>

    <el-card shadow="never" class="workflow-card">
      <template #header>
        <div class="card-header-content">
          <div>
            <div class="card-title">Phase 1 核心流程</div>
            <div class="card-desc">每一步的数据变化会传递到后续排产版本。</div>
          </div>
        </div>
      </template>
      <div class="workflow">
        <div class="workflow-step"><span class="step-no">01</span><strong>经营计划导入</strong><span>每次导入生成版本</span></div>
        <div class="workflow-step"><span class="step-no">02</span><strong>库存快照导入</strong><span>每日更新，分工厂汇总</span></div>
        <div class="workflow-step"><span class="step-no">03</span><strong>CRM 已出货同步</strong><span>料号 + 月份累计</span></div>
        <div class="workflow-step"><span class="step-no">04</span><strong>自动生成排产</strong><span>未来连续 12 周</span></div>
        <div class="workflow-step"><span class="step-no">05</span><strong>人工调整</strong><span>修改留痕，不生成版本</span></div>
        <div class="workflow-step"><span class="step-no">06</span><strong>发布</strong><span>计划员可直接发布</span></div>
        <div class="workflow-step"><span class="step-no">07</span><strong>导出</strong><span>自定义字段给工厂使用</span></div>
      </div>
    </el-card>

    <div class="bottom-panels">
      <el-card shadow="never" class="action-card">
        <template #header>
          <div class="card-header-content">
            <div>
              <div class="card-title">今天建议处理</div>
              <div class="card-desc">按照对排产影响的优先级排序。</div>
            </div>
          </div>
        </template>
        <div class="action-list">
          <div class="action-row">
            <div class="action-icon danger"><el-icon><Warning /></el-icon></div>
            <div class="action-info">
              <strong>处理产能超限</strong>
              <p>当前有周排产量超过产线配置上限。</p>
            </div>
            <el-tag type="danger" size="small" effect="plain">阻断发布前检查</el-tag>
            <el-button type="primary" link @click="router.push('/planning')">去调整</el-button>
          </div>
          <div class="action-row">
            <div class="action-icon primary"><el-icon><Document /></el-icon></div>
            <div class="action-info">
              <strong>确认经营计划版本</strong>
              <p>当前绑定 FC-2026-08-15-V6。</p>
            </div>
            <el-tag type="success" size="small" effect="plain">最新</el-tag>
            <el-button type="primary" link @click="router.push('/forecast')">查看数据</el-button>
          </div>
          <div class="action-row">
            <div class="action-icon success"><el-icon><Box /></el-icon></div>
            <div class="action-info">
              <strong>检查库存快照口径</strong>
              <p>不良品仓已排除，永惠与爱培科分开计算。</p>
            </div>
            <el-tag type="success" size="small" effect="plain">已完成</el-tag>
            <el-button type="primary" link @click="router.push('/inventory')">查看快照</el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="activity-card">
        <template #header>
          <div class="card-header-content">
            <div>
              <div class="card-title">最近活动</div>
              <div class="card-desc">版本、数据导入和发布操作。</div>
            </div>
          </div>
        </template>
        <div class="activity-list">
          <div class="activity-item">
            <span class="activity-time">08:42</span>
            <div class="activity-content">
              <strong>系统生成排产版本 SP-2026-08-V3.2</strong>
              <p>绑定经营计划 FC-2026-08-15-V6。</p>
            </div>
          </div>
          <div class="activity-item">
            <span class="activity-time">08:30</span>
            <div class="activity-content">
              <strong>CRM 已出货同步完成</strong>
              <p>按整机料号 + 月份累计。</p>
            </div>
          </div>
          <div class="activity-item">
            <span class="activity-time">23:40</span>
            <div class="activity-content">
              <strong>库存快照导入完成</strong>
              <p>永惠与爱培科分开计算。</p>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2329;
  margin: 0;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  border-radius: 8px;
}

.summary-card :deep(.el-card__body) {
  padding: 20px;
}

.summary-label {
  font-size: 14px;
  color: #8f959e;
  margin-bottom: 8px;
}

.summary-number {
  font-size: 28px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 4px;
}

.summary-note {
  font-size: 12px;
  color: #8f959e;
}

.workflow-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.card-header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.card-desc {
  font-size: 12px;
  color: #8f959e;
  margin-top: 4px;
}

.workflow {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
  padding: 8px 0;
}

.workflow-step {
  position: relative;
  padding: 16px;
  background: #f5f6f7;
  border-radius: 8px;
  text-align: center;
}

.workflow-step:not(:last-child)::after {
  content: '';
  position: absolute;
  right: -16px;
  top: 50%;
  transform: translateY(-50%);
  width: 12px;
  height: 2px;
  background: #d9dce1;
}

.step-no {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #3370ff;
  margin-bottom: 8px;
}

.workflow-step strong {
  display: block;
  font-size: 14px;
  color: #1f2329;
  margin-bottom: 4px;
}

.workflow-step span {
  display: block;
  font-size: 12px;
  color: #8f959e;
}

.bottom-panels {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: 20px;
}

.action-card,
.activity-card {
  border-radius: 8px;
}

.action-list {
  display: flex;
  flex-direction: column;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #e5e6eb;
}

.action-row:last-child {
  border-bottom: none;
}

.action-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.action-icon.primary {
  background: #e5f0ff;
  color: #3370ff;
}

.action-icon.success {
  background: #e8f8e5;
  color: #34c724;
}

.action-icon.danger {
  background: #ffe5e5;
  color: #f54a45;
}

.action-info {
  flex: 1;
  min-width: 0;
}

.action-info strong {
  display: block;
  font-size: 14px;
  color: #1f2329;
  margin-bottom: 4px;
}

.action-info p {
  margin: 0;
  font-size: 12px;
  color: #8f959e;
}

.activity-list {
  display: flex;
  flex-direction: column;
}

.activity-item {
  display: flex;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #e5e6eb;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-time {
  font-size: 14px;
  color: #8f959e;
  font-family: monospace;
  white-space: nowrap;
  min-width: 50px;
}

.activity-content strong {
  display: block;
  font-size: 14px;
  color: #1f2329;
  margin-bottom: 4px;
}

.activity-content p {
  margin: 0;
  font-size: 12px;
  color: #8f959e;
}
</style>
