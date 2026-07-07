<script setup lang="ts">
/**
 * 文件名称：HomeView
 * 文件说明：系统首页，负责展示正式库概览、首页分析筛选、单批次趋势图、多批次对比图以及批次统计表。
 * 业务职责：
 * 1. 首页筛选必须遵循工序 -> 批次 -> 数据类别的业务链路，避免不同批次间不存在的数据类别被错误组合查询。
 * 2. 当工序或批次切换时，自动刷新当前批次下可用的数据类别，并重新拉取趋势与对比数据。
 * 3. 对无数据场景进行显式提示，确保首页分析结果对业务人员可理解、可判断。
 * 开发者：czd
 */
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  getBatchStatistics,
  getBatches,
  getComparison,
  getOverview,
  getProcesses,
  getTrend,
  getVariables,
  type BatchOption,
  type BatchStatistic,
  type ComparisonPoint,
  type DemoOverview,
  type ProcessOption,
  type TrendPoint,
  type VariableOption,
} from '@/api/demo'
import { getHealth } from '@/api/system'
import { useSystemStore } from '@/stores/system'

const systemStore = useSystemStore()

const healthStatus = ref('检测中...')
const overview = ref<DemoOverview | null>(null)
const processes = ref<ProcessOption[]>([])
const batches = ref<BatchOption[]>([])
const variables = ref<VariableOption[]>([])
const trendPoints = ref<TrendPoint[]>([])
const comparisonPoints = ref<ComparisonPoint[]>([])
const batchStatistics = ref<BatchStatistic[]>([])

const selectedProcessId = ref<number>()
const selectedBatchId = ref<string>()
const selectedVariableId = ref<string>()

const loadingProcessScoped = ref(false)
const loadingChartData = ref(false)

const trendChartRef = ref<HTMLDivElement>()
const comparisonChartRef = ref<HTMLDivElement>()

let trendChart: echarts.ECharts | null = null
let comparisonChart: echarts.ECharts | null = null
let processRequestToken = 0
let chartRequestToken = 0

const selectedVariableLabel = computed(() => {
  return variables.value.find((item) => item.id === selectedVariableId.value)?.variableName ?? '未选择数据类别'
})

async function loadPage() {
  try {
    const [health, , overviewResponse, processResponse] = await Promise.all([
      getHealth(),
      systemStore.fetchInfo(),
      getOverview(),
      getProcesses(),
    ])

    healthStatus.value = health.data.status
    overview.value = overviewResponse.data
    processes.value = processResponse.data

    if (!processes.value.length) {
      return
    }

    selectedProcessId.value = processes.value[0].id
    await loadProcessScopedData(processes.value[0].id)
  } catch (error) {
    healthStatus.value = 'DOWN'
    ElMessage.error('首页初始化失败，请检查后端服务、数据库连接和正式库初始化状态')
  }
}

async function loadProcessScopedData(processId: number) {
  const currentToken = ++processRequestToken
  loadingProcessScoped.value = true
  batches.value = []
  variables.value = []
  trendPoints.value = []
  comparisonPoints.value = []
  batchStatistics.value = []
  selectedBatchId.value = undefined
  selectedVariableId.value = undefined

  try {
    const [batchResponse, statisticResponse] = await Promise.all([
      getBatches(processId),
      getBatchStatistics(processId),
    ])

    if (currentToken !== processRequestToken) {
      return
    }

    batches.value = batchResponse.data
    batchStatistics.value = statisticResponse.data
    selectedBatchId.value = batches.value[0]?.id

    if (!selectedBatchId.value) {
      await nextTick()
      renderTrendChart()
      renderComparisonChart()
      return
    }

    await loadVariablesForBatch(processId, selectedBatchId.value, currentToken)
  } catch (error) {
    ElMessage.error('首页筛选数据加载失败，请确认当前工序已完成正式数据导入')
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

    const previousVariableId = selectedVariableId.value
    variables.value = variableResponse.data
    selectedVariableId.value = variables.value.find((item) => item.id === previousVariableId)?.id ?? variables.value[0]?.id

    if (!selectedVariableId.value) {
      trendPoints.value = []
      comparisonPoints.value = []
      await nextTick()
      renderTrendChart()
      renderComparisonChart()
      return
    }

    await loadCharts(processId, selectedVariableId.value, batchId)
  } catch (error) {
    ElMessage.error('当前批次下的数据类别加载失败，请检查该批次是否存在有效历史数据')
  }
}

