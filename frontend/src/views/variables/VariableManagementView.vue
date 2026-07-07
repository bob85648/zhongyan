<script setup lang="ts">
/**
 * 文件名称：VariableManagementView
 * 文件说明：数据类别管理页面，负责以结构化表格方式展示正式库数据类别主数据，
 * 支持数据类别名称搜索、排序、分页、导出和详情弹窗查看。
 * 主要职责：
 * 1. 按工序、关键词、排序条件加载数据类别分页列表。
 * 2. 通过弹窗展示数据类别简要详情，避免主页面信息过载。
 * 3. 提供数据类别主数据导出能力，便于业务复核和交付使用。
 * 开发者：czd
 */
import { ElMessage } from 'element-plus'
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getProcesses, type ProcessOption } from '@/api/demo'
import {
  exportVariableManagementList,
  getVariableManagementDetail,
  getVariableManagementList,
  type VariableDetail,
  type VariableSummary,
} from '@/api/master'

type SortField = 'metricOrder' | 'metricCode' | 'metricName' | 'batchCount' | 'dataPointCount' | 'lastCollectTime'
type SortOrder = 'asc' | 'desc'

const route = useRoute()

const loading = ref(false)
const exportLoading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)

const processOptions = ref<ProcessOption[]>([])
const variableList = ref<VariableSummary[]>([])
const currentDetail = ref<VariableDetail | null>(null)

const selectedProcessId = ref<number>()
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const sortField = ref<SortField>('metricOrder')
const sortOrder = ref<SortOrder>('asc')

const sortOptions: Array<{ label: string; value: SortField }> = [
  { label: '排序号', value: 'metricOrder' },
  { label: '变量编码', value: 'metricCode' },
  { label: '变量名称', value: 'metricName' },
  { label: '覆盖批次', value: 'batchCount' },
  { label: '数据点数量', value: 'dataPointCount' },
  { label: '最近采集时间', value: 'lastCollectTime' },
]

async function loadProcessOptions() {
  const response = await getProcesses()
  processOptions.value = response.data
  const routeProcessId = Number(route.query.processId)
  if (Number.isFinite(routeProcessId) && routeProcessId > 0) {
    selectedProcessId.value = routeProcessId
    return
  }
  if (processOptions.value.length > 0) {
    selectedProcessId.value = processOptions.value[0].id
  }
}

async function loadVariableList() {
  if (!selectedProcessId.value) {
    return
  }
  loading.value = true
  try {
    const response = await getVariableManagementList(
      selectedProcessId.value,
      keyword.value || undefined,
      pageNo.value,
      pageSize.value,
      sortField.value,
      sortOrder.value,
    )
    variableList.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error('数据类别管理数据加载失败，请检查正式库和后端服务状态')
  } finally {
    loading.value = false
  }
}

