/**
 * 文件名称：router
 * 文件说明：前端路由入口文件，负责组织分析概览、批次分析、质量分析、导入任务、
 * 工序管理和数据类别管理等业务页面。
 * 主要职责：
 * 1. 定义系统路由结构。
 * 2. 配置页面标题与描述元信息。
 * 3. 为布局导航提供统一的页面入口。
 * 开发者：czd
 */
import { createRouter, createWebHistory } from 'vue-router'
import BatchAnalysisView from '@/views/batches/BatchAnalysisView.vue'
import HomeView from '@/views/home/HomeView.vue'
import ImportView from '@/views/imports/ImportView.vue'
import LayoutView from '@/views/layout/LayoutView.vue'
import ProcessManagementView from '@/views/processes/ProcessManagementView.vue'
import QualityAnalysisView from '@/views/quality/QualityAnalysisView.vue'
import VariableManagementView from '@/views/variables/VariableManagementView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: LayoutView,
      children: [
        {
          path: '',
          name: 'home',
          component: HomeView,
          meta: {
            title: '分析概览',
            description: '查看历史测试数据概览、批次趋势与多批次对比结果。',
          },
        },
        {
          path: 'batches',
          name: 'batches',
          component: BatchAnalysisView,
          meta: {
            title: '批次分析',
            description: '按工序、批次和变量查看正式库的单批次趋势分析与异常点明细。',
          },
        },
        {
          path: 'quality',
          name: 'quality',
          component: QualityAnalysisView,
          meta: {
            title: '质量分析',
            description: '从质量等级维度分析不同批次的均值、波动、缺失率与异常率表现。',
          },
        },
        {
          path: 'imports',
          name: 'imports',
          component: ImportView,
          meta: {
            title: '导入任务',
            description: '上传演示文件并生成新的批次、时序点和统计结果。',
          },
        },
        {
          path: 'process-management',
          name: 'process-management',
          component: ProcessManagementView,
          meta: {
            title: '工序管理',
            description: '查看当前纳入正式库分析范围的工序主数据、数据规模与采集覆盖情况。',
          },
        },
        {
          path: 'variable-management',
          name: 'variable-management',
          component: VariableManagementView,
          meta: {
            title: '数据类别管理',
            description: '按工序查看正式库数据类别编码、名称、覆盖规模和数据类别统计画像。',
          },
        },
      ],
    },
  ],
})

export default router
