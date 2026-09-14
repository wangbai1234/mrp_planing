<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, upload, post, put, del, download } from '../../api/client'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const materials = ref<any[]>([])
const loading = ref(false)
const importVisible = ref(false)
const formVisible = ref(false)
const file = ref<File | null>(null)
const stagingResult = ref<any>(null)
const uploadLoading = ref(false)
const confirmLoading = ref(false)
const isDragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const formMode = ref<'add' | 'edit'>('add')
const formData = ref<any>({})
const formLoading = ref(false)

const filterCategory = ref('')
const filterRegion = ref('')
const filterKeyword = ref('')

// Pagination
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const categories = ['原材料', '整机']
const regions = ['上海', '惠州']
const attributes = ['限制使用', '差异', '通用']

async function loadMaterials() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    if (filterCategory.value) params.append('category', filterCategory.value)
    if (filterRegion.value) params.append('region', filterRegion.value)
    if (filterKeyword.value) params.append('keyword', filterKeyword.value)
    params.append('page', currentPage.value.toString())
    params.append('pageSize', pageSize.value.toString())
    const res = await get<any>(`/materials?${params.toString()}`)
    materials.value = res?.items || []
    total.value = res?.total || 0
  } catch (e: any) {
    ElMessage.error('加载物料失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadMaterials()
}

function handleCurrentChange(page: number) {
  currentPage.value = page
  loadMaterials()
}

function handleSearch() {
  currentPage.value = 1
  loadMaterials()
}

// 新增
function openAddDialog() {
  formMode.value = 'add'
  formData.value = {
    materialCode: '',
    materialName: '',
    projectModel: '',
    specModel: '',
    unit: '',
    moq: null,
    mpq: null,
    region: '',
    attribute: '',
    category: '原材料',
    isActive: true,
    leadTimeDays: null,
    originPlace: ''
  }
  formVisible.value = true
}

// 编辑
function openEditDialog(row: any) {
  formMode.value = 'edit'
  formData.value = { ...row }
  formVisible.value = true
}

async function handleSubmit() {
  if (!formData.value.materialCode || !formData.value.materialName) {
    ElMessage.warning('料号和物料名称必填')
    return
  }
  
  formLoading.value = true
  try {
    if (formMode.value === 'add') {
      await post('/materials', formData.value)
      ElMessage.success('新增成功')
    } else {
      await put(`/materials/${formData.value.id}`, formData.value)
      ElMessage.success('编辑成功')
    }
    formVisible.value = false
    await loadMaterials()
  } catch (e: any) {
    ElMessage.error((formMode.value === 'add' ? '新增' : '编辑') + '失败: ' + e.message)
  } finally {
    formLoading.value = false
  }
}

// 删除
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该物料？', '提示', { type: 'warning' })
    await del(`/materials/${row.id}`)
    ElMessage.success('删除成功')
    await loadMaterials()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

// 导入相关
async function downloadTemplate() {
  try {
    await download('/materials/template', '物料_导入模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch (e: any) {
    ElMessage.error('下载失败: ' + e.message)
  }
}

function handleDragOver(e: DragEvent) {
  e.preventDefault()
  isDragOver.value = true
}

function handleDragLeave() {
  isDragOver.value = false
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  isDragOver.value = false
  const droppedFile = e.dataTransfer?.files[0]
  if (droppedFile) {
    validateAndUpload(droppedFile)
  }
}

function handleFileInput(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    validateAndUpload(target.files[0])
  }
}

function validateAndUpload(f: File) {
  if (!f.name.endsWith('.xlsx')) {
    ElMessage.error('文件格式错误，请上传 .xlsx 格式的文件')
    return
  }
  if (f.size > 30 * 1024 * 1024) {
    ElMessage.error('文件大小超过 30MB 限制')
    return
  }
  file.value = f
  handleUpload(f)
}

async function handleUpload(f: File) {
  uploadLoading.value = true
  stagingResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', f)
    const res = await upload<any>('/materials/import', formData)
    stagingResult.value = res.data
    if (res?.errorRows > 0) {
      ElMessage.warning(`解析完成: ${res.successRows} 行成功, ${res.errorRows} 行有错误`)
    }
  } catch (e: any) {
    ElMessage.error('上传失败: ' + e.message)
    file.value = null
  } finally {
    uploadLoading.value = false
  }
}

