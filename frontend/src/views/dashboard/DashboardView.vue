<script setup lang="ts">
import { inject, onMounted } from 'vue'
const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!
onMounted(() => setCrumb('计划管理', '工作台'))
</script>

<template>
  <div class="app-page active">
    <div class="page-head">
      <div><h1>MRP 工作台</h1><div class="subhead" style="margin-top:4px;color:var(--text-2)">按 Phase 1 主链路展示今天需要处理的数据、排产风险和下一步动作。</div></div>
      <div class="head-actions"><router-link to="/planning" class="btn primary"><svg class="icon"><use href="#i-calendar"/></svg>进入滚动排产</router-link></div>
    </div>
    <div class="content-grid">
      <div class="summary-card span-3"><div class="label">当前排产版本</div><div class="number">V3.2</div><div class="note">草稿 · 绑定最新经营计划</div></div>
      <div class="summary-card span-3"><div class="label">待处理超产能周</div><div class="number">0</div><div class="note">需要人工调整后再发布</div></div>
      <div class="summary-card span-3"><div class="label">数据新鲜度</div><div class="number">3 / 3</div><div class="note">经营计划、库存、CRM 均已更新</div></div>
      <div class="summary-card span-3"><div class="label">待平衡月份</div><div class="number">0</div><div class="note">来源月份当前合计与可排量</div></div>
      <section class="panel span-12"><div class="table-title"><div><h2>Phase 1 核心流程</h2><p>每一步的数据变化会传递到后续排产版本。</p></div></div>
        <div class="workflow">
          <div class="workflow-step"><span class="step-no">01</span><strong>经营计划导入</strong><span>每次导入生成版本</span></div>
          <div class="workflow-step"><span class="step-no">02</span><strong>库存快照导入</strong><span>每日更新，分工厂汇总</span></div>
          <div class="workflow-step"><span class="step-no">03</span><strong>CRM 已出货同步</strong><span>料号 + 月份累计</span></div>
          <div class="workflow-step"><span class="step-no">04</span><strong>自动生成排产</strong><span>未来连续 12 周</span></div>
          <div class="workflow-step"><span class="step-no">05</span><strong>人工调整</strong><span>修改留痕，不生成版本</span></div>
          <div class="workflow-step"><span class="step-no">06</span><strong>发布</strong><span>计划员可直接发布</span></div>
          <div class="workflow-step"><span class="step-no">07</span><strong>导出</strong><span>自定义字段给工厂使用</span></div>
        </div>
      </section>
      <section class="panel span-7"><div class="table-title"><div><h2>今天建议处理</h2><p>按照对排产影响的优先级排序。</p></div></div>
        <div class="action-list">
          <div class="action-row"><span class="action-icon"><svg class="icon"><use href="#i-alert"/></svg></span><div><strong>处理产能超限</strong><p>当前有周排产量超过产线配置上限。</p></div><span class="tag danger">阻断发布前检查</span><router-link to="/planning" class="quick-link">去调整</router-link></div>
          <div class="action-row"><span class="action-icon"><svg class="icon"><use href="#i-database"/></svg></span><div><strong>确认经营计划版本</strong><p>当前绑定 FC-2026-08-15-V6。</p></div><span class="tag success">最新</span><router-link to="/forecast" class="quick-link">查看数据</router-link></div>
          <div class="action-row"><span class="action-icon"><svg class="icon"><use href="#i-box"/></svg></span><div><strong>检查库存快照口径</strong><p>不良品仓已排除，永惠与 APK 分开计算。</p></div><span class="tag success">已完成</span><router-link to="/inventory" class="quick-link">查看快照</router-link></div>
        </div>
      </section>
      <section class="panel span-5"><div class="table-title"><div><h2>最近活动</h2><p>版本、数据导入和发布操作。</p></div></div>
        <div class="activity" style="padding:2px 10px 8px">
          <div class="log"><span class="log-time">08:42</span><div><strong>系统生成排产版本 SP-2026-08-V3.2</strong><p>绑定经营计划 FC-2026-08-15-V6。</p></div></div>
          <div class="log"><span class="log-time">08:30</span><div><strong>CRM 已出货同步完成</strong><p>按整机料号 + 月份累计。</p></div></div>
          <div class="log"><span class="log-time">23:40</span><div><strong>库存快照导入完成</strong><p>永惠与 APK 分开计算。</p></div></div>
        </div>
      </section>
    </div>
  </div>
</template>
