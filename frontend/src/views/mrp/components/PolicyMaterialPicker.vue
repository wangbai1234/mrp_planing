<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { mrpApi, type StockPolicyMaterialOption } from '../../../api/mrp'

const props = defineProps<{ modelValue: string; kind: 'material' | 'stock' | 'forecast'; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const options = ref<StockPolicyMaterialOption[]>([])
const selected = ref<StockPolicyMaterialOption>()
const displayedOptions = computed(() => selected.value && !options.value.some(item => item.materialCode === selected.value?.materialCode) ? [selected.value, ...options.value] : options.value)
function select(value: string) { selected.value = options.value.find(item => item.materialCode === value) || (selected.value?.materialCode === value ? selected.value : undefined); emit('update:modelValue', value || '') }
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const page = ref(1)
const total = ref(0)
let sequence = 0
let timer: ReturnType<typeof setTimeout> | undefined
function label(item: StockPolicyMaterialOption) { return [item.materialCode, item.materialName, item.specModel].filter(Boolean).join(' · ') }
async function load() {
  const request = ++sequence
  loading.value = true; error.value = ''
  try {
    const result = props.kind === 'forecast'
      ? await mrpApi.searchForecastMaterials(keyword.value, page.value)
      : await mrpApi.searchPolicyMaterials(props.kind, keyword.value, page.value)
    if (request !== sequence) return
    options.value = result.items; total.value = result.total
  } catch (cause: unknown) {
    if (request !== sequence) return
    options.value = []; total.value = 0
    error.value = cause instanceof Error ? cause.message : '加载物料失败'
  } finally { if (request === sequence) loading.value = false }
}
function search(value: string) {
  keyword.value = value.trim(); page.value = 1; ++sequence
  options.value = []; total.value = 0
  clearTimeout(timer); timer = setTimeout(load, 250)
}
function open(visible: boolean) { if (visible && !props.disabled) { clearTimeout(timer); keyword.value = ''; page.value = 1; load() } }
watch(() => props.modelValue, () => { ++sequence; clearTimeout(timer); loading.value = false })
onBeforeUnmount(() => { ++sequence; clearTimeout(timer) })
</script>

<template>
  <el-input v-if="disabled" :model-value="modelValue" disabled />
  <el-select v-else :model-value="modelValue" @update:model-value="select"
    filterable remote clearable :remote-method="search" :loading="loading" @visible-change="open"
    placeholder="搜索物料编码、名称或规格" style="width:100%" :no-data-text="error || '未找到有效物料'">
    <el-option v-for="item in displayedOptions" :key="item.materialCode" :label="label(item)" :value="item.materialCode" />
    <template #footer>
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button type="primary" link @click="load">重新加载</el-button></el-alert>
      <el-pagination v-else-if="total > 10" v-model:current-page="page" :page-size="10" :total="total" small
        layout="total, prev, pager, next" @current-change="load" />
    </template>
  </el-select>
</template>
