<template>
  <div class="page-header"><div><h1 class="page-title">物料缺料</h1><div v-if="batch" class="version-line">批次 #{{ batch.id }} · run #{{ batch.activeRunId || '未生成' }} · {{ batch.factoryCode }} · R{{ batch.revisionNo }}</div></div><div class="actions"><el-button v-if="batch && review?.materialFingerprint && review.material !== 'CONFIRMED' && batch.status === 'MATERIAL_REVIEW' && permission.hasPermission('mrp_shortage:confirm')" type="primary" :loading="saving" :disabled="Boolean(todoError) || confirmBlockers.length > 0" @click="confirmMaterial">确认物料分析</el-button><el-button v-if="batch && batch.activeRunId && permission.hasPermission('mrp_shortage:view')" type="primary" @click="tab === 'daily' ? exportDaily() : exportMaterials()">{{ tab === 'daily' ? '导出日备料' : '导出物料汇总' }}</el-button></div></div>
  <div v-if="batch && !staleBatch && canStartAnalysis && permission.hasPermission('mrp_shortage:manage')" class="task-bar"><el-button type="primary" :loading="saving" @click="startMaterialAnalysis">{{ batch.status === 'SCHEDULE_READY' ? '开始物料分析' : '重新计算物料分析' }}</el-button></div>
  <el-alert v-if="analysisAvailability" :title="analysisAvailability" :type="canStartAnalysis ? 'success' : 'warning'" :closable="false" class="notice" />
  <el-alert v-if="todoError" :title="todoError" type="error" :closable="false" class="notice"><el-button type="primary" link @click="loadTodo">重试</el-button></el-alert>
  <div v-if="analysisTask && ['PENDING','RUNNING','FAILED'].includes(analysisTask.status)" class="analysis-progress"><span>物料分析：{{ analysisTask.phase || taskStatusText(analysisTask.status) }}</span><el-progress v-if="analysisTask.status !== 'FAILED'" :percentage="taskPercent" :status="analysisTask.status === 'PENDING' ? 'warning' : undefined" /><span v-if="analysisTask.status === 'FAILED'" class="risk-text">{{ analysisTask.errorMessage || '任务失败，请检查输入与待处理异常后重试' }}</span></div>
  <el-alert v-if="confirmBlockers.length && batch?.status === 'MATERIAL_REVIEW'" :title="'确认前待处理：' + confirmBlockers.join('、')" type="warning" :closable="false" class="notice" />
  <el-alert v-if="batch?.status === 'MATERIAL_RECALC_REQUIRED'" title="库存分配已变化，原物料结果暂停确认与发布；请重新计算物料分析。" type="warning" :closable="false" class="notice" />
  <el-alert v-if="legacyBatch" title="历史版本按物料汇总展示原末级结果；库存池与库存风险没有完整冻结记录，不能据此判断无缺料。来源明细仍可下钻查看。" type="info" :closable="false" class="notice" />
  <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice"><el-button type="primary" link @click="load">重新加载</el-button></el-alert>
  <div class="card" v-loading="loading">
    <template v-if="batch">
      <el-tabs v-model="tab" class="workspace-nav">
        <el-tab-pane label="末级物料" name="materials" />
        <el-tab-pane label="日备料" name="daily" :disabled="!batch.activeRunId" />
        <template v-if="!staleBatch">
          <el-tab-pane name="decisions"><template #label>待处理异常 <el-badge v-if="todo.issues" :value="todo.issues" /></template></el-tab-pane>
          <el-tab-pane name="policy"><template #label>默认下钻 <el-badge v-if="todo.policy" :value="todo.policy" /></template></el-tab-pane>
          <el-tab-pane label="库存分配" name="allocation" />
          <el-tab-pane name="unallocated"><template #label>待分配需求 <el-badge v-if="todo.unallocated" :value="todo.unallocated" /></template></el-tab-pane>
          <el-tab-pane name="risk"><template #label>库存风险 <el-badge v-if="todo.risks" :value="todo.risks" /></template></el-tab-pane>
          <el-tab-pane name="feedback"><template #label>物料反馈 <el-badge v-if="review?.openFeedback" :value="review.openFeedback" /></template></el-tab-pane>
          <el-tab-pane label="计算追溯" name="trace" />
        </template>
      </el-tabs>
      <div v-if="tab === 'materials'">
        <div class="filters"><el-select v-model="factoryCode" placeholder="全部工厂" clearable style="width:140px" @change="search"><el-option v-for="code in factories" :key="code" :label="code" :value="code" /></el-select><el-input v-model="sourceMonth" placeholder="来源月 YYYY-MM-01" clearable style="width:180px" @keyup.enter="search" @clear="search" /><el-input v-model="keyword" placeholder="搜索整机或末级需求物料" clearable style="width:220px" @keyup.enter="search" @clear="search" /></div>
        <el-empty v-if="stageEmpty" :description="stageEmpty" />
        <el-table v-else :data="demands" border v-loading="demandsLoading">
          <el-table-column prop="factoryCode" label="工厂" width="120" />
          <el-table-column v-if="route.query.root" prop="rootMaterialCode" label="来源整机" width="160" />
          <el-table-column prop="terminalMaterialCode" label="末级需求物料" min-width="170" />
          <el-table-column v-if="route.query.root" prop="sourceMonth" label="来源月" width="125" />
          <el-table-column label="终点类型" width="110"><template #default="{row}">{{ terminalTypeText(row.terminalType) }}</template></el-table-column>
          <el-table-column label="供应方式" width="110"><template #default="{row}">{{ supplyModeText(row.supplyMode) }}</template></el-table-column>
          <el-table-column :label="scopedDemand ? '筛选范围净需求' : '末级净需求'" min-width="145" align="right"><template #default="{row}">{{ formatQty(row.quantity) }}</template></el-table-column>
          <el-table-column v-if="!route.query.root" label="整批良品库存池" min-width="145"><template #default="{row}"><el-button v-if="!legacyBatch" type="primary" link @click="showPools(row)">{{ row.inventoryPoolCount ? row.inventoryPoolCount + ' 仓' : '无冻结库存池' }}</el-button><span v-else>历史未评估</span></template></el-table-column>
          <el-table-column v-if="!route.query.root" label="整批库存风险" min-width="135"><template #default="{row}"><span v-if="legacyBatch">未评估</span><span v-else-if="row.warehouseRiskCount" class="risk-text">{{ row.warehouseRiskCount }} 项库存风险</span><span v-else-if="!row.inventoryPoolCount">未评估</span><span v-else>无待处理风险</span></template></el-table-column>
          <el-table-column v-if="!route.query.root" label="处理状态" width="115"><template #default="{row}">{{ legacyBatch || staleBatch ? '历史记录' : row.pendingIssueCount || row.pendingPolicyCount || row.warehouseRiskCount ? '待处理' : '无待办' }}</template></el-table-column>
          <el-table-column label="操作" width="210"><template #default="{row}"><el-button v-if="!route.query.root" type="primary" link @click="showSources(row)">受影响整机</el-button><el-button v-if="row.rootMaterialCode && ['MATERIAL_REVIEW','READY'].includes(batch.status) && permission.hasPermission('mrp_shortage:manage')" type="primary" link @click="startFeedback(row)">反馈排产</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无末级需求物料" /></template>
        </el-table>
        <el-pagination v-if="!stageEmpty" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="search" @current-change="loadDemands" />
      </div>
      <RollingPlanningGrid v-else-if="tab === 'daily'" :batch-id="batch.id" view="TERMINAL" mode="day" read-only />
      <DecisionCenter v-else-if="tab === 'decisions' && !staleBatch" :batch-id="batch.id" :batch-status="batch.status" @changed="load" />
      <MaterialPolicyNoticePanel v-else-if="tab === 'policy' && !staleBatch" :batch-id="batch.id" @changed="load" />
      <InventoryAllocation v-else-if="tab === 'allocation' && !staleBatch" :batch-id="batch.id" :batch-status="batch.status" @changed="load" />
      <PendingUnallocatedPanel v-else-if="tab === 'unallocated' && !staleBatch" :batch-id="batch.id" />
      <StockRiskPanel v-else-if="tab === 'risk' && !staleBatch" :batch-id="batch.id" :run-id="batch.activeRunId" @changed="load" />
      <RequirementTrace v-else-if="tab === 'trace'" :batch-id="batch.id" />
      <div v-else-if="tab === 'feedback'" v-loading="feedbackLoading"><el-table :data="feedbackRows" border><el-table-column prop="rootMaterialCode" label="受影响整机" /><el-table-column prop="terminalMaterialCode" label="末级需求物料" /><el-table-column prop="affectedQuantity" label="影响数量" /><el-table-column prop="reason" label="反馈原因" /><el-table-column label="状态" width="100"><template #default="{row}">{{ row.status === 'OPEN' ? '待排产处理' : '已处理' }}</template></el-table-column><el-table-column label="排产处理结论" min-width="160"><template #default="{row}">{{ row.response || '待排产员填写' }}</template></el-table-column><template #empty><el-empty description="暂无物料反馈" /></template></el-table><el-pagination v-model:current-page="feedbackPage" :page-size="20" :total="feedbackTotal" layout="total, prev, pager, next" @current-change="loadFeedback" /></div>
    </template>
    <el-empty v-else-if="!loading && !error" description="暂无排产批次" />
  </div>
  <el-dialog v-model="poolsVisible" title="良品库存池" width="760px"><el-table :data="pools" border v-loading="poolsLoading"><el-table-column prop="warehouseCode" label="仓库" min-width="140" /><el-table-column prop="availableQty" label="冻结可用量" width="130" /><el-table-column prop="allocatedQty" label="已分配量" width="130" /><el-table-column prop="reservedQty" label="保留量" width="130" /><template #empty><el-empty description="该物料没有冻结良品库存池" /></template></el-table><el-pagination v-model:current-page="poolsPage" :page-size="20" :total="poolsTotal" layout="total, prev, pager, next" @current-change="loadPools" /></el-dialog>
  <el-dialog v-model="feedbackVisible" title="反馈整机排产" width="520px"><el-form :model="feedbackInput" label-width="100px"><el-form-item label="整机">{{ feedbackInput.rootMaterialCode }}</el-form-item><el-form-item label="物料">{{ feedbackInput.terminalMaterialCode }}</el-form-item><el-form-item label="影响数量"><el-input v-model="feedbackInput.affectedQuantity" /></el-form-item><el-form-item label="建议日期"><el-date-picker v-model="feedbackInput.suggestedDate" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item><el-form-item label="原因"><el-input v-model="feedbackInput.reason" type="textarea" maxlength="512" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="feedbackVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitFeedback">确认</el-button></template></el-dialog>
  <el-dialog v-model="sourcesVisible" title="受影响整机" width="760px"><el-table :data="sources" border v-loading="sourcesLoading"><el-table-column prop="rootMaterialCode" label="来源整机" /><el-table-column prop="sourceMonth" label="来源月" /><el-table-column prop="quantity" label="净需求" /><el-table-column label="操作"><template #default="{row}"><el-button v-if="batch && ['MATERIAL_REVIEW','READY'].includes(batch.status) && permission.hasPermission('mrp_shortage:manage')" type="primary" link @click="startFeedback(row)">反馈排产</el-button></template></el-table-column><template #empty><el-empty description="暂无受影响整机" /></template></el-table><el-pagination v-model:current-page="sourcesPage" :page-size="20" :total="sourcesTotal" layout="total, prev, pager, next" @current-change="loadSources" /></el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { mrpApi, type CalculationProgress, type MaterialFeedback, type MrpCalcBatch, type MrpInventoryPool, type MrpTerminalDemand, type WorkbenchReviewStatus } from '../../api/mrp'