async function loadCharts(processId: number, variableId: string, batchId: string) {
  const currentToken = ++chartRequestToken
  loadingChartData.value = true

  try {
    const [trendResponse, comparisonResponse] = await Promise.all([
      getTrend(processId, batchId, variableId),
      getComparison(processId, variableId),
    ])

    if (currentToken !== chartRequestToken) {
      return
    }

    trendPoints.value = trendResponse.data
    comparisonPoints.value = comparisonResponse.data
    await nextTick()
    renderTrendChart()
    renderComparisonChart()
  } catch (error) {
    if (currentToken === chartRequestToken) {
      trendPoints.value = []
      comparisonPoints.value = []
      await nextTick()
      renderTrendChart()
      renderComparisonChart()
    }
    ElMessage.error('首页分析图表加载失败，请确认当前筛选条件存在可分析的数据点')
  } finally {
    if (currentToken === chartRequestToken) {
      loadingChartData.value = false
    }
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) {
    return
  }

  trendChart ??= echarts.init(trendChartRef.value)
  const hasData = trendPoints.value.length > 0

  trendChart.setOption({
    color: ['#5b9ee6', '#8fc3f5'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      top: 0,
      data: ['原始值', '清洗值'],
      textStyle: { color: '#5f7c9d' },
    },
    grid: { left: 40, right: 20, top: 44, bottom: 40 },
    xAxis: {
      type: 'category',
      data: trendPoints.value.map((item) => item.collectTime),
      axisLine: { lineStyle: { color: '#c9d9ea' } },
      axisLabel: { color: '#6b85a3' },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: '#6b85a3' },
      splitLine: { lineStyle: { color: '#eaf1f8' } },
    },
    graphic: hasData
      ? []
      : [
          {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '当前批次下暂无可展示的趋势数据',
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
        data: trendPoints.value.map((item) => item.rawValue),
        lineStyle: { width: 3 },
        symbolSize: 7,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(91, 158, 230, 0.28)' },
            { offset: 1, color: 'rgba(91, 158, 230, 0.03)' },
          ]),
        },
      },
      {
        name: '清洗值',
        type: 'line',
        smooth: true,
        data: trendPoints.value.map((item) => item.cleanValue),
        lineStyle: { width: 3 },
        symbolSize: 6,
      },
    ],
  })
}

function renderComparisonChart() {
  if (!comparisonChartRef.value) {
    return
  }

  comparisonChart ??= echarts.init(comparisonChartRef.value)
  const hasData = comparisonPoints.value.length > 0

  comparisonChart.setOption({
    color: ['#78aeea', '#4a90e2'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      top: 0,
      data: ['均值', '标准差'],
      textStyle: { color: '#5f7c9d' },
    },
    grid: { left: 40, right: 20, top: 44, bottom: 40 },
    xAxis: {
      type: 'category',
      data: comparisonPoints.value.map((item) => item.batchCode),
      axisLine: { lineStyle: { color: '#c9d9ea' } },
      axisLabel: { color: '#6b85a3' },
    },
    yAxis: [
      {
        type: 'value',
        name: '均值',
        nameTextStyle: { color: '#6b85a3' },
        axisLabel: { color: '#6b85a3' },
        splitLine: { lineStyle: { color: '#eaf1f8' } },
      },
      {
        type: 'value',
        name: '标准差',
        nameTextStyle: { color: '#6b85a3' },
        axisLabel: { color: '#6b85a3' },
        splitLine: { show: false },
      },
    ],
    graphic: hasData
      ? []
      : [
          {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '当前数据类别暂无多批次对比结果',
              fill: '#8da5bf',
              fontSize: 16,
            },
          },
        ],
    series: [
      {
        name: '均值',
        type: 'bar',
        data: comparisonPoints.value.map((item) => item.meanValue),
        barMaxWidth: 36,
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#94c0f1' },
            { offset: 1, color: '#5b9ee6' },
          ]),
        },
      },
      {
        name: '标准差',
        type: 'line',
        yAxisIndex: 1,
        data: comparisonPoints.value.map((item) => item.stdValue),
        lineStyle: { width: 3, color: '#4a90e2' },
        itemStyle: { color: '#4a90e2' },
        symbolSize: 7,
        smooth: true,
      },
    ],
  })
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
  await loadCharts(selectedProcessId.value, variableId, selectedBatchId.value)
})

onMounted(() => {
  void loadPage()
})

onBeforeUnmount(() => {
  trendChart?.dispose()
  comparisonChart?.dispose()
  trendChart = null
  comparisonChart = null
})
</script>

