<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

interface GovernanceMetric {
  label: string
  value: string
  helper: string
  tone: 'blue' | 'green' | 'orange' | 'red'
}

interface GovernanceAsset {
  processName: string
  dataCategory: string
  owner: string
  score: number
  issueCount: number
  status: '健康' | '关注' | '整改中'
}

interface GovernanceTask {
  title: string
  department: string
  priority: '高' | '中' | '低'
  dueDate: string
  status: '待处理' | '处理中' | '待复核'
}

interface GovernanceRuleHit {
  ruleName: string
  target: string
  hitCount: number
  severity: '严重' | '较高' | '一般'
}

interface QualityTrendPoint {
  label: string
  score: number
  issueCount: number
}

interface IssueDistributionItem {
  name: string
  value: number
}

const metrics: GovernanceMetric[] = [
  { label: '数据资产', value: '128', helper: '覆盖 6 个生产工序', tone: 'blue' },
  { label: '治理评分', value: '91.6', helper: '较上周提升 2.4', tone: 'green' },
  { label: '质量问题', value: '36', helper: '今日新增 5 项', tone: 'orange' },
  { label: '待整改任务', value: '12', helper: '3 项临近截止', tone: 'red' },
]

const qualityTrend: QualityTrendPoint[] = [
  { label: '07-01', score: 87.2, issueCount: 46 },
  { label: '07-02', score: 88.5, issueCount: 42 },
  { label: '07-03', score: 89.1, issueCount: 39 },
  { label: '07-04', score: 90.4, issueCount: 35 },
  { label: '07-05', score: 89.8, issueCount: 37 },
  { label: '07-06', score: 91.0, issueCount: 31 },
  { label: '07-07', score: 91.6, issueCount: 29 },
]

const issueDistribution: IssueDistributionItem[] = [
  { name: '缺失值', value: 12 },
  { name: '异常值', value: 9 },
  { name: '标准不一致', value: 8 },
  { name: '采集延迟', value: 7 },
]

const governanceAssets: GovernanceAsset[] = [
  { processName: '制丝工序', dataCategory: '入口水分', owner: '工艺质量组', score: 96, issueCount: 1, status: '健康' },
  { processName: '制丝工序', dataCategory: '热风温度', owner: '设备运行组', score: 89, issueCount: 5, status: '关注' },
  { processName: '卷包工序', dataCategory: '剔除率', owner: '卷包车间', score: 84, issueCount: 7, status: '整改中' },
  { processName: '发酵工序', dataCategory: '环境湿度', owner: '仓储管理组', score: 92, issueCount: 3, status: '健康' },
  { processName: '质检工序', dataCategory: '感官评分', owner: '质量检测组', score: 88, issueCount: 4, status: '关注' },
]

const governanceTasks: GovernanceTask[] = [
  { title: '补齐制丝热风温度采集缺口', department: '设备运行组', priority: '高', dueDate: '2026-07-09', status: '处理中' },
  { title: '统一卷包剔除率字段口径', department: '卷包车间', priority: '高', dueDate: '2026-07-10', status: '待处理' },
  { title: '复核质检感官评分异常批次', department: '质量检测组', priority: '中', dueDate: '2026-07-12', status: '待复核' },
  { title: '完善发酵环境湿度责任人信息', department: '仓储管理组', priority: '低', dueDate: '2026-07-15', status: '待处理' },
]

const ruleHits: GovernanceRuleHit[] = [
  { ruleName: '关键指标不能为空', target: '入口水分 / 热风温度', hitCount: 12, severity: '较高' },
  { ruleName: '物理上下限校验', target: '环境湿度 / 烘丝温度', hitCount: 9, severity: '严重' },
  { ruleName: '同批次采集频率一致性', target: '卷包剔除率', hitCount: 8, severity: '较高' },
  { ruleName: '数据类别命名规范', target: '质检评分字段', hitCount: 7, severity: '一般' },
]

const trendChartRef = ref<HTMLDivElement>()
const distributionChartRef = ref<HTMLDivElement>()

let trendChart: echarts.ECharts | null = null
let distributionChart: echarts.ECharts | null = null

const averageScore = computed(() => {
  const total = governanceAssets.reduce((sum, item) => sum + item.score, 0)
  return Math.round(total / governanceAssets.length)
})

function getAssetStatusType(status: GovernanceAsset['status']) {
  if (status === '健康') {
    return 'success'
  }
  if (status === '整改中') {
    return 'warning'
  }
  return 'primary'
}

function getPriorityType(priority: GovernanceTask['priority']) {
  if (priority === '高') {
    return 'danger'
  }
  if (priority === '中') {
    return 'warning'
  }
  return 'info'
}

function getTaskStatusType(status: GovernanceTask['status']) {
  if (status === '处理中') {
    return 'primary'
  }
  if (status === '待复核') {
    return 'success'
  }
  return 'warning'
}

function getSeverityType(severity: GovernanceRuleHit['severity']) {
  if (severity === '严重') {
    return 'danger'
  }
  if (severity === '较高') {
    return 'warning'
  }
  return 'info'
}

