<script setup lang="ts">
/**
 * 文件名称：ProcessManagementView
 * 文件说明：工序管理页面，负责以结构化表格方式展示工序主数据，
 * 支持工序名称搜索、排序、快捷跳转和详情弹窗查看。
 * 主要职责：
 * 1. 加载工序列表并提供前端搜索和排序能力。
 * 2. 通过弹窗展示工序简要详情，避免主页面信息过载。
 * 3. 提供进入数据类别管理、批次分析和质量分析的快捷入口。
 * 开发者：czd
 */
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  getProcessManagementDetail,
  getProcessManagementList,
  type ProcessDetail,
  type ProcessSummary,
} from '@/api/master'

type SortField = 'processName' | 'processCode' | 'variableCount' | 'batchCount' | 'dataPointCount'

const router = useRouter()

const loading = ref(false)
const detailLoading = ref(false)
const processList = ref<ProcessSummary[]>([])
const searchKeyword = ref('')
const sortField = ref<SortField>('processName')
const sortOrder = ref<'asc' | 'desc'>('asc')
const detailVisible = ref(false)
const currentDetail = ref<ProcessDetail | null>(null)

const sortOptions: Array<{ label: string; value: SortField }> = [
  { label: '工序名称', value: 'processName' },
  { label: '工序编码', value: 'processCode' },
  { label: '变量数量', value: 'variableCount' },
  { label: '批次数量', value: 'batchCount' },
  { label: '数据点数量', value: 'dataPointCount' },
]

const filteredProcessList = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const source = keyword
    ? processList.value.filter(
        (item) =>
          item.processName.toLowerCase().includes(keyword) || item.processCode.toLowerCase().includes(keyword),
      )
    : [...processList.value]

  return source.sort((left, right) => {
    const leftValue = left[sortField.value]
    const rightValue = right[sortField.value]

    if (typeof leftValue === 'number' && typeof rightValue === 'number') {
      return sortOrder.value === 'asc' ? leftValue - rightValue : rightValue - leftValue
    }

    const result = String(leftValue).localeCompare(String(rightValue), 'zh-CN')
    return sortOrder.value === 'asc' ? result : -result
  })
})

async function loadProcessList() {
  loading.value = true
  try {
    const response = await getProcessManagementList()
    processList.value = response.data
  } catch (error) {
    ElMessage.error('工序管理数据加载失败，请检查后端服务和 PostgreSQL 正式库连接状态')
  } finally {
    loading.value = false
  }
}

async function openDetail(processId: number) {
  detailLoading.value = true
  detailVisible.value = true
  try {
    const response = await getProcessManagementDetail(processId)
    currentDetail.value = response.data
  } catch (error) {
    detailVisible.value = false
    ElMessage.error('工序详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

function goToVariableManagement(processId: number) {
  void router.push({
    path: '/variable-management',
    query: {
      processId: String(processId),
    },
  })
}

function goToBatchAnalysis() {
  void router.push('/batches')
}

function goToQualityAnalysis() {
  void router.push('/quality')
}

function resetFilters() {
  searchKeyword.value = ''
  sortField.value = 'processName'
  sortOrder.value = 'asc'
}

onMounted(() => {
  void loadProcessList()
})
</script>

<template>
  <div class="process-page">
    <section class="page-banner">
      <div class="banner-copy">
        <span class="banner-tag">MASTER DATA</span>
        <h2>工序主数据管理</h2>
        <p>以简洁结构化方式管理当前纳入分析范围的工序主数据，并快速进入下游分析模块。</p>
      </div>
      <div class="banner-actions">
        <el-button type="primary" @click="goToBatchAnalysis">进入批次分析</el-button>
        <el-button @click="goToQualityAnalysis">进入质量分析</el-button>
      </div>
    </section>

    <el-card shadow="hover">
      <template #header>查询条件</template>
      <div class="toolbar-grid">
        <el-input v-model="searchKeyword" placeholder="请输入工序名称或工序编码" clearable />
        <el-select v-model="sortField" placeholder="请选择排序字段">
          <el-option v-for="item in sortOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="sortOrder" placeholder="请选择排序方式">
          <el-option label="升序" value="asc" />
          <el-option label="降序" value="desc" />
        </el-select>
        <div class="toolbar-buttons">
          <el-button type="primary">查询结果已实时筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <div class="table-header">
          <span>工序列表</span>
          <el-tag type="primary" effect="plain">共 {{ filteredProcessList.length }} 条</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="filteredProcessList" row-key="processId">
        <el-table-column prop="processCode" label="工序编码" min-width="140" />
        <el-table-column prop="processName" label="工序名称" min-width="220" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag type="success" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="variableCount" label="变量数量" width="110" />
        <el-table-column prop="batchCount" label="批次数量" width="110" />
        <el-table-column prop="dataPointCount" label="数据点数量" min-width="130">
          <template #default="{ row }">
            {{ row.dataPointCount.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="lastCollectTime" label="最近采集时间" min-width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="openDetail(row.processId)">查看详情</el-button>
              <el-button link type="primary" @click="goToVariableManagement(row.processId)">数据类别管理</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="工序详情"
      width="680px"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-dialog">
        <template v-if="currentDetail">
          <div class="detail-overview">
            <div class="overview-item">
              <span>工序名称</span>
              <strong>{{ currentDetail.processName }}</strong>
            </div>
            <div class="overview-item">
              <span>工序编码</span>
              <strong>{{ currentDetail.processCode }}</strong>
            </div>
            <div class="overview-item">
              <span>状态</span>
              <strong>{{ currentDetail.status }}</strong>
            </div>
            <div class="overview-item">
              <span>来源文件数</span>
              <strong>{{ currentDetail.sourceFileCount }}</strong>
            </div>
          </div>

          <div class="detail-summary">
            <div class="summary-item">
              <span>变量数量</span>
              <strong>{{ currentDetail.variableCount }}</strong>
            </div>
            <div class="summary-item">
              <span>批次数量</span>
              <strong>{{ currentDetail.batchCount }}</strong>
            </div>
            <div class="summary-item">
              <span>数据点数量</span>
              <strong>{{ currentDetail.dataPointCount.toLocaleString() }}</strong>
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
          </div>

          <div class="description-box">
            <span>业务说明</span>
            <p>{{ currentDetail.description }}</p>
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
.process-page {
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
  grid-template-columns: 1.2fr 1fr 1fr 1.2fr;
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

.detail-dialog {
  min-height: 260px;
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

.time-section {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-item,
.summary-item,
.time-item,
.description-box {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid var(--line-color);
  background: #f9fbff;
}

.overview-item span,
.summary-item span,
.time-item span,
.description-box span {
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

.description-box p {
  margin: 10px 0 0;
  line-height: 1.7;
  color: var(--text-main);
}

@media (max-width: 1200px) {
  .toolbar-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .page-banner,
  .toolbar-grid,
  .detail-overview,
  .detail-summary,
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
