<script setup lang="ts">
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

type ValidityLabel = '料头' | '正常' | '料尾'
type StepKey = 'imported' | 'fields' | 'rules' | 'compare' | 'dataset'

interface RawPoint {
  time: string
  value: number | null
  validity: ValidityLabel
  batch: string
}

interface MetricSample {
  name: string
  unit: string
  category: '流量' | '含水率' | '温度' | '比例' | '精度'
  physicalMin: number
  physicalMax: number
  points: RawPoint[]
}

interface ProcessSample {
  name: string
  fileCount: number
  batchCount: number
  rowsPerBatch: number
  metricCount: number
  sourceFolder: string
  batches: string[]
  metrics: MetricSample[]
}

interface CleanPoint extends RawPoint {
  cleanValue: number | null
  issues: string[]
  action: string
  retained: boolean
}

const steps: Array<{ key: StepKey; title: string; desc: string }> = [
  { key: 'imported', title: '已导入数据', desc: '选择 07230 批次' },
  { key: 'fields', title: '字段识别', desc: '宽表转长表' },
  { key: 'rules', title: '规则清洗', desc: '配置处理策略' },
  { key: 'compare', title: '可视化对比', desc: '前后曲线验证' },
  { key: 'dataset', title: '建模数据集', desc: 'DeepAR / TFT 准备' },
]

const pipelineNodes = [
  { title: '原始 Excel', desc: '07230 宽表文件' },
  { title: '字段识别', desc: '时间/指标/有效性/批次' },
  { title: '暂存数据', desc: '保留原始值和来源行' },
  { title: '规则清洗', desc: '过滤/修复/标记' },
  { title: '质量评估', desc: '可用率和问题明细' },
  { title: '建模数据集', desc: 'DeepAR / TFT 输入' },
]

const batchOptions = [
  '07230-4-A-2501-023',
  '07230-4-A-2501-024',
  '07230-4-A-2502-010',
  '07230-4-A-2502-011',
  '07230-4-A-2503-015',
  '07230-4-A-2503-016',
  '07230-4-A-2504-010',
  '07230-4-A-2504-020',
]

const activeStep = ref<StepKey>('imported')
const selectedProcessName = ref('叶片加料')
const selectedBatch = ref('07230-4-A-2501-024')
const selectedMetricName = ref('A叶片加料加料前入口含水率')
const previewRunCount = ref(0)
const datasetPublished = ref(false)
const chartRef = ref<HTMLDivElement>()

const rules = reactive({
  filterValidity: true,
  fixNegative: true,
  detectZeroStop: true,
  detectOutlier: true,
  repairAbnormal: true,
})

let compareChart: echarts.ECharts | null = null

function makePoints(batch: string, config: {
  start: number
  step: number
  base: number
  wave: number
  trend?: number
  negativeIndexes?: number[]
  zeroIndexes?: number[]
  spikeIndexes?: number[]
  missingIndexes?: number[]
}): RawPoint[] {
  return Array.from({ length: 28 }, (_, index) => {
    const minute = config.start + index * config.step
    const hh = 16 + Math.floor(minute / 60)
    const mm = minute % 60
    const validity: ValidityLabel = index < 4 ? '料头' : index > 23 ? '料尾' : '正常'
    let value: number | null = Number(
      (config.base + Math.sin(index / 2.6) * config.wave + (config.trend ?? 0) * index).toFixed(3),
    )

    if (config.negativeIndexes?.includes(index)) value = -Math.abs(value / 3)
    if (config.zeroIndexes?.includes(index)) value = 0
    if (config.spikeIndexes?.includes(index)) value = Number((value + config.wave * 7).toFixed(3))
    if (config.missingIndexes?.includes(index)) value = null

    return {
      time: `2025-01-20 ${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:00`,
      value,
      validity,
      batch,
    }
  })
}

function makeRawPointsFromRows(batch: string, rows: Array<[string, number | null, ValidityLabel]>): RawPoint[] {
  return rows.map(([time, value, validity]) => ({
    time,
    value,
    validity,
    batch,
  }))
}