<template>
  <div class="home-grid">
    <section class="hero-card">
      <div class="hero-copy">
        <span class="hero-tag">PostgreSQL Ready</span>
        <h2>烟草制造历史数据分析与可视化总览</h2>
        <p>
          首页聚焦正式库历史数据的快速浏览与筛选分析，帮助业务人员从工序、批次和数据类别三个维度快速定位问题批次与关键指标。
        </p>
      </div>
      <div class="hero-metrics">
        <div class="metric-card">
          <span class="metric-label">服务状态</span>
          <strong>{{ healthStatus }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">系统名称</span>
          <strong>{{ systemStore.info?.systemName ?? '--' }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">运行环境</span>
          <strong>{{ systemStore.info?.environment ?? '--' }}</strong>
        </div>
        <div class="metric-card">
          <span class="metric-label">接入工序</span>
          <strong>{{ overview?.processCount ?? 0 }}</strong>
        </div>
      </div>
    </section>

    <section class="stats-grid">
      <el-card shadow="hover">
        <template #header>正式库数据概览</template>
        <div class="stats-row">
          <div><strong>{{ overview?.processCount ?? 0 }}</strong><span>工序</span></div>
          <div><strong>{{ overview?.variableCount ?? 0 }}</strong><span>指标</span></div>
          <div><strong>{{ overview?.batchCount ?? 0 }}</strong><span>批次</span></div>
          <div><strong>{{ overview?.dataPointCount ?? 0 }}</strong><span>数据点</span></div>
        </div>
        <div class="process-tags">
          <el-tag v-for="name in overview?.processNames ?? []" :key="name" effect="plain">{{ name }}</el-tag>
        </div>
      </el-card>

      <el-card shadow="hover">
        <template #header>分析筛选</template>
        <div class="filter-grid">
          <el-select v-model="selectedProcessId" placeholder="请选择工序" :loading="loadingProcessScoped">
            <el-option v-for="item in processes" :key="item.id" :label="item.processName" :value="item.id" />
          </el-select>
          <el-select v-model="selectedVariableId" placeholder="请选择数据类别" :loading="loadingChartData">
            <el-option
              v-for="item in variables"
              :key="item.id"
              :label="item.variableName"
              :value="item.id"
            />
          </el-select>
          <el-select v-model="selectedBatchId" placeholder="请选择批次" :loading="loadingProcessScoped">
            <el-option
              v-for="item in batches"
              :key="item.id"
              :label="`${item.batchCode} / ${item.qualityLevel}`"
              :value="item.id"
            />
          </el-select>
        </div>
      </el-card>
    </section>

    <section class="chart-grid">
      <el-card shadow="hover">
        <template #header>{{ selectedVariableLabel }} 单批次趋势</template>
        <div ref="trendChartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="hover">
        <template #header>{{ selectedVariableLabel }} 多批次对比</template>
        <div ref="comparisonChartRef" class="chart-box"></div>
      </el-card>
    </section>

    <el-card shadow="hover">
      <template #header>批次统计明细</template>
      <el-table :data="batchStatistics" stripe>
        <el-table-column prop="batchCode" label="批次" min-width="160" />
        <el-table-column prop="variableName" label="指标" min-width="220" />
        <el-table-column prop="meanValue" label="均值" />
        <el-table-column prop="stdValue" label="标准差" />
        <el-table-column prop="minValue" label="最小值" />
        <el-table-column prop="maxValue" label="最大值" />
        <el-table-column prop="missingRate" label="缺失率" />
        <el-table-column prop="statOutlierRate" label="统计异常率" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.home-grid {
  display: grid;
  gap: 20px;
}

.hero-card {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
  padding: 28px;
  border-radius: 24px;
  background: linear-gradient(180deg, #ffffff 0%, #f3f9ff 100%);
  border: 1px solid var(--line-color);
  box-shadow: var(--shadow-soft);
}

.hero-tag {
  display: inline-block;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary-color);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h2 {
  margin: 18px 0 12px;
  font-size: 34px;
  color: var(--text-main);
}

.hero-copy p {
  margin: 0;
  line-height: 1.7;
  color: var(--text-secondary);
}

.hero-metrics {
  display: grid;
  gap: 14px;
}

.metric-card {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid var(--line-color);
}

.metric-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.metric-card strong {
  font-size: 24px;
  color: var(--text-main);
}

.stats-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stats-row div {
  display: grid;
  gap: 8px;
  padding: 16px;
  border-radius: 16px;
  background: var(--panel-soft);
  border: 1px solid var(--line-color);
}

.stats-row strong {
  font-size: 26px;
  color: var(--primary-color);
}

.stats-row span {
  color: var(--text-secondary);
}

.process-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.filter-grid {
  display: grid;
  gap: 14px;
}

.chart-box {
  height: 340px;
  border-radius: 16px;
}

@media (max-width: 1100px) {
  .hero-card,
  .stats-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
