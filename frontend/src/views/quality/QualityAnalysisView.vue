<script setup lang="ts">
/**
 * 文件名称：QualityAnalysisView
 * 文件说明：正式质量分析页面，负责展示 PostgreSQL 专题库下不同质量等级的分层统计与批次明细。
 * 主要职责：
 * 1. 加载工序与指标筛选数据。
 * 2. 展示质量摘要与分层图表。
 * 3. 展示批次级质量明细表。
 * 开发者：czd
 */
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  getProcesses,
  getQualityAnalysis,
  getVariables,
  type ProcessOption,
  type QualityAnalysis,
  type QualityLevelStatistic,
  type VariableOption,
} from '@/api/demo'

const processes = ref<ProcessOption[]>([])
const variables = ref<VariableOption[]>([])
const qualityAnalysis = ref<QualityAnalysis | null>(null)

const selectedProcessId = ref<number>()
const selectedVariableId = ref<string>()

const levelChartRef = ref<HTMLDivElement>()
const batchChartRef = ref<HTMLDivElement>()
let levelChart: echarts.ECharts | null = null
let batchChart: echarts.ECharts | null = null

const qualityMetricCards = computed(() => {
  if (!qualityAnalysis.value) {
    return []
  }
  return [
    { label: '分析批次数', value: qualityAnalysis.value.totalBatchCount },
    { label: '覆盖数据点', value: qualityAnalysis.value.totalDataPointCount },
    { label: '平均均值', value: qualityAnalysis.value.avgMeanValue.toFixed(2) },
    { label: '平均波动', value: qualityAnalysis.value.avgStdValue.toFixed(2) },
    { label: '平均缺失率', value: formatPercent(qualityAnalysis.value.avgMissingRate) },
    { label: '平均统计异常率', value: formatPercent(qualityAnalysis.value.avgStatOutlierRate) },
  ]
})

const analysisTitle = computed(() => {
  if (!qualityAnalysis.value) {
    return '--'
  }
  return `${qualityAnalysis.value.processName} / ${qualityAnalysis.value.variableName}`
})

async function loadPage() {
  try {
    const processResponse = await getProcesses()
    processes.value = processResponse.data
    if (processes.value.length > 0) {
      selectedProcessId.value = processes.value[0].id
      await loadVariablesAndAnalysis(processes.value[0].id)
    }
  } catch (error) {
    ElMessage.error('质量分析页面初始化失败，请确认 PostgreSQL 和后端服务已启动')
  }
}

async function loadVariablesAndAnalysis(processId: number) {
  // 切换工序后先刷新指标列表，再默认加载第一项指标的正式库统计结果。
  const variableResponse = await getVariables(processId)
  variables.value = variableResponse.data
  selectedVariableId.value = variables.value[0]?.id
  if (selectedVariableId.value) {
    await loadQualityAnalysis(processId, selectedVariableId.value)
  } else {
    qualityAnalysis.value = null
    await nextTick()
    renderCharts()
  }
}

async function loadQualityAnalysis(processId: number, variableId: string) {
  // 质量分析接口一次性返回摘要、分层和批次明细，前端只做展示与轻量格式化。
  const response = await getQualityAnalysis(processId, variableId)
  qualityAnalysis.value = response.data
  await nextTick()
  renderCharts()
}

function renderCharts() {
  renderLevelChart()
  renderBatchChart()
}

function renderLevelChart() {
  if (!levelChartRef.value) return
  levelChart ??= echarts.init(levelChartRef.value)
  const levelStats = qualityAnalysis.value?.qualityLevelStatistics ?? []

  levelChart.setOption({
    color: ['#6ba7ea', '#8fc3f5'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      top: 0,
      data: ['平均均值', '平均波动'],
      textStyle: { color: '#5f7c9d' },
    },
    grid: { left: 44, right: 24, top: 52, bottom: 36 },
    xAxis: {
      type: 'category',
      data: levelStats.map((item) => `等级 ${item.qualityLevel}`),
      axisLine: { lineStyle: { color: '#c9d9ea' } },
      axisLabel: { color: '#6b85a3' },
    },
    yAxis: [
      {
        type: 'value',
        name: '平均均值',
        nameTextStyle: { color: '#6b85a3' },
        axisLabel: { color: '#6b85a3' },
        splitLine: { lineStyle: { color: '#eaf1f8' } },
      },
      {
        type: 'value',
        name: '平均波动',
        nameTextStyle: { color: '#6b85a3' },
        axisLabel: { color: '#6b85a3' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '平均均值',
        type: 'bar',
        barMaxWidth: 42,
        data: levelStats.map((item) => item.avgMeanValue),
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#9bcbf7' },
            { offset: 1, color: '#5b9ee6' },
          ]),
        },
      },
      {
        name: '平均波动',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 8,
        data: levelStats.map((item) => item.avgStdValue),
        lineStyle: { width: 3, color: '#4a90e2' },
        itemStyle: { color: '#4a90e2' },
      },
    ],
  })
}