const processSamples: ProcessSample[] = [
  {
    name: '叶片加料',
    sourceFolder: '07230数据/叶片加料',
    fileCount: 24,
    batchCount: 24,
    rowsPerBatch: 984,
    metricCount: 29,
    batches: batchOptions,
    metrics: [
      {
        name: 'A叶片加料物料流量',
        unit: 'kg/h',
        category: '流量',
        physicalMin: 0,
        physicalMax: 3000,
        points: makePoints('07230-4-A-2501-023', {
          start: 29,
          step: 1,
          base: 2080,
          wave: 120,
          zeroIndexes: [0, 1, 2, 24, 25, 26, 27],
          spikeIndexes: [16],
        }),
      },
      {
        name: 'A叶片加料加料前入口含水率',
        unit: '%',
        category: '含水率',
        physicalMin: 0,
        physicalMax: 20,
        points: makeRawPointsFromRows('07230-4-A-2501-024', [
          ['2025-01-20 19:13:40', -0.05, '料头'],
          ['2025-01-20 19:13:50', -0.05, '料头'],
          ['2025-01-20 19:14:00', -0.05, '料头'],
          ['2025-01-20 19:14:10', -0.05, '料头'],
          ['2025-01-20 19:36:20', 16.47, '正常'],
          ['2025-01-20 19:36:30', 16.45, '正常'],
          ['2025-01-20 19:36:40', 16.42, '正常'],
          ['2025-01-20 19:36:50', 16.36, '正常'],
          ['2025-01-20 19:37:00', 16.38, '正常'],
          ['2025-01-20 19:37:10', 16.35, '正常'],
          ['2025-01-20 19:37:20', 16.34, '正常'],
          ['2025-01-20 19:37:30', 16.31, '正常'],
          ['2025-01-20 19:37:40', null, '正常'],
          ['2025-01-20 19:37:50', 16.29, '正常'],
          ['2025-01-20 19:38:00', 16.27, '正常'],
          ['2025-01-20 19:38:10', 16.25, '正常'],
          ['2025-01-20 19:38:20', 16.21, '正常'],
          ['2025-01-20 19:38:30', 16.18, '正常'],
          ['2025-01-20 19:38:40', 23.86, '正常'],
          ['2025-01-20 19:38:50', 16.16, '正常'],
          ['2025-01-20 19:39:00', 16.14, '正常'],
          ['2025-01-20 19:39:10', 16.12, '正常'],
          ['2025-01-20 19:39:20', 16.1, '正常'],
          ['2025-01-20 19:39:30', 16.08, '正常'],
          ['2025-01-20 21:30:10', 16.4, '料尾'],
          ['2025-01-20 21:30:20', 16.4, '料尾'],
          ['2025-01-20 21:30:30', 16.36, '料尾'],
          ['2025-01-20 21:30:40', 16.37, '料尾'],
        ]),
      },
      {
        name: 'A叶片加料出口烟叶含水率',
        unit: '%',
        category: '含水率',
        physicalMin: 0,
        physicalMax: 22,
        points: makePoints('07230-4-A-2501-023', {
          start: 29,
          step: 1,
          base: 17.8,
          wave: 0.6,
          trend: -0.01,
          negativeIndexes: [1, 2],
          spikeIndexes: [14],
        }),
      },
      {
        name: 'A叶片加料加糖料比例',
        unit: '%',
        category: '比例',
        physicalMin: 0,
        physicalMax: 8,
        points: makePoints('07230-4-A-2501-023', {
          start: 29,
          step: 1,
          base: 3.16,
          wave: 0.08,
          missingIndexes: [8],
        }),
      },
    ],
  },
  {
    name: '松散回潮',
    sourceFolder: '07230数据/松散回潮',
    fileCount: 24,
    batchCount: 24,
    rowsPerBatch: 906,
    metricCount: 20,
    batches: batchOptions,
    metrics: [
      {
        name: 'A松散回潮回潮机入口秤流量',
        unit: 'kg/h',
        category: '流量',
        physicalMin: 0,
        physicalMax: 2800,
        points: makePoints('07230-4-A-2501-023', {
          start: 18,
          step: 1,
          base: 1960,
          wave: 150,
          zeroIndexes: [0, 1, 24, 25, 26, 27],
          spikeIndexes: [17],
        }),
      },
      {
        name: 'A松散回潮回潮机出口含水率',
        unit: '%',
        category: '含水率',
        physicalMin: 0,
        physicalMax: 22,
        points: makePoints('07230-4-A-2501-023', {
          start: 18,
          step: 1,
          base: 15.9,
          wave: 0.68,
          negativeIndexes: [2],
          spikeIndexes: [19],
        }),
      },
      {
        name: 'A松散回潮热风温度',
        unit: '℃',
        category: '温度',
        physicalMin: 100,
        physicalMax: 150,
        points: makePoints('07230-4-A-2501-023', {
          start: 18,
          step: 1,
          base: 130.2,
          wave: 0.8,
          missingIndexes: [11],
        }),
      },
    ],
  },
  {
    name: '薄板烘丝',
    sourceFolder: '07230数据/薄板烘丝',
    fileCount: 24,
    batchCount: 24,
    rowsPerBatch: 1052,
    metricCount: 26,
    batches: batchOptions,
    metrics: [
      {
        name: 'A烘丝薄板入口秤流量',
        unit: 'kg/h',
        category: '流量',
        physicalMin: 0,
        physicalMax: 2800,
        points: makePoints('07230-4-A-2501-023', {
          start: 41,
          step: 1,
          base: 2200,
          wave: 130,
          zeroIndexes: [0, 1, 2, 25, 26, 27],
        }),
      },
      {
        name: 'A叶丝干燥出口叶丝含水率',
        unit: '%',
        category: '含水率',
        physicalMin: 0,
        physicalMax: 18,
        points: makePoints('07230-4-A-2501-023', {
          start: 41,
          step: 1,
          base: 12.4,
          wave: 0.5,
          negativeIndexes: [2, 3],
          spikeIndexes: [15],
        }),
      },
      {
        name: 'A叶丝增温增湿HT出口温度',
        unit: '℃',
        category: '温度',
        physicalMin: 20,
        physicalMax: 95,
        points: makePoints('07230-4-A-2501-023', {
          start: 41,
          step: 1,
          base: 58.6,
          wave: 3.2,
          spikeIndexes: [20],
        }),
      },
    ],
  },
  {
    name: '烟丝加香',
    sourceFolder: '07230数据/烟丝加香',
    fileCount: 24,
    batchCount: 24,
    rowsPerBatch: 607,
    metricCount: 6,
    batches: batchOptions,
    metrics: [
      {
        name: 'A叶丝加香物料流量',
        unit: 'kg/h',
        category: '流量',
        physicalMin: 0,
        physicalMax: 5200,
        points: makePoints('07230-4-A-2501-023', {
          start: 52,
          step: 1,
          base: 4200,
          wave: 260,
          zeroIndexes: [0, 1, 2, 3, 24, 25, 26, 27],
        }),
      },
      {
        name: 'A叶丝加香加香比例',
        unit: '%',
        category: '比例',
        physicalMin: 0,
        physicalMax: 1.2,
        points: makePoints('07230-4-A-2501-023', {
          start: 52,
          step: 1,
          base: 0.62,
          wave: 0.05,
          zeroIndexes: [0, 1, 2, 24, 25],
          spikeIndexes: [13],
        }),
      },
      {
        name: 'A叶丝加香加香后含水率',
        unit: '%',
        category: '含水率',
        physicalMin: 0,
        physicalMax: 16,
        points: makePoints('07230-4-A-2501-023', {
          start: 52,
          step: 1,
          base: 11.8,
          wave: 0.46,
          negativeIndexes: [3],
          missingIndexes: [16],
        }),
      },
    ],
  },
]