async function openDetail(metricCode: string) {
  if (!selectedProcessId.value) {
    ElMessage.warning('请先选择工序后再查看数据类别详情')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getVariableManagementDetail(selectedProcessId.value, metricCode)
    currentDetail.value = response.data
  } catch (error) {
    detailVisible.value = false
    ElMessage.error('数据类别详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  void loadVariableList()
}

function handleReset() {
  keyword.value = ''
  sortField.value = 'metricOrder'
  sortOrder.value = 'asc'
  pageNo.value = 1
  void loadVariableList()
}

function handleSortChange() {
  pageNo.value = 1
  void loadVariableList()
}

function handlePageChange(currentPage: number) {
  pageNo.value = currentPage
  void loadVariableList()
}

function handlePageSizeChange(currentPageSize: number) {
  pageSize.value = currentPageSize
  pageNo.value = 1
  void loadVariableList()
}

async function handleExport() {
  if (!selectedProcessId.value) {
    ElMessage.warning('请先选择工序后再导出')
    return
  }
  exportLoading.value = true
  try {
    const blob = await exportVariableManagementList(
      selectedProcessId.value,
      keyword.value || undefined,
      sortField.value,
      sortOrder.value,
    )
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = 'variable-management.csv'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    ElMessage.success('数据类别主数据导出成功')
  } catch (error) {
    ElMessage.error('数据类别主数据导出失败，请稍后重试')
  } finally {
    exportLoading.value = false
  }
}

watch(selectedProcessId, () => {
  pageNo.value = 1
  void loadVariableList()
})

onMounted(async () => {
  try {
    await loadProcessOptions()
    await loadVariableList()
  } catch (error) {
    ElMessage.error('数据类别管理初始化失败，请刷新页面后重试')
  }
})
</script>

<template>
  <div class="variable-page">
    <section class="page-banner">
      <div class="banner-copy">
        <span class="banner-tag">CATEGORY DATA</span>
        <h2>数据类别管理</h2>
        <p>以简洁结构化方式管理正式库数据类别主数据，并支持快速检索、排序、分页和导出。</p>
      </div>
      <div class="banner-actions">
        <el-button :loading="exportLoading" type="primary" @click="handleExport">导出数据类别</el-button>
      </div>
    </section>

    <el-card shadow="hover">
      <template #header>查询条件</template>
      <div class="toolbar-grid">
        <el-select v-model="selectedProcessId" placeholder="请选择工序">
          <el-option v-for="item in processOptions" :key="item.id" :label="item.processName" :value="item.id" />
        </el-select>
        <el-input v-model="keyword" placeholder="请输入数据类别名称或数据类别编码" clearable />
        <el-select v-model="sortField" placeholder="请选择排序字段" @change="handleSortChange">
          <el-option v-for="item in sortOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="sortOrder" placeholder="请选择排序方式" @change="handleSortChange">
          <el-option label="升序" value="asc" />
          <el-option label="降序" value="desc" />
        </el-select>
        <div class="toolbar-buttons">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <div class="table-header">
          <span>数据类别列表</span>
          <el-tag type="primary" effect="plain">共 {{ total }} 条</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="variableList" row-key="metricCode">
        <el-table-column prop="metricCode" label="数据类别编码" min-width="120" />
        <el-table-column prop="metricName" label="数据类别名称" min-width="220" />
        <el-table-column prop="sourceColumnName" label="来源字段" min-width="220" />
        <el-table-column prop="metricOrder" label="排序号" width="90" />
        <el-table-column prop="batchCount" label="覆盖批次" width="100" />
        <el-table-column prop="dataPointCount" label="数据点数量" min-width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag type="success" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastCollectTime" label="最近采集时间" min-width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="openDetail(row.metricCode)">查看详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNo"
          :page-sizes="[10, 20, 50]"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="数据类别详情"
      width="720px"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-dialog">
        <template v-if="currentDetail">
          <div class="detail-overview">
            <div class="overview-item">
              <span>数据类别名称</span>
              <strong>{{ currentDetail.metricName }}</strong>
            </div>
            <div class="overview-item">
              <span>数据类别编码</span>
              <strong>{{ currentDetail.metricCode }}</strong>
            </div>
            <div class="overview-item">
              <span>所属工序</span>
              <strong>{{ currentDetail.processName }}</strong>
            </div>
            <div class="overview-item">
              <span>状态</span>
              <strong>{{ currentDetail.status }}</strong>
            </div>
          </div>

          <div class="detail-summary">
            <div class="summary-item">
              <span>排序号</span>
              <strong>{{ currentDetail.metricOrder }}</strong>
            </div>
            <div class="summary-item">
              <span>覆盖批次</span>
              <strong>{{ currentDetail.batchCount }}</strong>
            </div>
            <div class="summary-item">
              <span>数据点数量</span>
              <strong>{{ currentDetail.dataPointCount.toLocaleString() }}</strong>
            </div>
          </div>

          <div class="detail-summary secondary">
            <div class="summary-item">
              <span>平均值</span>
              <strong>{{ currentDetail.averageValue ?? '--' }}</strong>
            </div>
            <div class="summary-item">
              <span>平均标准差</span>
              <strong>{{ currentDetail.averageStdValue ?? '--' }}</strong>
            </div>
            <div class="summary-item">
              <span>来源字段</span>
              <strong>{{ currentDetail.sourceColumnName || '--' }}</strong>
            </div>
          </div>

          <div class="time-section">
            <div class="time-item">
              <span>首次采集时间</span>
              <strong>{{ currentDetail.firstCollectTime }}</strong>
            </div>
            <div class="time-item">
              <span>最近采集时间</span>
              <strong>{{ currentDetail.lastCollectTime }}</strong>
            </div>
            <div class="time-item">
              <span>历史最小值</span>
              <strong>{{ currentDetail.minValue ?? '--' }}</strong>
            </div>
            <div class="time-item">
              <span>历史最大值</span>
              <strong>{{ currentDetail.maxValue ?? '--' }}</strong>
            </div>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.variable-page {
  display: grid;
  gap: 20px;
}

.page-banner {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  padding: 28px;
  border-radius: 24px;
  background: linear-gradient(180deg, #ffffff 0%, #f4f9ff 100%);
  border: 1px solid var(--line-color);
  box-shadow: var(--shadow-soft);
}

.banner-tag {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary-color);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.banner-copy h2 {
  margin: 16px 0 10px;
  font-size: 34px;
  color: var(--text-main);
}

.banner-copy p {
  margin: 0;
  line-height: 1.7;
  color: var(--text-secondary);
}

.banner-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: 1fr 1.1fr 1fr 1fr 0.9fr;
  gap: 14px;
}

.toolbar-buttons {
  display: flex;
  gap: 12px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-dialog {
  min-height: 280px;
}

.detail-overview,
.detail-summary,
.time-section {
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
}

.detail-overview {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.detail-summary.secondary,
.time-section {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-item,
.summary-item,
.time-item {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid var(--line-color);
  background: #f9fbff;
}

.overview-item span,
.summary-item span,
.time-item span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.overview-item strong,
.summary-item strong,
.time-item strong {
  display: block;
  margin-top: 10px;
  color: var(--text-main);
  font-size: 22px;
}

@media (max-width: 1280px) {
  .toolbar-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .page-banner,
  .toolbar-grid,
  .detail-overview,
  .detail-summary,
  .detail-summary.secondary,
  .time-section {
    grid-template-columns: 1fr;
    display: grid;
  }

  .banner-actions,
  .toolbar-buttons {
    flex-direction: column;
  }
}
</style>
