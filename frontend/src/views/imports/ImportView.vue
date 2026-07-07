<script setup lang="ts">
/**
 * 文件说明：ImportView
 * 文件业务说明：导入任务页面，负责选择工序、上传历史 Excel 文件并展示导入任务结果，
 *          用于打通多工序历史数据导入与正式分析入库的业务闭环。
 * 业务职责：
 * 1. 加载可选工序与导入任务列表。
 * 2. 支持选择工序并上传真实 Excel 文件。
 * 3. 展示导入结果、任务状态和结果说明。
 * 开发者：czd
 */
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getProcesses, type ProcessOption } from '@/api/demo'
import { getImportTasks, uploadImport, type ImportTask } from '@/api/imports'

const processes = ref<ProcessOption[]>([])
const tasks = ref<ImportTask[]>([])
const selectedProcessId = ref<number>()
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadResult = ref('')

const canSubmit = computed(() => Boolean(selectedProcessId.value && selectedFile.value) && !uploading.value)

async function loadInitialData() {
  const [processResponse, taskResponse] = await Promise.all([
    getProcesses(),
    getImportTasks(),
  ])
  processes.value = processResponse.data
  tasks.value = taskResponse.data
  if (!selectedProcessId.value && processes.value.length > 0) {
    selectedProcessId.value = processes.value[0].id
  }
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  selectedFile.value = target.files?.[0] ?? null
}

async function submitUpload() {
  if (!selectedProcessId.value || !selectedFile.value) {
    ElMessage.warning('请先选择工序并上传 Excel 文件')
    return
  }

  try {
    uploading.value = true
    const response = await uploadImport(selectedProcessId.value, selectedFile.value)
    uploadResult.value = `批次 ${response.data.generatedBatchCode} 已完成导入`
    ElMessage.success(response.data.message)
    selectedFile.value = null
    await loadInitialData()
  } catch (error) {
    ElMessage.error('导入失败，请检查文件内容或后端日志')
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  void loadInitialData()
})
</script>

<template>
  <div class="import-grid">
    <section class="upload-grid">
      <el-card shadow="hover">
        <template #header>历史文件导入</template>
        <div class="upload-form">
          <el-select v-model="selectedProcessId" placeholder="请选择导入工序">
            <el-option v-for="item in processes" :key="item.id" :label="item.processName" :value="item.id" />
          </el-select>
          <label class="file-picker">
            <span>{{ selectedFile?.name ?? '请选择 xlsx / xls 历史数据文件' }}</span>
            <input type="file" accept=".xlsx,.xls" @change="onFileChange" />
          </label>
          <el-button type="primary" :loading="uploading" :disabled="!canSubmit" @click="submitUpload">
            上传并写入正式分析库
          </el-button>
        </div>
      </el-card>

      <el-card shadow="hover">
        <template #header>当前说明</template>
        <ul class="tip-list">
          <li>当前支持薄板烘丝、松散回潮、烟丝干燥三个工序独立导入。</li>
          <li>导入时会按所选工序将数据写入正式分析表，并自动完成批次统计计算。</li>
          <li>导入成功后，可直接在分析概览、批次分析和质量分析页面查看结果。</li>
          <li v-if="uploadResult">{{ uploadResult }}</li>
        </ul>
      </el-card>
    </section>

    <el-card shadow="hover">
      <template #header>导入任务列表</template>
      <el-table :data="tasks" stripe>
        <el-table-column prop="id" label="任务ID" width="90" />
        <el-table-column prop="processName" label="工序" min-width="140" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="generatedBatchCode" label="生成批次" min-width="180" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="successRows" label="成功行数" width="110" />
        <el-table-column prop="failedRows" label="失败行数" width="110" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column prop="message" label="说明" min-width="260" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.import-grid {
  display: grid;
  gap: 20px;
}

.upload-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}

.upload-form {
  display: grid;
  gap: 16px;
}

.file-picker {
  display: block;
  padding: 16px;
  border: 1px dashed #9cc2ec;
  border-radius: 14px;
  background: #f7fbff;
  color: var(--text-main);
  cursor: pointer;
}

.file-picker input {
  display: none;
}

.tip-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
  color: var(--text-secondary);
}

@media (max-width: 1100px) {
  .upload-grid {
    grid-template-columns: 1fr;
  }
}
</style>