const selectedProcess = computed(() =>
  processSamples.find((item) => item.name === selectedProcessName.value) ?? processSamples[0],
)

const selectedMetric = computed(() =>
  selectedProcess.value.metrics.find((item) => item.name === selectedMetricName.value) ?? selectedProcess.value.metrics[0],
)

const rawPoints = computed(() =>
  selectedMetric.value.points.map((point) => ({ ...point, batch: selectedBatch.value })),
)

const rawPreviewRows = computed(() =>
  rawPoints.value.slice(0, 8).map((item) => ({
    time: item.time,
    value: formatValue(item.value),
    validity: item.validity,
    batch: item.batch,
  })),
)

const fieldPreviewRows = computed(() => [
  { field: '时间', role: '采集时间', example: rawPoints.value[0]?.time ?? '--', note: '作为时序模型的时间索引' },
  {
    field: selectedMetric.value.name,
    role: '指标值',
    example: formatValue(rawPoints.value[4]?.value),
    note: `数值范围 ${selectedMetric.value.physicalMin} ~ ${selectedMetric.value.physicalMax} ${selectedMetric.value.unit}`,
  },
  {
    field: `${selectedMetric.value.name}-有效性`,
    role: '有效性标签',
    example: rawPoints.value[0]?.validity ?? '--',
    note: '用于识别料头、正常生产、料尾',
  },
  {
    field: `${selectedMetric.value.name}-批次`,
    role: '批次编码',
    example: selectedBatch.value,
    note: '用于跨工序串联同一生产批次',
  },
])

const cleanedPoints = computed<CleanPoint[]>(() => {
  const rawValues = rawPoints.value.map((item) => item.value).filter((value): value is number => value !== null)
  const mean = rawValues.reduce((sum, value) => sum + value, 0) / Math.max(rawValues.length, 1)
  const variance = rawValues.reduce((sum, value) => sum + (value - mean) ** 2, 0) / Math.max(rawValues.length, 1)
  const std = Math.sqrt(variance)

  return rawPoints.value.map((point, index, source) => {
    const issues: string[] = []
    let cleanValue = point.value
    let retained = true
    let action = '保留原值'

    if (point.validity !== '正常') {
      issues.push(point.validity)
      if (rules.filterValidity) {
        retained = false
        action = '过滤料头/料尾'
      } else {
        action = '标记料头/料尾'
      }
    }

    if (point.value === null) {
      issues.push('缺失值')
      if (rules.repairAbnormal) {
        cleanValue = interpolateValue(source, index)
        action = retained ? '缺失值插值' : action
      }
    }

    if (typeof point.value === 'number' && rules.fixNegative && point.value < selectedMetric.value.physicalMin) {
      issues.push('低于物理下限')
      cleanValue = Math.max(selectedMetric.value.physicalMin, interpolateValue(source, index) ?? selectedMetric.value.physicalMin)
      action = retained ? '负值修复' : action
    }

    if (
      typeof point.value === 'number' &&
      rules.detectZeroStop &&
      selectedMetric.value.category === '流量' &&
      point.value === 0
    ) {
      issues.push('零值停机段')
      action = retained ? '标记停机段' : action
    }

    if (typeof point.value === 'number' && rules.detectOutlier && std > 0 && Math.abs(point.value - mean) > std * 2.4) {
      issues.push('异常波动')
      if (rules.repairAbnormal) {
        cleanValue = interpolateValue(source, index)
        action = retained ? '异常点平滑' : action
      }
    }

    return {
      ...point,
      cleanValue: retained && cleanValue !== null ? Number(cleanValue.toFixed(3)) : null,
      retained,
      issues,
      action,
    }
  })
})