function removeFile() {
  file.value = null
  stagingResult.value = null
}

async function handleImportConfirm() {
  if (!stagingResult.value?.checksum) return
  
  confirmLoading.value = true
  try {
    const res = await post<any>('/materials/import/confirm', {
      checksum: stagingResult.value.checksum,
      skipDuplicates: true
    })
    ElMessage.success(`导入成功: ${res?.imported} 条`)
    handleImportClose()
    currentPage.value = 1
    await loadMaterials()
  } catch (e: any) {
    ElMessage.error('导入失败: ' + e.message)
  } finally {
    confirmLoading.value = false
  }
}

function handleImportClose() {
  importVisible.value = false
  stagingResult.value = null
  file.value = null
  uploadLoading.value = false
}

function openImportDialog() {
  stagingResult.value = null
  file.value = null
  importVisible.value = true
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  setCrumb('数据管理', '物料管理')
  loadMaterials()
})
</script>

<template>
  <div class="material-page">
    <!-- Page Head -->
    <div class="page-header">
      <h1 class="page-title">物料管理</h1>
      <div class="page-actions">
        <el-button type="primary" @click="openImportDialog">
          <el-icon><Upload /></el-icon>导入
        </el-button>
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>新增
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterCategory" placeholder="物料类别" clearable style="width: 140px" @change="handleSearch">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="filterRegion" placeholder="所属区域" clearable style="width: 140px" @change="handleSearch">
        <el-option v-for="r in regions" :key="r" :label="r" :value="r" />
      </el-select>
      <el-input v-model="filterKeyword" placeholder="搜索料号/名称/型号" clearable style="width: 220px" @keyup.enter="handleSearch">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 数据表格 -->
    <div class="table-card" v-loading="loading">
      <el-table :data="materials" size="default" border style="width: 100%" max-height="calc(100vh - 320px)">
        <el-table-column prop="materialCode" label="料号" width="130" fixed />
        <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="projectModel" label="项目型号" width="110" show-overflow-tooltip />
        <el-table-column prop="specModel" label="规格型号" width="150" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="moq" label="MOQ" width="80" align="right" />
        <el-table-column prop="mpq" label="MPQ" width="80" align="right" />
        <el-table-column prop="region" label="所属区域" width="80" align="center" />
        <el-table-column prop="attribute" label="属性" width="90" align="center" />
        <el-table-column prop="category" label="物料类别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.category === '整机' ? 'success' : 'info'" size="small" effect="plain">{{ row.category || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="是否有效" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small" effect="plain">{{ row.isActive ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="leadTimeDays" label="提前期(天)" width="90" align="right" />
        <el-table-column prop="originPlace" label="产地" width="80" align="center" />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="total-text">共 {{ total }} 条</span>
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>

      <el-empty v-if="!loading && materials.length === 0" description="暂无物料数据，请先导入" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :width="680" :close-on-click-modal="false" class="form-dialog">
      <template #header>
        <div class="dialog-title">{{ formMode === 'add' ? '新增物料' : '编辑物料' }}</div>
      </template>
      
      <el-form :model="formData" label-width="100px" label-position="top">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0 20px;">
          <el-form-item label="料号" required>
            <el-input v-model="formData.materialCode" placeholder="请输入料号" :disabled="formMode === 'edit'" />
          </el-form-item>
          <el-form-item label="物料名称" required>
            <el-input v-model="formData.materialName" placeholder="请输入物料名称" />
          </el-form-item>
          <el-form-item label="项目型号">
            <el-input v-model="formData.projectModel" placeholder="请输入项目型号" />
          </el-form-item>
          <el-form-item label="规格型号">
            <el-input v-model="formData.specModel" placeholder="请输入规格型号" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="formData.unit" placeholder="请输入单位" />
          </el-form-item>
          <el-form-item label="MOQ最小起订量">
            <el-input-number v-model="formData.moq" :min="0" :controls="false" placeholder="请输入" style="width: 100%" />
          </el-form-item>
          <el-form-item label="MPQ最小包装">
            <el-input-number v-model="formData.mpq" :min="0" :controls="false" placeholder="请输入" style="width: 100%" />
          </el-form-item>
          <el-form-item label="所属区域">
            <el-select v-model="formData.region" placeholder="请选择" style="width: 100%">
              <el-option v-for="r in regions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="属性">
            <el-select v-model="formData.attribute" placeholder="请选择" style="width: 100%">
              <el-option v-for="a in attributes" :key="a" :label="a" :value="a" />
            </el-select>
          </el-form-item>
          <el-form-item label="物料类别">
            <el-select v-model="formData.category" placeholder="请选择" style="width: 100%">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="L-T提前期(天)">
            <el-input-number v-model="formData.leadTimeDays" :min="0" :controls="false" placeholder="请输入" style="width: 100%" />
          </el-form-item>
          <el-form-item label="产地">
            <el-input v-model="formData.originPlace" placeholder="请输入产地" />
          </el-form-item>
        </div>
        <el-form-item label="是否有效">
          <el-switch v-model="formData.isActive" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="formLoading">确认</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importVisible" :width="760" :before-close="handleImportClose" class="import-dialog" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-title">物料批量导入</div>
      </template>

      <div class="step-hint">
        <span class="step-label">步骤 1/2：</span>
        <span>可下载模板获取数据，根据指引编辑后再上传</span>
        <span class="download-link" @click="downloadTemplate">下载模板</span>
      </div>

      <div class="upload-section">
        <div 
          v-if="!file && !uploadLoading"
          class="upload-area"
          :class="{ 'is-dragover': isDragOver }"
          @dragover="handleDragOver"
          @dragleave="handleDragLeave"
          @drop="handleDrop"
          @click="fileInput?.click()"
        >
          <div class="upload-icon">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
              <path d="M28 4H12C10.9391 4 9.92172 4.42143 9.17157 5.17157C8.42143 5.92172 8 6.93913 8 8V40C8 41.0609 8.42143 42.0783 9.17157 42.8284C9.92172 43.5786 10.9391 44 12 44H36C37.0609 44 38.0783 43.5786 38.8284 42.8284C39.5786 42.0783 40 41.0609 40 40V16L28 4Z" stroke="#1677FF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M28 4V16H40" stroke="#1677FF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M18 24H30" stroke="#1677FF" stroke-width="2" stroke-linecap="round"/>
              <path d="M18 32H24" stroke="#1677FF" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="upload-main-text">拖拽文件至此，或<span class="upload-link">选择文件上传</span></div>
          <div class="upload-sub-text">仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件</div>
          <input ref="fileInput" type="file" accept=".xlsx" style="display: none" @change="handleFileInput">
        </div>

        <div v-if="uploadLoading" class="upload-area uploading">
          <el-icon class="is-loading" :size="32" color="#1677FF"><Loading /></el-icon>
          <div class="upload-main-text" style="margin-top: 12px">正在解析文件...</div>
        </div>

        <div v-if="file && !uploadLoading" class="file-info-card">
          <div class="file-icon">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
              <rect width="40" height="40" rx="8" fill="#E8F3FF"/>
              <path d="M22 10H14C12.8954 10 12 10.8954 12 12V28C12 29.1046 12.8954 30 14 30H26C27.1046 30 28 29.1046 28 28V16L22 10Z" stroke="#1677FF" stroke-width="1.5"/>
              <path d="M22 10V16H28" stroke="#1677FF" stroke-width="1.5"/>
            </svg>
          </div>
          <div class="file-details">
            <div class="file-name">{{ file.name }}</div>
            <div class="file-size">{{ formatFileSize(file.size) }}</div>
          </div>
          <div class="file-remove" @click="removeFile">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M12 4L4 12M4 4L12 12" stroke="#86909C" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </div>
        </div>

        <div v-if="stagingResult" class="parse-summary">
          <div class="summary-item">
            <span class="summary-label">总行数</span>
            <span class="summary-value">{{ stagingResult.totalRows }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">有效行</span>
            <span class="summary-value success">{{ stagingResult.successRows }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">错误行</span>
            <span class="summary-value" :class="{ error: stagingResult.errorRows > 0 }">{{ stagingResult.errorRows }}</span>
          </div>
        </div>
      </div>

      <div class="rules-section">
        <div class="rules-title">上传的 Excel 文件需符合以下规范：</div>
        <div class="rules-list">
          <div class="rule-item">1. 仅支持上传小于 30 MB 且不超过 30,000 条记录的 .xlsx 文件</div>
          <div class="rule-item">2. 所有必填项不能为空，否则将无法完成导入</div>
          <div class="rule-item">3. 不能存在合并单元格</div>
          <div class="rule-item">4. 请确保所有数据信息准确无误，错误的数据将无法被导入</div>
          <div class="rule-item">5. 导入过程可能需要一些时间，具体时长取决于导入数据量，请耐心等待</div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleImportClose">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleImportConfirm" 
            :loading="confirmLoading"
            :disabled="!stagingResult || stagingResult.errorRows > 0"
          >
            开始导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.material-page {
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
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

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
}

.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e6eb;
}

.total-text {
  font-size: 14px;
  color: #86909C;
}

/* 表单弹窗 */
:deep(.form-dialog) {
  border-radius: 12px;
}

:deep(.form-dialog .el-dialog__header) {
  padding: 20px 24px 0;
  margin: 0;
}

:deep(.form-dialog .el-dialog__body) {
  padding: 16px 24px;
}

:deep(.form-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid #E5E6EB;
}

:deep(.form-dialog .el-form-item) {
  margin-bottom: 16px;
}

:deep(.form-dialog .el-form-item__label) {
  font-size: 13px;
  color: #4E5969;
  padding-bottom: 4px;
}

/* 导入弹窗 */
:deep(.import-dialog) {
  border-radius: 12px;
}

:deep(.import-dialog .el-dialog__header) {
  padding: 20px 24px 0;
  margin: 0;
}

:deep(.import-dialog .el-dialog__body) {
  padding: 16px 24px;
}

:deep(.import-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid #E5E6EB;
}

.dialog-title {
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.step-hint {
  font-size: 14px;
  color: #4E5969;
  margin-bottom: 16px;
}

.step-label {
  color: #1F2329;
  font-weight: 500;
}

.download-link {
  color: #1677FF;
  margin-left: 8px;
  cursor: pointer;
  font-weight: 500;
}

.download-link:hover {
  text-decoration: underline;
}

.upload-section {
  margin-bottom: 20px;
}

.upload-area {
  border: 1px dashed #C9DDFF;
  border-radius: 10px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: #FAFBFC;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.upload-area:hover,
.upload-area.is-dragover {
  border-color: #1677FF;
  background: #F2F7FF;
}

.upload-area.uploading {
  cursor: default;
}

.upload-icon {
  margin-bottom: 16px;
}

.upload-main-text {
  font-size: 14px;
  color: #4E5969;
  margin-bottom: 8px;
}

.upload-link {
  color: #1677FF;
  font-weight: 500;
}

.upload-sub-text {
  font-size: 12px;
  color: #86909C;
}

.file-info-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #F7F8FA;
  border-radius: 8px;
  border: 1px solid #E5E6EB;
}

.file-details {
  flex: 1;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #1F2329;
}

.file-size {
  font-size: 12px;
  color: #86909C;
  margin-top: 2px;
}

.file-remove {
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.file-remove:hover {
  background: #E5E6EB;
}

.parse-summary {
  display: flex;
  gap: 32px;
  margin-top: 12px;
  padding: 12px 16px;
  background: #F7F8FA;
  border-radius: 8px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  font-size: 12px;
  color: #86909C;
}

.summary-value {
  font-size: 16px;
  font-weight: 600;
  color: #1F2329;
}

.summary-value.success {
  color: #00B42A;
}

.summary-value.error {
  color: #F53F3F;
}

.rules-section {
  margin-top: 4px;
}

.rules-title {
  font-size: 14px;
  font-weight: 500;
  color: #1F2329;
  margin-bottom: 12px;
}

.rule-item {
  font-size: 13px;
  color: #4E5969;
  line-height: 28px;
}
</style>
