<script setup lang="ts">
/**
 * 文件名称：BatchAnalysisView
 * 文件说明：批次分析页面，负责按工序、批次、数据类别联动加载历史数据，展示批次概览、趋势图和异常明细。
 * 业务职责：
 * 1. 先按工序加载批次列表，再按当前选中批次加载可用数据类别，避免展示无数据的数据类别。
 * 2. 在批次或数据类别切换后，重新查询批次详情与趋势数据，保证图表和明细与当前选择一致。
 * 3. 将批次分析页面保持为企业级的结构化展示方式，便于后续继续扩展质量分析能力。
 * 开发者：czd
 */
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  getBatchDetail,
  getBatches,
  getProcesses,
  getTrend,
  getVariables,
  type BatchDetail,
  type BatchOption,
  type ProcessOption,
  type TrendPoint,
  type VariableOption,
} from '@/api/demo'

const processes = ref<ProcessOption[]>([])
const batches = ref<BatchOption[]>([])
const variables = ref<VariableOption[]>([])
const batchDetail = ref<BatchDetail | null>(null)
const trendPoints = ref<TrendPoint[]>([])

const selectedProcessId = ref<number>()
const selectedBatchId = ref<string>()
const selectedVariableId = ref<string>()

const loadingProcessScoped = ref(false)
const loadingAnalysis = ref(false)
const trendChartRef = ref<HTMLDivElement>()

let trendChart: echarts.ECharts | null = null
let processRequestToken = 0
let analysisRequestToken = 0

const selectedVariableLabel = computed(() => {
  return variables.value.find((item) => item.id === selectedVariableId.value)?.variableName ?? '未选择数据类别'
})

const anomalyRows = computed(() => {
  return trendPoints.value.filter((item) => item.missing || item.statOutlier || item.physicalOutlier)
})

async function loadPage() {
  try {
    const response = await getProcesses()
    processes.value = response.data
    if (!processes.value.length) {
      return
    }
    selectedProcessId.value = processes.value[0].id
    await loadProcessScopedData(processes.value[0].id)
  } catch (error) {
    ElMessage.error('工序基础数据加载失败，请检查后端服务与数据库连接状态')
  }
}

async function loadProcessScopedData(processId: number) {
  const currentToken = ++processRequestToken
  loadingProcessScoped.value = true
  batchDetail.value = null
  trendPoints.value = []
  batches.value = []
  variables.value = []
  selectedBatchId.value = undefined
  selectedVariableId.value = undefined

  try {
    const batchResponse = await getBatches(processId)
    if (currentToken !== processRequestToken) {
      return
    }

    batches.value = batchResponse.data
    const firstBatchId = batches.value[0]?.id
    selectedBatchId.value = firstBatchId

    if (!firstBatchId) {
      await nextTick()
      renderTrendChart()
      return
    }

    await loadVariablesForBatch(processId, firstBatchId, currentToken)
  } catch (error) {
    ElMessage.error('批次列表加载失败，请检查工序数据是否已经完成初始化')
  } finally {
    if (currentToken === processRequestToken) {
      loadingProcessScoped.value = false
    }
  }
}

async function loadVariablesForBatch(processId: number, batchId: string, requestToken = processRequestToken) {
  try {
    const variableResponse = await getVariables(processId, batchId)
    if (requestToken !== processRequestToken) {
      return
    }

    const currentVariableId = selectedVariableId.value
    variables.value = variableResponse.data
    selectedVariableId.value = variables.value.find((item) => item.id === currentVariableId)?.id ?? variables.value[0]?.id

    if (!selectedVariableId.value) {
      batchDetail.value = null
      trendPoints.value = []
      await nextTick()
      renderTrendChart()
      return
    }

    await loadBatchAnalysis(processId, batchId, selectedVariableId.value)
  } catch (error) {
    ElMessage.error('数据类别加载失败，请检查该批次是否存在有效历史数据')
  }
}