const report = computed(() => {
  const total = cleanedPoints.value.length
  const removed = cleanedPoints.value.filter((item) => !item.retained).length
  const negativeFixed = cleanedPoints.value.filter((item) => item.issues.includes('低于物理下限')).length
  const zeroStops = cleanedPoints.value.filter((item) => item.issues.includes('零值停机段')).length
  const outliers = cleanedPoints.value.filter((item) => item.issues.includes('异常波动')).length
  const repaired = cleanedPoints.value.filter((item) => ['负值修复', '异常点平滑', '缺失值插值'].includes(item.action)).length
  const cleanUsable = cleanedPoints.value.filter(isModelReadyPoint).length
  const rawUsable = rawPoints.value.filter((item) => item.validity === '正常' && item.value !== null && item.value >= selectedMetric.value.physicalMin).length

  return {
    total,
    removed,
    negativeFixed,
    zeroStops,
    outliers,
    repaired,
    cleanUsable,
    rawUsable,
    beforeRate: Math.round((rawUsable / total) * 100),
    afterRate: Math.round((cleanUsable / total) * 100),
  }
})

const ruleLibraryRows = computed(() => [
  {
    name: '过滤料头/料尾',
    target: '全部指标',
    method: '剔除非正常生产区间',
    enabled: rules.filterValidity,
    hits: report.value.removed,
  },
  {
    name: '负值修复',
    target: '含水率、流量、比例',
    method: '低于物理下限时邻近插值',
    enabled: rules.fixNegative,
    hits: report.value.negativeFixed,
  },
  {
    name: '零值停机段识别',
    target: '物料流量类指标',
    method: '标记停机/空跑，不进入建模集',
    enabled: rules.detectZeroStop,
    hits: report.value.zeroStops,
  },
  {
    name: '异常波动识别',
    target: '全部连续型指标',
    method: '按统计波动阈值标记离群点',
    enabled: rules.detectOutlier,
    hits: report.value.outliers,
  },
  {
    name: '缺失/异常值修复',
    target: '缺失、负值、异常点',
    method: '邻近插值/平滑生成清洗值',
    enabled: rules.repairAbnormal,
    hits: report.value.repaired,
  },
])

const taskHistoryRows = computed(() => [
  {
    id: 'DG-07230-001',
    batch: selectedBatch.value,
    process: selectedProcess.value.name,
    rules: ruleLibraryRows.value.filter((item) => item.enabled).length,
    rawPoints: report.value.total,
    modelPoints: report.value.cleanUsable,
    status: datasetPublished.value ? '已发布建模集' : previewRunCount.value > 0 ? '预览完成' : '待执行预览',
  },
  {
    id: 'DG-07230-000',
    batch: '07230-4-A-2501-024',
    process: selectedProcess.value.name,
    rules: 5,
    rawPoints: 28,
    modelPoints: 21,
    status: '历史预览',
  },
])

const batchQualityRows = computed(() =>
  selectedProcess.value.batches.slice(0, 5).map((batch, index) => {
    const availableRate = Math.max(62, report.value.afterRate + [0, 5, -3, 4, -6][index])
    return {
      batch,
      process: selectedProcess.value.name,
      rawPoints: selectedProcess.value.rowsPerBatch,
      issueCount: 34 + index * 3,
      availableRate,
      status: availableRate >= 75 ? '较好' : availableRate >= 68 ? '可用' : '需复核',
    }
  }),
)

const datasetProfile = computed(() => ({
  name: `${selectedBatch.value}_${selectedProcess.value.name}_clean_v${previewRunCount.value || 1}`,
  location: `治理数据集 / 07230 / ${selectedProcess.value.name} / ${selectedBatch.value}`,
  tableName: 'dwd_07230_timeseries_clean',
  version: `clean_v${previewRunCount.value || 1}`,
  granularity: '1分钟',
  features: ['工序', '批次', '指标', '清洗值', '有效性标记', '时间索引'],
  models: 'DeepAR / TFT',
  status: datasetPublished.value ? '已发布' : '待发布',
}))

const modelRows = computed(() =>
  cleanedPoints.value
    .filter(isModelReadyPoint)
    .slice(0, 12)
    .map((item) => ({
      time: item.time,
      process: selectedProcess.value.name,
      batch: item.batch,
      metric: selectedMetric.value.name,
      rawValue: formatValue(item.value),
      cleanValue: formatValue(item.cleanValue),
      validity: item.validity,
      mark: item.issues.length ? item.issues.join('、') : '正常样本',
    })),
)

const activeStepIndex = computed(() => steps.findIndex((item) => item.key === activeStep.value))

function formatValue(value: number | null | undefined) {
  if (value === null || value === undefined) return '--'
  return `${value.toFixed(3)}${selectedMetric.value.unit}`
}

function interpolateValue(points: RawPoint[], index: number) {
  const isUsablePoint = (item: RawPoint): item is RawPoint & { value: number } =>
    typeof item.value === 'number' && item.value >= 0
  const previous = [...points.slice(0, index)].reverse().find(isUsablePoint)
  const next = points.slice(index + 1).find(isUsablePoint)
  if (previous && next) {
    return Number(((previous.value + next.value) / 2).toFixed(3))
  }
  return previous?.value ?? next?.value ?? null
}