function renderTrendChart() {
  if (!trendChartRef.value) {
    return
  }

  trendChart ??= echarts.init(trendChartRef.value)
  trendChart.setOption({
    color: ['#4a90e2', '#f59e0b'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      top: 0,
      data: ['治理评分', '问题数'],
      textStyle: { color: '#5f7c9d' },
    },
    grid: { left: 42, right: 42, top: 46, bottom: 36 },
    xAxis: {
      type: 'category',
      data: qualityTrend.map((item) => item.label),
      axisLine: { lineStyle: { color: '#c9d9ea' } },
      axisLabel: { color: '#6b85a3' },
    },
    yAxis: [
      {
        type: 'value',
        min: 80,
        max: 100,
        axisLabel: { color: '#6b85a3' },
        splitLine: { lineStyle: { color: '#eaf1f8' } },
      },
      {
        type: 'value',
        axisLabel: { color: '#6b85a3' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '治理评分',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: qualityTrend.map((item) => item.score),
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(74, 144, 226, 0.24)' },
            { offset: 1, color: 'rgba(74, 144, 226, 0.03)' },
          ]),
        },
      },
      {
        name: '问题数',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 26,
        data: qualityTrend.map((item) => item.issueCount),
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: '#f59e0b',
        },
      },
    ],
  })
}

function renderDistributionChart() {
  if (!distributionChartRef.value) {
    return
  }

  distributionChart ??= echarts.init(distributionChartRef.value)
  distributionChart.setOption({
    color: ['#4a90e2', '#ef6f6c', '#f5a623', '#35b779'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      bottom: 0,
      textStyle: { color: '#5f7c9d' },
    },
    series: [
      {
        name: '问题分布',
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 3,
        },
        label: {
          color: '#476482',
          formatter: '{b}: {c}',
        },
        data: issueDistribution,
      },
    ],
  })
}

function resizeCharts() {
  trendChart?.resize()
  distributionChart?.resize()
}

onMounted(async () => {
  await nextTick()
  renderTrendChart()
  renderDistributionChart()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  distributionChart?.dispose()
  trendChart = null
  distributionChart = null
})
</script>

<template>
  <div class="governance-page">
    <section class="page-banner">
      <div class="banner-copy">
        <span class="banner-tag">DATA GOVERNANCE</span>
        <h2>数据治理驾驶舱</h2>
        <p>
          面向烟草制造历史数据的轻量治理演示，集中呈现数据资产、质量评分、规则命中和整改任务，帮助业务人员快速定位重点问题。
        </p>
      </div>
      <div class="banner-score">
        <span>资产平均质量分</span>
        <strong>{{ averageScore }}</strong>
        <small>重点资产整体处于可控状态</small>
      </div>
    </section>

    <section class="metric-grid">
      <div v-for="item in metrics" :key="item.label" class="metric-card" :class="`tone-${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.helper }}</small>
      </div>
    </section>

    <section class="chart-grid">
      <el-card shadow="hover">
        <template #header>质量趋势</template>
        <div ref="trendChartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="hover">
        <template #header>问题分布</template>
        <div ref="distributionChartRef" class="chart-box"></div>
      </el-card>
    </section>

    <el-card shadow="hover">
      <template #header>
        <div class="section-header">
          <span>重点数据资产</span>
          <el-tag type="primary" effect="plain">治理监控中</el-tag>
        </div>
      </template>
      <el-table :data="governanceAssets" row-key="dataCategory" stripe>
        <el-table-column prop="processName" label="所属工序" min-width="130" />
        <el-table-column prop="dataCategory" label="数据类别" min-width="160" />
        <el-table-column prop="owner" label="责任人/部门" min-width="150" />
        <el-table-column prop="score" label="质量分" width="110">
          <template #default="{ row }">
            <strong class="score-text">{{ row.score }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="issueCount" label="问题数" width="110" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getAssetStatusType(row.status)" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <section class="table-grid">
      <el-card shadow="hover">
        <template #header>治理任务</template>
        <el-table :data="governanceTasks" row-key="title">
          <el-table-column prop="title" label="问题描述" min-width="220" />
          <el-table-column prop="department" label="责任部门" min-width="130" />
          <el-table-column prop="priority" label="优先级" width="100">
            <template #default="{ row }">
              <el-tag :type="getPriorityType(row.priority)" effect="plain">{{ row.priority }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="dueDate" label="截止时间" min-width="120" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getTaskStatusType(row.status)" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="hover">
        <template #header>规则命中</template>
        <el-table :data="ruleHits" row-key="ruleName">
          <el-table-column prop="ruleName" label="规则名称" min-width="180" />
          <el-table-column prop="target" label="适用对象" min-width="150" />
          <el-table-column prop="hitCount" label="命中次数" width="100" />
          <el-table-column prop="severity" label="严重程度" width="110">
            <template #default="{ row }">
              <el-tag :type="getSeverityType(row.severity)" effect="plain">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.governance-page {
  display: grid;
  gap: 20px;
}

.page-banner {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: stretch;
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
  max-width: 780px;
  margin: 0;
  line-height: 1.7;
  color: var(--text-secondary);
}

.banner-score {
  min-width: 220px;
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 22px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid var(--line-color);
}

.banner-score span,
.banner-score small,
.metric-card span,
.metric-card small {
  color: var(--text-secondary);
}

.banner-score strong {
  font-size: 42px;
  color: #35a66a;
  line-height: 1;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  display: grid;
  gap: 10px;
  padding: 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid var(--line-color);
  box-shadow: var(--shadow-soft);
}

.metric-card strong {
  font-size: 30px;
  line-height: 1;
}

.tone-blue strong {
  color: var(--primary-color);
}

.tone-green strong {
  color: #35a66a;
}

.tone-orange strong {
  color: #f59e0b;
}

.tone-red strong {
  color: #e06666;
}

.chart-grid,
.table-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.chart-box {
  height: 340px;
  border-radius: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.score-text {
  color: var(--primary-color);
  font-size: 18px;
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid,
  .table-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .page-banner {
    display: grid;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .banner-score {
    min-width: 0;
  }
}
</style>