async function loadBatchAnalysis(processId: number, batchId: string, variableId: string) {
  const currentToken = ++analysisRequestToken
  loadingAnalysis.value = true

  try {
    const [detailResponse, trendResponse] = await Promise.all([
      getBatchDetail(processId, batchId),
      getTrend(processId, batchId, variableId),
    ])
    if (currentToken !== analysisRequestToken) {
      return
    }

    batchDetail.value = detailResponse.data
    trendPoints.value = trendResponse.data
    await nextTick()
    renderTrendChart()
  } catch (error) {
    if (currentToken === analysisRequestToken) {
      batchDetail.value = null
      trendPoints.value = []
      await nextTick()
      renderTrendChart()
    }
    ElMessage.error('批次分析数据加载失败，请确认当前批次下的数据类别存在趋势数据')
  } finally {
    if (currentToken === analysisRequestToken) {
      loadingAnalysis.value = false
    }
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) {
    return
  }

  trendChart ??= echarts.init(trendChartRef.value)
  const hasData = trendPoints.value.length > 0
  const markPoints = trendPoints.value
    .map((item, index) => ({ item, index }))
    .filter(({ item }) => item.statOutlier || item.physicalOutlier)
    .map(({ item, index }) => ({
      coord: [item.collectTime, item.rawValue ?? item.cleanValue ?? 0],
      value: index + 1,
      itemStyle: { color: '#f08a8a' },
    }))

  trendChart.setOption({
    color: ['#5a9ff5', '#8ec5ff'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#d7e7fb',
      textStyle: { color: '#35526e' },
    },
    legend: {
      top: 0,
      textStyle: { color: '#5d7895' },
      data: ['原始值', '清洗值'],
    },
    grid: { left: 48, right: 24, top: 48, bottom: 42 },
    xAxis: {
      type: 'category',
      data: trendPoints.value.map((item) => item.collectTime),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#c9dced' } },
      axisLabel: { color: '#6b86a3' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6b86a3' },
      splitLine: { lineStyle: { color: '#e9f1fb' } },
    },
    graphic: hasData
      ? []
      : [
          {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '当前批次暂无可展示的趋势数据',
              fill: '#8da5bf',
              fontSize: 16,
            },
          },
        ],
    series: [
      {
        name: '原始值',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 3 },
        data: trendPoints.value.map((item) => item.rawValue),
        markPoint: {
          symbolSize: 34,
          data: markPoints,
        },
      },
      {
        name: '清洗值',
        type: 'line',
        smooth: true,
        symbolSize: 5,
        lineStyle: { width: 3 },
        data: trendPoints.value.map((item) => item.cleanValue),
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(142, 197, 255, 0.28)' },
            { offset: 1, color: 'rgba(142, 197, 255, 0.04)' },
          ]),
        },
      },
    ],
  })
}

function formatAnomalyType(point: TrendPoint) {
  const labels: string[] = []
  if (point.missing) {
    labels.push('缺失点')
  }
  if (point.statOutlier) {
    labels.push('统计异常')
  }
  if (point.physicalOutlier) {
    labels.push('物理异常')
  }
  return labels.join(' / ')
}

watch(selectedProcessId, async (processId, oldProcessId) => {
  if (!processId || oldProcessId === undefined || processId === oldProcessId) {
    return
  }
  await loadProcessScopedData(processId)
})

watch(selectedBatchId, async (batchId, oldBatchId) => {
  if (!selectedProcessId.value || !batchId || batchId === oldBatchId || loadingProcessScoped.value) {
    return
  }
  await loadVariablesForBatch(selectedProcessId.value, batchId)
})

watch(selectedVariableId, async (variableId, oldVariableId) => {
  if (!selectedProcessId.value || !selectedBatchId.value || !variableId || variableId === oldVariableId) {
    return
  }
  if (loadingProcessScoped.value) {
    return
  }
  await loadBatchAnalysis(selectedProcessId.value, selectedBatchId.value, variableId)
})

onMounted(() => {
  void loadPage()
})

onBeforeUnmount(() => {
  trendChart?.dispose()
  trendChart = null
})
</script>