function isModelReadyPoint(item: CleanPoint) {
  const unresolvedNegative = item.issues.includes('低于物理下限') && item.action !== '负值修复'
  const unresolvedMissing = item.issues.includes('缺失值') && item.action !== '缺失值插值'
  const unresolvedOutlier = item.issues.includes('异常波动') && item.action !== '异常点平滑'
  return (
    item.retained &&
    item.validity === '正常' &&
    item.cleanValue !== null &&
    !item.issues.includes('零值停机段') &&
    !unresolvedNegative &&
    !unresolvedMissing &&
    !unresolvedOutlier
  )
}

function runCleaningPreview() {
  previewRunCount.value += 1
  datasetPublished.value = false
  activeStep.value = 'compare'
  ElMessage.success('规则清洗结果已刷新，可查看前后曲线、问题点和待发布建模数据集')
  void nextTick(renderCompareChart)
}

function publishModelDataset() {
  datasetPublished.value = true
  activeStep.value = 'dataset'
  ElMessage.success('建模数据集已发布到治理数据集目录，可对接 DeepAR / TFT 训练任务')
}

function renderCompareChart() {
  if (!chartRef.value) return

  compareChart ??= echarts.init(chartRef.value)
  compareChart.setOption({
    color: ['#8da5bf', '#4a90e2', '#ef6f6c'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#d8e6f5',
      textStyle: { color: '#36506e' },
    },
    legend: {
      top: 0,
      data: ['原始值', '清洗值', '异常点'],
      textStyle: { color: '#5f7c9d' },
    },
    grid: { left: 48, right: 24, top: 48, bottom: 44 },
    xAxis: {
      type: 'category',
      data: rawPoints.value.map((item) => item.time.slice(11, 16)),
      axisLine: { lineStyle: { color: '#c9d9ea' } },
      axisLabel: { color: '#6b85a3' },
    },
    yAxis: {
      type: 'value',
      name: selectedMetric.value.unit,
      nameTextStyle: { color: '#6b85a3' },
      axisLabel: { color: '#6b85a3' },
      splitLine: { lineStyle: { color: '#eaf1f8' } },
    },
    series: [
      {
        name: '原始值',
        type: 'line',
        smooth: true,
        symbolSize: 5,
        data: rawPoints.value.map((item) => item.value),
        lineStyle: { width: 2, type: 'dashed' },
      },
      {
        name: '清洗值',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: cleanedPoints.value.map((item) => item.cleanValue),
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(74, 144, 226, 0.24)' },
            { offset: 1, color: 'rgba(74, 144, 226, 0.03)' },
          ]),
        },
      },
      {
        name: '异常点',
        type: 'scatter',
        symbolSize: 10,
        data: cleanedPoints.value
          .map((item, index) => (item.issues.length && item.value !== null ? [index, item.value] : null))
          .filter(Boolean),
      },
    ],
  })
  compareChart.resize()
}

function resizeChart() {
  compareChart?.resize()
}

function scheduleRenderCompareChart() {
  void nextTick(() => {
    renderCompareChart()
    window.setTimeout(() => {
      renderCompareChart()
      resizeChart()
    }, 80)
  })
}

watch(selectedProcessName, () => {
  selectedMetricName.value = selectedProcess.value.metrics[0].name
  scheduleRenderCompareChart()
})

watch([selectedBatch, selectedMetricName, rules], () => {
  scheduleRenderCompareChart()
}, { deep: true })

watch(activeStep, (step) => {
  if (step === 'compare') {
    scheduleRenderCompareChart()
  }
})

onMounted(async () => {
  await nextTick()
  renderCompareChart()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  compareChart?.dispose()
  compareChart = null
})
</script>

