<script setup lang="ts">
/**
 * 文件名称：LayoutView
 * 文件说明：系统主布局页面，负责承载左侧导航、页面标题描述以及各业务子页面的统一容器。
 * 主要职责：
 * 1. 维护系统导航入口。
 * 2. 根据当前路由展示页面标题与说明。
 * 3. 统一历史分析各业务页面的整体布局风格。
 * 开发者：czd
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => String(route.meta.title ?? '历史传感器数据分析与展示'))
const pageDescription = computed(() =>
  String(route.meta.description ?? '当前为前后端基础框架，可在此基础上继续扩展业务模块。'),
)

const navItems = [
  { label: '分析概览', to: '/' },
  { label: '批次分析', to: '/batches' },
  { label: '质量分析', to: '/quality' },
  { label: '导入任务', to: '/imports' },
  { label: '工序管理', to: '/process-management' },
  { label: '数据类别管理', to: '/variable-management' },
]
</script>

<template>
  <div class="layout-shell">
    <aside class="layout-side">
      <div class="brand">
        <div class="brand-title">武汉中烟</div>
        <div class="brand-subtitle">历史数据分析系统</div>
      </div>
      <nav class="menu-list">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="menu-item"
          active-class="active"
        >
          {{ item.label }}
        </router-link>
      </nav>
    </aside>

    <main class="layout-main">
      <header class="topbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageDescription }}</p>
        </div>
      </header>
      <section class="content-panel">
        <router-view />
      </section>
    </main>
  </div>
</template>

<style scoped>
.layout-shell {
  display: grid;
  grid-template-columns: 260px 1fr;
  min-height: 100vh;
}

.layout-side {
  padding: 28px 20px;
  background: linear-gradient(180deg, #dcecff 0%, #cfe3fb 100%);
  color: var(--text-main);
  border-right: 1px solid var(--line-color);
}

.brand {
  padding: 8px 8px 28px;
  border-bottom: 1px solid rgba(74, 144, 226, 0.16);
}

.brand-title {
  font-size: 24px;
  font-weight: 700;
  color: #244c7a;
}

.brand-subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: #6887ab;
}

.menu-list {
  margin-top: 24px;
  display: grid;
  gap: 10px;
}

.menu-item {
  padding: 12px 14px;
  border-radius: 12px;
  color: #426488;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(134, 178, 228, 0.3);
  transition: all 0.2s ease;
}

.menu-item.active {
  background: linear-gradient(180deg, #ffffff 0%, #eef6ff 100%);
  color: var(--primary-color);
  border-color: rgba(74, 144, 226, 0.4);
  box-shadow: 0 8px 18px rgba(74, 144, 226, 0.12);
}

.layout-main {
  padding: 28px;
}

.topbar h1 {
  margin: 0;
  font-size: 30px;
  color: var(--text-main);
}

.topbar p {
  margin: 10px 0 0;
  color: var(--text-secondary);
}

.content-panel {
  margin-top: 24px;
}

@media (max-width: 900px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-side {
    padding-bottom: 18px;
  }
}
</style>