<template>
  <div class="batch-grid">
    <el-card shadow="hover" class="panel-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>批次分析</strong>
            <span>按工序、批次和数据类别查看历史曲线</span>
          </div>
        </div>
      </template>
      <div class="filter-grid">
        <el-select v-model="selectedProcessId" placeholder="请选择工序" :loading="loadingProcessScoped">
          <el-option v-for="item in processes" :key="item.id" :label="item.processName" :value="item.id" />
        </el-select>
        <el-select v-model="selectedBatchId" placeholder="请选择批次" :loading="loadingProcessScoped">
          <el-option
            v-for="item in batches"
            :key="item.id"
            :label="`${item.batchCode} / 质量${item.qualityLevel}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="selectedVariableId" placeholder="请选择数据类别" :loading="loadingAnalysis">
          <el-option
            v-for="item in variables"
            :key="item.id"
            :label="item.variableName"
            :value="item.id"
          />
        </el-select>
      </div>
    </el-card>

    <section class="summary-grid">
      <div class="summary-card">
        <span>批次编号</span>
        <strong>{{ batchDetail?.batchCode ?? '--' }}</strong>
      </div>
      <div class="summary-card">
        <span>所属工序</span>
        <strong>{{ batchDetail?.processName ?? '--' }}</strong>
      </div>
      <div class="summary-card">
        <span>质量等级</span>
        <strong>{{ batchDetail?.qualityLevel ?? '--' }}</strong>
      </div>
      <div class="summary-card">
        <span>数据点总数</span>
        <strong>{{ batchDetail?.dataPointCount ?? 0 }}</strong>
      </div>
      <div class="summary-card">
        <span>异常点数量</span>
        <strong>{{ (batchDetail?.statOutlierCount ?? 0) + (batchDetail?.physicalOutlierCount ?? 0) }}</strong>
      </div>
      <div class="summary-card">
        <span>缺失点数量</span>
        <strong>{{ batchDetail?.missingCount ?? 0 }}</strong>
      </div>
    </section>

    <el-card shadow="hover" class="panel-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>{{ selectedVariableLabel }}</strong>
            <span>趋势图分析</span>
          </div>
        </div>
      </template>
      <div ref="trendChartRef" class="chart-box"></div>
      <div v-if="batchDetail" class="time-range">
        <span>开始时间：{{ batchDetail.startTime }}</span>
        <span>结束时间：{{ batchDetail.endTime }}</span>
      </div>
    </el-card>

    <el-card shadow="hover" class="panel-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>异常点明细</strong>
            <span>当前趋势曲线中的异常与缺失记录</span>
          </div>
        </div>
      </template>
      <el-table :data="anomalyRows" stripe empty-text="当前筛选条件下暂无异常记录">
        <el-table-column prop="collectTime" label="采集时间" min-width="160" />
        <el-table-column prop="rawValue" label="原始值" min-width="120" />
        <el-table-column prop="cleanValue" label="清洗值" min-width="120" />
        <el-table-column label="异常类型" min-width="180">
          <template #default="{ row }">
            <el-tag type="info" effect="light" class="light-tag">{{ formatAnomalyType(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.batch-grid {
  display: grid;
  gap: 20px;
}

.panel-card {
  border: 1px solid #d9e8f7;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header strong {
  display: block;
  color: var(--text-main);
  font-size: 16px;
}

.card-header span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid #d8e7f8;
  background: linear-gradient(180deg, #ffffff 0%, #f4f9ff 100%);
  box-shadow: var(--shadow-soft);
}

.summary-card span {
  color: var(--text-secondary);
  font-size: 13px;
}

.summary-card strong {
  color: #2f5f8f;
  font-size: 24px;
  line-height: 1.2;
}

.chart-box {
  height: 360px;
}

.time-range {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.light-tag {
  border-color: #cfe3f8;
  color: #4775a3;
  background: #f4f9ff;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .filter-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .time-range {
    display: grid;
    gap: 8px;
  }
}
</style>