<template>
  <div class="governance-workbench">
    <el-card shadow="hover" class="pipeline-card">
      <template #header>
        <div class="section-header">
          <span>数据治理实现链路</span>
          <el-tag effect="plain">07230 原始数据 → 建模数据集</el-tag>
        </div>
      </template>
      <div class="pipeline-strip">
        <div v-for="(item, index) in pipelineNodes" :key="item.title" class="pipeline-node">
          <span>{{ index + 1 }}</span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.desc }}</small>
        </div>
      </div>
    </el-card>

    <section class="workbench-banner">
      <div>
        <span class="banner-tag">DATA GOVERNANCE</span>
        <h2>07230 数据治理与清洗工作台</h2>
        <p>从导入后的原始宽表出发，完成字段识别、规则清洗、前后对比和建模数据集准备，形成 DeepAR / TFT 可使用的标准时序数据。</p>
      </div>
      <div class="model-status">
        <span>建模准备状态</span>
        <strong>{{ report.afterRate }}%</strong>
        <small>可入模样本 {{ report.cleanUsable }} / {{ report.total }}</small>
        <em>口径：正常区间 + 有清洗值 + 非停机段</em>
      </div>
    </section>

    <section class="step-strip">
      <button
        v-for="(item, index) in steps"
        :key="item.key"
        class="step-card"
        :class="{ active: activeStep === item.key, finished: index < activeStepIndex }"
        type="button"
        @click="activeStep = item.key"
      >
        <span>{{ index + 1 }}</span>
        <strong>{{ item.title }}</strong>
        <small>{{ item.desc }}</small>
      </button>
    </section>

    <el-card shadow="hover">
      <template #header>已导入数据集</template>
      <div class="selector-grid">
        <el-select v-model="selectedProcessName" placeholder="选择工序">
          <el-option v-for="item in processSamples" :key="item.name" :label="item.name" :value="item.name" />
        </el-select>
        <el-select v-model="selectedBatch" placeholder="选择批次">
          <el-option v-for="item in selectedProcess.batches" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="selectedMetricName" placeholder="选择指标">
          <el-option v-for="item in selectedProcess.metrics" :key="item.name" :label="item.name" :value="item.name" />
        </el-select>
      </div>
      <div class="dataset-summary">
        <div><span>来源目录</span><strong>{{ selectedProcess.sourceFolder }}</strong></div>
        <div><span>当前文件</span><strong>{{ selectedBatch }}.xlsx</strong></div>
        <div><span>批次文件</span><strong>{{ selectedProcess.fileCount }} 个</strong></div>
        <div><span>样本行数</span><strong>{{ selectedProcess.rowsPerBatch }} 行/批</strong></div>
        <div><span>识别指标</span><strong>{{ selectedProcess.metricCount }} 个</strong></div>
        <div><span>原始结构</span><strong>时间 + 指标三元组</strong></div>
      </div>
    </el-card>

    <section v-show="activeStep === 'imported'" class="content-grid">
      <el-card shadow="hover">
        <template #header>原始宽表抽样预览</template>
        <el-table :data="rawPreviewRows" stripe>
          <el-table-column prop="time" label="时间" min-width="170" />
          <el-table-column prop="value" :label="selectedMetric.name" min-width="220" />
          <el-table-column prop="validity" :label="`${selectedMetric.name}-有效性`" min-width="180">
            <template #default="{ row }">
              <el-tag :type="row.validity === '正常' ? 'success' : 'warning'" effect="plain">{{ row.validity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="batch" :label="`${selectedMetric.name}-批次`" min-width="180" />
        </el-table>
      </el-card>

      <el-card shadow="hover">
        <template #header>数据结构与口径</template>
        <div class="explain-list">
          <p>Excel 原始结构按“时间 + 指标值 + 指标-有效性 + 指标-批次”成组三列展开。</p>
          <p>有效性字段用于区分料头、正常生产区间和料尾，清洗时作为首要过滤口径。</p>
          <p>含水率、流量等指标同时执行物理上下限、停机零值段和波动点识别。</p>
        </div>
      </el-card>
    </section>

    <section v-show="activeStep === 'fields'" class="content-grid">
      <el-card shadow="hover">
        <template #header>字段识别结果</template>
        <el-table :data="fieldPreviewRows" stripe>
          <el-table-column prop="field" label="原始字段" min-width="240" />
          <el-table-column prop="role" label="识别角色" width="130" />
          <el-table-column prop="example" label="样例值" min-width="160" />
          <el-table-column prop="note" label="治理用途" min-width="240" />
        </el-table>
      </el-card>

      <el-card shadow="hover">
        <template #header>标准化长表映射</template>
        <div class="mapping-flow">
          <div>宽表字段</div>
          <span>→</span>
          <div>指标主数据</div>
          <span>→</span>
          <div>时序长表</div>
          <span>→</span>
          <div>建模样本</div>
        </div>
        <div class="mapping-note">
          <p>转换后每行代表一个时间点上的一个指标值，字段包括时间、工序、批次、指标、原始值、清洗值和清洗标记。</p>
        </div>
      </el-card>
    </section>

    <section v-show="activeStep === 'rules'" class="content-grid">
      <el-card shadow="hover">
        <template #header>清洗规则配置</template>
        <div class="rule-list">
          <div class="rule-item">
            <div><strong>过滤料头/料尾</strong><span>只保留有效性为“正常”的生产区间。</span></div>
            <el-switch v-model="rules.filterValidity" />
          </div>
          <div class="rule-item">
            <div><strong>负值处理</strong><span>含水率、流量等指标低于物理下限时做修复。</span></div>
            <el-switch v-model="rules.fixNegative" />
          </div>
          <div class="rule-item">
            <div><strong>零值停机段识别</strong><span>物料流量为 0 的连续时间段标记为停机/空跑。</span></div>
            <el-switch v-model="rules.detectZeroStop" />
          </div>
          <div class="rule-item">
            <div><strong>异常波动识别</strong><span>使用 3σ 思路识别离群点，并结合生产波动阈值复核。</span></div>
            <el-switch v-model="rules.detectOutlier" />
          </div>
          <div class="rule-item">
            <div><strong>缺失/异常值修复</strong><span>对缺失、负值和异常点做邻近插值或平滑。</span></div>
            <el-switch v-model="rules.repairAbnormal" />
          </div>
        </div>
        <div class="action-row">
          <el-button type="primary" @click="runCleaningPreview">执行清洗预览</el-button>
          <el-tag effect="plain">已执行 {{ previewRunCount }} 次</el-tag>
        </div>
      </el-card>

      <el-card shadow="hover">
        <template #header>清洗报告</template>
        <div class="report-grid">
          <div><span>原始点数</span><strong>{{ report.total }}</strong></div>
          <div><span>过滤点数</span><strong>{{ report.removed }}</strong></div>
          <div><span>负值修复</span><strong>{{ report.negativeFixed }}</strong></div>
          <div><span>零值标记</span><strong>{{ report.zeroStops }}</strong></div>
          <div><span>异常波动</span><strong>{{ report.outliers }}</strong></div>
          <div><span>修复点数</span><strong>{{ report.repaired }}</strong></div>
        </div>
        <div class="rate-card">
          <span>可用率提升</span>
          <strong>{{ report.beforeRate }}% → {{ report.afterRate }}%</strong>
          <small>计算方式：可用率 = 可入模样本数 / 原始点数。料头、料尾、停机零值段不计入建模样本；缺失、负值、异常点只有被修复后才重新计入。</small>
        </div>
      </el-card>

      <el-card shadow="hover" class="full-span">
        <template #header>清洗规则库</template>
        <el-table :data="ruleLibraryRows" stripe>
          <el-table-column prop="name" label="规则名称" min-width="160" />
          <el-table-column prop="target" label="适用对象" min-width="180" />
          <el-table-column prop="method" label="处理方式" min-width="240" />
          <el-table-column prop="hits" label="命中次数" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
                {{ row.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="hover" class="full-span">
        <template #header>清洗任务记录</template>
        <el-table :data="taskHistoryRows" stripe>
          <el-table-column prop="id" label="任务编号" min-width="140" />
          <el-table-column prop="batch" label="批次" min-width="170" />
          <el-table-column prop="process" label="工序" width="120" />
          <el-table-column prop="rules" label="执行规则数" width="120" />
          <el-table-column prop="rawPoints" label="原始点数" width="110" />
          <el-table-column prop="modelPoints" label="可入模点数" width="120" />
          <el-table-column prop="status" label="状态" min-width="140">
            <template #default="{ row }">
              <el-tag :type="row.status === '已发布建模集' ? 'success' : 'primary'" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>

    <section v-show="activeStep === 'compare'" class="content-grid">
      <el-card shadow="hover" class="wide-card">
        <template #header>清洗前后可视化对比</template>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>
      <el-card shadow="hover">
        <template #header>问题点明细</template>
        <el-table :data="cleanedPoints.filter((item) => item.issues.length).slice(0, 8)" stripe>
          <el-table-column prop="time" label="时间" min-width="130" />
          <el-table-column label="原始值" width="110">
            <template #default="{ row }">{{ formatValue(row.value) }}</template>
          </el-table-column>
          <el-table-column label="清洗值" width="110">
            <template #default="{ row }">{{ formatValue(row.cleanValue) }}</template>
          </el-table-column>
          <el-table-column label="问题" min-width="160">
            <template #default="{ row }">{{ row.issues.join('、') }}</template>
          </el-table-column>
          <el-table-column prop="action" label="处理动作" min-width="130" />
        </el-table>
      </el-card>
    </section>

    <section v-show="activeStep === 'dataset'" class="content-grid">
      <el-card shadow="hover" class="wide-card">
        <template #header>
          <div class="section-header">
            <span>建模数据集预览</span>
            <el-tag type="success" effect="plain">可供 DeepAR / TFT 使用</el-tag>
          </div>
        </template>
        <el-table :data="modelRows" stripe>
          <el-table-column prop="time" label="时间" min-width="150" />
          <el-table-column prop="process" label="工序" width="110" />
          <el-table-column prop="batch" label="批次" min-width="170" />
          <el-table-column prop="metric" label="指标" min-width="220" />
          <el-table-column prop="rawValue" label="原始值" width="120" />
          <el-table-column prop="cleanValue" label="清洗值" width="120" />
          <el-table-column prop="validity" label="有效性" width="100" />
          <el-table-column prop="mark" label="清洗标记" min-width="180" />
        </el-table>
      </el-card>
      <el-card shadow="hover">
        <template #header>入模说明</template>
        <div class="explain-list">
          <p>后续模型侧可以按批次、工序和指标拼接多变量时序数据。</p>
          <p>DeepAR 适合做概率预测，TFT 可以结合静态变量和已知未来特征。</p>
          <p>发布后的数据集保留原始值、清洗值和清洗标记，便于模型侧回溯数据质量。</p>
        </div>
      </el-card>

      <el-card shadow="hover">
        <template #header>建模数据集发布</template>
        <div class="dataset-profile">
          <div><span>数据集名称</span><strong>{{ datasetProfile.name }}</strong></div>
          <div><span>发布位置</span><strong>{{ datasetProfile.location }}</strong></div>
          <div><span>目标表</span><strong>{{ datasetProfile.tableName }}</strong></div>
          <div><span>版本号</span><strong>{{ datasetProfile.version }}</strong></div>
          <div><span>时间粒度</span><strong>{{ datasetProfile.granularity }}</strong></div>
          <div><span>适用模型</span><strong>{{ datasetProfile.models }}</strong></div>
          <div><span>发布状态</span><strong>{{ datasetProfile.status }}</strong></div>
        </div>
        <div class="feature-tags">
          <el-tag v-for="item in datasetProfile.features" :key="item" effect="plain">{{ item }}</el-tag>
        </div>
        <div class="action-row">
          <el-button type="primary" @click="publishModelDataset">发布到建模数据集</el-button>
          <el-tag :type="datasetPublished ? 'success' : 'warning'" effect="plain">
            {{ datasetPublished ? '已发布，模型侧可订阅' : '等待确认发布' }}
          </el-tag>
        </div>
      </el-card>

      <el-card shadow="hover" class="full-span">
        <template #header>多批次清洗质量对比</template>
        <el-table :data="batchQualityRows" stripe>
          <el-table-column prop="batch" label="批次" min-width="170" />
          <el-table-column prop="process" label="工序" width="120" />
          <el-table-column prop="rawPoints" label="原始点数" width="120" />
          <el-table-column prop="issueCount" label="问题点数" width="120" />
          <el-table-column prop="availableRate" label="可用率" width="120">
            <template #default="{ row }">{{ row.availableRate }}%</template>
          </el-table-column>
          <el-table-column prop="status" label="治理状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === '较好' ? 'success' : row.status === '可用' ? 'primary' : 'warning'" effect="plain">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.governance-workbench {
  display: grid;
  gap: 18px;
}

.pipeline-card {
  border-color: #cfe2fb;
}

.workbench-banner {
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
}

.workbench-banner h2 {
  margin: 16px 0 10px;
  font-size: 34px;
  color: var(--text-main);
}

.workbench-banner p {
  max-width: 820px;
  margin: 0;
  line-height: 1.7;
  color: var(--text-secondary);
}

.model-status {
  min-width: 240px;
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 22px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid var(--line-color);
}

.model-status span,
.model-status small,
.model-status em {
  color: var(--text-secondary);
}

.model-status em {
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.model-status strong {
  color: #35a66a;
  font-size: 42px;
  line-height: 1;
}

.step-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.step-card {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 4px 10px;
  align-items: center;
  min-height: 78px;
  padding: 14px;
  border: 1px solid var(--line-color);
  border-radius: 16px;
  background: #ffffff;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-soft);
}

.step-card span {
  grid-row: span 2;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--primary-color);
  background: var(--primary-soft);
  font-weight: 700;
}

.step-card strong {
  font-size: 15px;
}

.step-card small {
  color: var(--text-secondary);
}

.step-card.active {
  border-color: rgba(74, 144, 226, 0.55);
  background: linear-gradient(180deg, #ffffff 0%, #eef6ff 100%);
}

.step-card.finished span {
  color: #ffffff;
  background: #35a66a;
}

.pipeline-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.pipeline-node {
  position: relative;
  min-height: 84px;
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 4px 10px;
  align-content: center;
  padding: 14px;
  border: 1px solid var(--line-color);
  border-radius: 14px;
  background: var(--panel-soft);
}

.pipeline-node span {
  grid-row: span 2;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #ffffff;
  background: var(--primary-color);
  font-weight: 700;
}

.pipeline-node strong {
  color: var(--text-main);
  font-size: 14px;
}

.pipeline-node small {
  color: var(--text-secondary);
  line-height: 1.5;
  font-size: 12px;
}

.selector-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1.6fr;
  gap: 14px;
}

.dataset-summary,
.report-grid {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.dataset-summary {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.report-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dataset-summary div,
.report-grid div,
.rate-card {
  padding: 16px;
  border: 1px solid var(--line-color);
  border-radius: 16px;
  background: var(--panel-soft);
}

.dataset-summary span,
.report-grid span,
.rate-card span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.dataset-summary strong,
.report-grid strong,
.rate-card strong {
  display: block;
  margin-top: 8px;
  color: var(--text-main);
  font-size: 18px;
  word-break: break-all;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.65fr);
  gap: 20px;
}

.full-span {
  grid-column: 1 / -1;
}

.wide-card {
  min-width: 0;
}

.explain-list {
  display: grid;
  gap: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.explain-list p {
  margin: 0;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--panel-soft);
  border: 1px solid var(--line-color);
}

.mapping-flow {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr;
  gap: 10px;
  align-items: center;
}

.mapping-flow div {
  padding: 18px 12px;
  border-radius: 14px;
  text-align: center;
  color: var(--primary-color);
  background: var(--primary-soft);
  border: 1px solid #cfe2fb;
  font-weight: 600;
}

.mapping-flow span {
  color: var(--text-secondary);
  font-weight: 700;
}

.mapping-note {
  margin-top: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.rule-list {
  display: grid;
  gap: 14px;
}

.rule-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--line-color);
  background: var(--panel-soft);
}

.rule-item strong,
.rule-item span {
  display: block;
}

.rule-item span {
  margin-top: 6px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

.rate-card {
  margin-top: 16px;
}

.rate-card strong {
  color: #35a66a;
  font-size: 28px;
}

.rate-card small {
  display: block;
  margin-top: 10px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.dataset-profile {
  display: grid;
  gap: 12px;
}

.dataset-profile div {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--line-color);
  background: var(--panel-soft);
}

.dataset-profile span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.dataset-profile strong {
  display: block;
  margin-top: 8px;
  color: var(--text-main);
  font-size: 17px;
  word-break: break-all;
}

.feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.chart-box {
  height: 390px;
  border-radius: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 1280px) {
  .step-strip,
  .pipeline-strip,
  .selector-grid,
  .dataset-summary,
  .report-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .workbench-banner,
  .rule-item {
    display: grid;
  }

  .step-strip,
  .pipeline-strip,
  .selector-grid,
  .dataset-summary,
  .report-grid,
  .mapping-flow {
    grid-template-columns: 1fr;
  }

  .mapping-flow span {
    text-align: center;
  }

  .model-status {
    min-width: 0;
  }
}
</style>