import { usePermissionStore } from '../../stores/permission'
import RollingPlanningGrid from './components/RollingPlanningGrid.vue'
import DecisionCenter from './components/DecisionCenter.vue'
import MaterialPolicyNoticePanel from './components/MaterialPolicyNoticePanel.vue'
import InventoryAllocation from './components/InventoryAllocation.vue'
import PendingUnallocatedPanel from './components/PendingUnallocatedPanel.vue'
import StockRiskPanel from './components/StockRiskPanel.vue'
import RequirementTrace from './components/RequirementTrace.vue'
const route=useRoute(); const permission=usePermissionStore()
const batch=ref<MrpCalcBatch|null>(null); const review=ref<WorkbenchReviewStatus|null>(null); const loading=ref(false); const error=ref(''); const tab=ref('materials'); const saving=ref(false)
const todo=ref({issues:0,policy:0,risks:0,unallocated:0}); const todoError=ref(''); const analysisTask=ref<CalculationProgress|null>(null)
const taskPercent=computed(()=>analysisTask.value?.progressTotal ? Math.min(100,Math.round(100*(analysisTask.value.progressCurrent||0)/analysisTask.value.progressTotal)) : 0)
const confirmBlockers=computed(()=>[todo.value.issues?`${todo.value.issues} 项异常`:null,todo.value.policy?`${todo.value.policy} 项默认下钻`:null,todo.value.unallocated?`${todo.value.unallocated} 项待分配`:null,todo.value.risks?`${todo.value.risks} 项库存风险`:null,review.value?.openFeedback?`${review.value.openFeedback} 条反馈`:null].filter((item):item is string=>Boolean(item)))
function taskStatusText(status:string){return ({PENDING:'等待执行',RUNNING:'正在计算',FAILED:'计算失败'} as Record<string,string>)[status]||status}
const legacyBatch=computed(()=>Boolean(batch.value&&batch.value.ruleVersion!=='v5-shifted-business-weeks'))
const staleBatch=computed(()=>batch.value?.status==='STALE')
const scopedDemand=computed(()=>Boolean(sourceMonth.value.trim()||keyword.value.trim()||route.query.root))
const demands=ref<MrpTerminalDemand[]>([]); const demandsLoading=ref(false); const keyword=ref(''); const page=ref(1); const pageSize=ref(20); const total=ref(0)
const factoryCode=ref(''); const sourceMonth=ref(''); const factories=ref<string[]>([])
const pools=ref<MrpInventoryPool[]>([]);const poolsVisible=ref(false);const poolsPage=ref(1);const poolsTotal=ref(0);const poolsLoading=ref(false);const poolMaterial=ref<MrpTerminalDemand|null>(null)
const canStartAnalysis=computed(()=>Boolean(batch.value?.activeRunId && ['SCHEDULE_READY','MATERIAL_RECALC_REQUIRED','FAILED'].includes(batch.value.status) && review.value?.schedule==='CONFIRMED'))
const analysisAvailability=computed(()=>{if(!batch.value)return '';if(staleBatch.value)return '暂不能计算缺料：经营计划已更新，原批次仅供历史查询；请等待新批次整机排产确认';if(canStartAnalysis.value)return batch.value.status==='SCHEDULE_READY'?'整机排产已确认，可以开始物料缺料分析':'物料分析需要重新计算，当前可以启动';if(!batch.value.activeRunId)return '暂不能计算缺料：整机日排产尚未生成';if(batch.value.status==='SCHEDULE_READY'&&review.value?.schedule!=='CONFIRMED')return '暂不能计算缺料：等待排产员确认整机日计划';if(batch.value.status==='MATERIAL_RECALC_REQUIRED')return '暂不能计算缺料：整机确认已失效，等待排产员重新确认';return ''})
const stageEmpty=computed(()=>{if(!batch.value)return '';const status=batch.value.status;if(!batch.value.activeRunId)return '整机排产尚未生成';if(status==='SCHEDULE_READY')return review.value?.schedule==='CONFIRMED'?'待物控开始物料分析':'等待排产员确认整机日计划';if(status==='MATERIAL_RECALC_REQUIRED')return '库存分配已变化，请重新计算物料分析';if(['CALCULATING','WRITING','ANALYZING','REANALYZING'].includes(status))return '物料分析正在计算，请稍后刷新';if(status==='FAILED')return '物料分析失败，请查看任务错误并重试';return ''})
function terminalTypeText(value:string){return ({MACHINE_OUTPUT:'整机产出',FINISHED_GOOD:'整机',RAW_MATERIAL:'原材料',PCBA:'PCBA',HEAD:'机头',STRUCTURAL_LEAF:'结构末级',STOP:'策略停止'} as Record<string,string>)[value]||value||'未配置'}
function supplyModeText(value:string){return ({SELF_MADE:'自制',PURCHASED:'外购',OUTSOURCED:'委外',UNMAINTAINED:'未配置'} as Record<string,string>)[value]||value||'未配置'}
function formatQty(value:number){return Number(value).toLocaleString('zh-CN',{maximumFractionDigits:6})}
const sources=ref<MrpTerminalDemand[]>([]);const sourcesVisible=ref(false);const sourcesPage=ref(1);const sourcesTotal=ref(0);const sourcesLoading=ref(false);const sourceMaterial=ref<MrpTerminalDemand|null>(null)
const feedbackRows=ref<MaterialFeedback[]>([]); const feedbackPage=ref(1); const feedbackTotal=ref(0); const feedbackLoading=ref(false); const feedbackVisible=ref(false)
const feedbackInput=ref({factoryCode:'',rootMaterialCode:'',terminalMaterialCode:'',sourceMonth:'',affectedQuantity:'',suggestedDate:null as string|null,reason:''})
function message(e:unknown){return e instanceof Error?e.message:String(e)}
let refreshTimer:ReturnType<typeof setTimeout>|undefined
let loadGeneration=0;let demandsGeneration=0;let feedbackGeneration=0;let todoGeneration=0;let poolsGeneration=0;let sourcesGeneration=0
function current(generation:number,id:number){return generation===loadGeneration&&batch.value?.id===id}
async function load(){
  clearTimeout(refreshTimer)
  const generation=++loadGeneration
  demandsGeneration++;feedbackGeneration++;todoGeneration++;poolsGeneration++;sourcesGeneration++
  loading.value=true;error.value='';saving.value=false
  demandsLoading.value=false;feedbackLoading.value=false
  const routeId=Number(route.params.id)
  if(batch.value&&(!Number.isInteger(routeId)||routeId<=0||batch.value.id!==routeId)){
    batch.value=null;review.value=null;factories.value=[]
    tab.value='materials'
    demands.value=[];total.value=0;feedbackRows.value=[];feedbackTotal.value=0
    todo.value={issues:0,policy:0,risks:0,unallocated:0};analysisTask.value=null;todoError.value=''
    poolsVisible.value=false;sourcesVisible.value=false;feedbackVisible.value=false
  }
  try{
    const nextBatch=Number.isInteger(routeId)&&routeId>0?await mrpApi.getBatch(routeId):await mrpApi.getCurrentBatch()
    if(generation!==loadGeneration)return
    batch.value=nextBatch;review.value=null;factories.value=[]
    demands.value=[];total.value=0;feedbackRows.value=[];feedbackTotal.value=0
    todo.value={issues:0,policy:0,risks:0,unallocated:0};analysisTask.value=null;todoError.value=''
    if(!nextBatch)return
    const legacyPublished = nextBatch.status === 'PUBLISHED' && nextBatch.ruleVersion !== 'v5-shifted-business-weeks'
    const [nextReview,nextFactories]=await Promise.all([legacyPublished ? Promise.resolve(null) : mrpApi.getWorkbenchReviews(nextBatch.id),mrpApi.getBatchFactories(nextBatch.id)])
    if(!current(generation,nextBatch.id))return
    review.value=nextReview;factories.value=nextFactories
    await Promise.all([loadDemands(),loadFeedback(),loadTodo()])
    if(current(generation,nextBatch.id)&&['ANALYZING','REANALYZING','CALCULATING','WRITING'].includes(nextBatch.status))refreshTimer=setTimeout(()=>void load(),2500)
  }catch(e:unknown){if(generation===loadGeneration){batch.value=null;review.value=null;error.value='加载物料缺料失败：'+message(e)}}
  finally{if(generation===loadGeneration)loading.value=false}
}
async function loadTodo(){
  const active=batch.value;if(!active)return
  const generation=loadGeneration;const request=++todoGeneration;todoError.value=''
  try{
    const id=active.id
    const [issues,policy,risks,unallocated,task]=await Promise.all([mrpApi.getIssuesPage(id,1,1,'OPEN'),mrpApi.getPendingDefaultExplosionCount(id),active.activeRunId?mrpApi.getStockRisksPage(id,1,1,active.activeRunId):Promise.resolve({total:0}),mrpApi.getPendingUnallocatedPage(id,1,1),mrpApi.getCalculationProgress(id)])
    if(!current(generation,id)||request!==todoGeneration)return
    todo.value={issues:issues.total||0,policy:policy||0,risks:risks.total||0,unallocated:unallocated.total||0};analysisTask.value=task
  }catch(e:unknown){if(current(generation,active.id)&&request===todoGeneration)todoError.value='加载待办与任务状态失败：'+message(e)}
}
async function loadDemands(){
  const active=batch.value;if(!active){demands.value=[];total.value=0;return}
  const generation=loadGeneration;const request=++demandsGeneration;demandsLoading.value=true
  try{
    const month=sourceMonth.value.trim()
    if(month&&!/^\d{4}-\d{2}-01$/.test(month))throw new Error('来源月请填写 YYYY-MM-01')
    const data=await mrpApi.getShortagePage(active.id,{page:page.value,pageSize:pageSize.value,factoryCode:factoryCode.value||undefined,sourceMonth:month||undefined,keyword:keyword.value.trim()||undefined,rootMaterialCode:typeof route.query.root==='string'?route.query.root:undefined})
    if(!current(generation,active.id)||request!==demandsGeneration)return
    demands.value=data.items||[];total.value=data.total||0
  }catch(e:unknown){if(current(generation,active.id)&&request===demandsGeneration){demands.value=[];total.value=0;ElMessage.error('加载末级需求失败：'+message(e))}}
  finally{if(current(generation,active.id)&&request===demandsGeneration)demandsLoading.value=false}
}
async function loadFeedback(){
  const active=batch.value;if(!active){feedbackRows.value=[];feedbackTotal.value=0;return}
  const generation=loadGeneration;const request=++feedbackGeneration;feedbackLoading.value=true
  try{
    const result=await mrpApi.getMaterialFeedback(active.id,feedbackPage.value,20)
    if(!current(generation,active.id)||request!==feedbackGeneration)return
    feedbackRows.value=result.items||[];feedbackTotal.value=result.total||0
  }catch(e:unknown){if(current(generation,active.id)&&request===feedbackGeneration)ElMessage.error('加载物料反馈失败：'+message(e))}
  finally{if(current(generation,active.id)&&request===feedbackGeneration)feedbackLoading.value=false}
}
function startFeedback(row:MrpTerminalDemand){feedbackInput.value={factoryCode:row.factoryCode,rootMaterialCode:row.rootMaterialCode,terminalMaterialCode:row.terminalMaterialCode,sourceMonth:row.sourceMonth,affectedQuantity:String(row.quantity),suggestedDate:null,reason:''};feedbackVisible.value=true}
async function submitFeedback(){
  const active=batch.value;if(!active)return
  const generation=loadGeneration;saving.value=true
  try{await mrpApi.createMaterialFeedback(active.id,feedbackInput.value);if(!current(generation,active.id))return;feedbackVisible.value=false;ElMessage.success('反馈已提交');saving.value=false;await load()}
  catch(e:unknown){if(current(generation,active.id))ElMessage.error('提交反馈失败：'+message(e))}
  finally{if(current(generation,active.id))saving.value=false}
}
async function confirmMaterial(){
  const active=batch.value;const fingerprint=review.value?.materialFingerprint;if(!active||!fingerprint)return
  const generation=loadGeneration;saving.value=true
  try{const confirmed=await mrpApi.confirmMaterial(active.id,fingerprint);if(!current(generation,active.id))return;review.value=confirmed;ElMessage.success('物料分析已确认');await load()}
  catch(e:unknown){if(current(generation,active.id)){ElMessage.error('确认失败：'+message(e));saving.value=false;await load()}}
  finally{if(current(generation,active.id))saving.value=false}
}
async function startMaterialAnalysis(){
  const active=batch.value;if(!active)return
  const generation=loadGeneration;saving.value=true
  try{await mrpApi.startMaterialAnalysis(active.id);if(!current(generation,active.id))return;ElMessage.success('物料分析任务已提交');saving.value=false;await load()}
  catch(e:unknown){if(current(generation,active.id))ElMessage.error('提交物料分析失败：'+message(e))}
  finally{if(current(generation,active.id))saving.value=false}
}
function search(){page.value=1;void loadDemands()}
async function showPools(row:MrpTerminalDemand){poolMaterial.value=row;poolsPage.value=1;poolsVisible.value=true;await loadPools()}
async function loadPools(){const active=batch.value;const row=poolMaterial.value;if(!active||!row)return;const generation=loadGeneration;const request=++poolsGeneration;poolsLoading.value=true;try{const result=await mrpApi.getShortagePools(active.id,{page:poolsPage.value,pageSize:20,factoryCode:row.factoryCode,terminalMaterialCode:row.terminalMaterialCode});if(!current(generation,active.id)||request!==poolsGeneration)return;pools.value=result.items||[];poolsTotal.value=result.total||0}catch(e:unknown){if(current(generation,active.id)&&request===poolsGeneration){pools.value=[];poolsTotal.value=0;ElMessage.error('加载库存池失败：'+message(e))}}finally{if(current(generation,active.id)&&request===poolsGeneration)poolsLoading.value=false}}
async function showSources(row:MrpTerminalDemand){sourceMaterial.value=row;sourcesPage.value=1;sourcesVisible.value=true;await loadSources()}
async function loadSources(){const active=batch.value;const row=sourceMaterial.value;if(!active||!row)return;const generation=loadGeneration;const request=++sourcesGeneration;sourcesLoading.value=true;try{const result=await mrpApi.getShortagePage(active.id,{page:sourcesPage.value,pageSize:20,factoryCode:row.factoryCode,terminalMaterialCode:row.terminalMaterialCode,detail:true});if(!current(generation,active.id)||request!==sourcesGeneration)return;sources.value=result.items||[];sourcesTotal.value=result.total||0}catch(e:unknown){if(current(generation,active.id)&&request===sourcesGeneration){sources.value=[];sourcesTotal.value=0;ElMessage.error('加载受影响整机失败：'+message(e))}}finally{if(current(generation,active.id)&&request===sourcesGeneration)sourcesLoading.value=false}}
async function exportMaterials(){if(batch.value)try{await mrpApi.exportShortage(batch.value.id,{factoryCode:factoryCode.value||undefined,sourceMonth:sourceMonth.value.trim()||undefined,keyword:keyword.value.trim()||undefined,rootMaterialCode:typeof route.query.root==='string'?route.query.root:undefined})}catch(e:unknown){ElMessage.error('导出失败：'+message(e))}}
async function exportDaily(){if(batch.value)try{await mrpApi.exportDailyPlans(batch.value.id,'TERMINAL')}catch(e:unknown){ElMessage.error('导出失败：'+message(e))}}
watch(()=>route.fullPath,()=>{factoryCode.value=typeof route.query.factory==='string'?route.query.factory:'';sourceMonth.value=typeof route.query.sourceMonth==='string'?route.query.sourceMonth:'';page.value=1;void load()});onMounted(()=>{factoryCode.value=typeof route.query.factory==='string'?route.query.factory:'';sourceMonth.value=typeof route.query.sourceMonth==='string'?route.query.sourceMonth:'';void load()});onBeforeUnmount(()=>{clearTimeout(refreshTimer);loadGeneration++;demandsGeneration++;feedbackGeneration++;todoGeneration++;poolsGeneration++;sourcesGeneration++})
</script>
<style scoped>.page-header,.actions,.filters{display:flex;align-items:center;justify-content:space-between;gap:12px}.page-header{margin-bottom:16px}.page-title{font-size:20px;color:#1f2329;margin:0}.version-line{color:#86909c;margin-top:6px}.card{background:#fff;border-radius:8px;padding:16px}.notice{margin-bottom:14px}.workspace-nav{margin-bottom:12px}.filters{justify-content:flex-start;margin:12px 0}.risk-text{color:#f53f3f}.task-bar{display:flex;justify-content:flex-end;margin:0 0 14px}.analysis-progress{display:flex;align-items:center;gap:14px;margin-bottom:14px;padding:10px 14px;background:#fff;border:1px solid #e5e6eb;border-radius:6px}.analysis-progress :deep(.el-progress){min-width:180px;flex:1}</style>