function renderBatchChart() {
  if (!batchChartRef.value) return
  batchChart ??= echarts.init(batchChartRef.value)
  const batchStats = qualityAnalysis.value?.batchStatistics ?? []

  batchChart.setOption({
    color: ['#5b9ee6', '#78aeea'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      top: 0,
      data: ['缺失率', '统计异常率'],
      textStyle: { color: '#5f7c9d' },
    },
    grid: { left: 44, right: 24, top: 52, bottom: 56 },
    xAxis: {
      type: 'category',
      data: batchStats.map((item) => item.batchCode),
      axisLine: { lineStyle: { color: '#c9d9ea' } },
      axisLabel: {
        color: '#6b85a3',
        rotate: 18,
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#6b85a3',
        formatter: (value: number) => `${Math.round(value * 100)}%`,
      },
      splitLine: { lineStyle: { color: '#eaf1f8' } },
    },
    series: [
      {
        name: '缺失率',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: batchStats.map((item) => item.missingRate),
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(91, 158, 230, 0.26)' },
            { offset: 1, color: 'rgba(91, 158, 230, 0.04)' },
          ]),
        },
      },
      {
        name: '统计异常率',
        type: 'bar',
        barMaxWidth: 34,
        data: batchStats.map((item) => item.statOutlierRate),
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#b7d8f7' },
            { offset: 1, color: '#78aeea' },
          ]),
        },
      },
    ],
  })
}

function qualityTagType(level: string) {
  if (level === 'A') return 'success'
  if (level === 'B') return 'primary'
  return 'warning'
}

function formatPercent(value: number) {
  return `${(value * 100).toFixed(2)}%`
}

function formatLevelMetrics(row: QualityLevelStatistic) {
  return `批次 ${row.batchCount} 个 / 缺失率 ${formatPercent(row.avgMissingRate)} / 统计异常率 ${formatPercent(row.avgStatOutlierRate)}`
}

watch(selectedProcessId, async (processId, oldProcessId) => {
  if (!processId || oldProcessId === undefined || processId === oldProcessId) return
  await loadVariablesAndAnalysis(processId)
})

watch(selectedVariableId, async (variableId, oldVariableId) => {
  if (!selectedProcessId.value || !variableId) return
  if (variableId === oldVariableId) return
  await loadQualityAnalysis(selectedProcessId.value, variableId)
})

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <div class="quality-grid">
    <el-card shadow="hover">
      <template #header>质量分析筛选</template>
      <div class="filter-grid">
        <el-select v-model="selectedProcessId" placeholder="选择工序">
          <el-option v-for="item in processes" :key="item.id" :label="item.processName" :value="item.id" />
        </el-select>
        <el-select v-model="selectedVariableId" placeholder="选择指标">
          <el-option
            v-for="item in variables"
            :key="item.id"
            :label="`${item.variableName} (${item.unit})`"
            :value="item.id"
          />
        </el-select>
      </div>
    </el-card>

    <section class="hero-card" v-if="qualityAnalysis">
      <div class="hero-copy">
        <span class="hero-tag">Quality Layering</span>
        <h2>{{ analysisTitle }}</h2>
        <p>
          当前质量分析从质量等级、批次波动、缺失率和统计异常率几个维度观察历史表现，
          用于快速识别同一指标在不同质量层级下的稳定性差异。
        </p>
      </div>
      <div class="hero-levels">
        <div
          v-for="item in qualityAnalysis.qualityLevelStatistics"
          :key="item.qualityLevel"
          class="level-card"
        >
          <div class="level-header">
            <el-tag :type="qualityTagType(item.qualityLevel)" effect="light">等级 {{ item.qualityLevel }}</el-tag>
            <strong>{{ item.avgMeanValue.toFixed(2) }}</strong>
          </div>
          <p>{{ formatLevelMetrics(item) }}</p>
        </div>
      </div>
    </section>

    <section class="summary-grid">
      <div v-for="item in qualityMetricCards" :key="item.label" class="summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </section>

    <section class="chart-grid">
      <el-card shadow="hover">
        <template #header>质量等级分层对比</template>
        <div ref="levelChartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="hover">
        <template #header>批次缺失与异常率</template>
        <div ref="batchChartRef" class="chart-box"></div>
      </el-card>
    </section>

    <el-card shadow="hover">
      <template #header>批次质量明细</template>
      <el-table :data="qualityAnalysis?.batchStatistics ?? []" stripe>
        <el-table-column prop="batchCode" label="批次编号" min-width="160" />
        <el-table-column label="质量等级" min-width="110">
          <template #default="{ row }">
            <el-tag :type="qualityTagType(row.qualityLevel)" effect="light">等级 {{ row.qualityLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="meanValue" label="均值" />
        <el-table-column prop="stdValue" label="标准差" />
        <el-table-column label="缺失率">
          <template #default="{ row }">{{ formatPercent(row.missingRate) }}</template>
        </el-table-column>
        <el-table-column label="统计异常率">
          <template #default="{ row }">{{ formatPercent(row.statOutlierRate) }}</template>
        </el-table-column>
        <el-table-column label="物理异常率">
          <template #default="{ row }">{{ formatPercent(row.physicalOutlierRate) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.quality-grid {
  display: grid;
  gap: 20px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.hero-card {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 20px;
  padding: 26px;
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
  font-size: 30px;
  color: var(--text-main);
}

.hero-copy p {
  margin: 0;
  line-height: 1.75;
  color: var(--text-secondary);
}

.hero-levels {
  display: grid;
  gap: 14px;
}

.level-card {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--line-color);
  background: rgba(255, 255, 255, 0.92);
}

.level-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.level-header strong {
  color: var(--text-main);
  font-size: 26px;
}

.level-card p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.6;
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
  border: 1px solid var(--line-color);
  background: #ffffff;
  box-shadow: var(--shadow-soft);
}

.summary-card span {
  color: var(--text-secondary);
  font-size: 13px;
}

.summary-card strong {
  color: var(--primary-color);
  font-size: 24px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.chart-box {
  height: 360px;
}

@media (max-width: 1200px) {
  .hero-card,
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .filter-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
