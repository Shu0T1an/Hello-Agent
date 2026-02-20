<script setup lang="ts">
import { ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useCheckpointStore } from '@/stores/checkpoint'
import type { Checkpoint } from '@/types/checkpoint'
import BaseModal from '@/components/base/BaseModal.vue'
import CodeHighlight from '@/components/base/CodeHighlight.vue'
import CheckpointBasicInfo from './CheckpointBasicInfo.vue'
import CheckpointStateData from './CheckpointStateData.vue'
import CheckpointExecutionRecord from './CheckpointExecutionRecord.vue'

interface Props {
  visible: boolean
  checkpoint: Checkpoint
}

interface Emits {
  (e: 'close'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const checkpointStore = useCheckpointStore()
const { currentDetail, detailLoading } = storeToRefs(checkpointStore)
const { loadDetail } = checkpointStore

// 标签页状态
type Tab = 'basic' | 'state' | 'execution' | 'raw'
const activeTab = ref<Tab>('basic')

// 加载详情
async function loadCheckpointDetail() {
  if (props.visible) {
    await loadDetail(props.checkpoint.checkpointId)
  }
}

// 监听可见性变化
watch(() => props.visible, (visible) => {
  if (visible) {
    loadCheckpointDetail()
    activeTab.value = 'basic'
  }
})

// 监听 checkpoint 变化
watch(() => props.checkpoint, () => {
  if (props.visible) {
    loadCheckpointDetail()
  }
}, { deep: true })

// 标签页配置
const tabs: { id: Tab; label: string; icon: string }[] = [
  { id: 'basic', label: '基础信息', icon: 'Info' },
  { id: 'state', label: '状态数据', icon: 'Database' },
  { id: 'execution', label: '执行记录', icon: 'Activity' },
  { id: 'raw', label: '原始 JSON', icon: 'Code' },
]

function handleClose() {
  emit('close')
}
</script>

<template>
  <BaseModal
    :visible="visible"
    title="Checkpoint 详情"
    width="xl"
    :close-on-overlay-click="true"
    @close="handleClose"
  >
    <div class="checkpoint-detail-modal">
      <!-- 加载状态 -->
      <div v-if="detailLoading" class="flex items-center justify-center py-16">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
      </div>

      <!-- 内容 -->
      <div v-else-if="currentDetail" class="space-y-4">
        <!-- 标签页导航 -->
        <div class="flex border-b dark:border-gray-700">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px"
            :class="activeTab === tab.id
              ? 'text-blue-600 dark:text-blue-400 border-blue-600 dark:border-blue-400'
              : 'text-gray-500 dark:text-gray-400 border-transparent hover:text-gray-700 dark:hover:text-gray-300'"
            @click="activeTab = tab.id"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 标签页内容 -->
        <div class="mt-4">
          <CheckpointBasicInfo
            v-if="activeTab === 'basic'"
            :checkpoint="currentDetail"
          />

          <CheckpointStateData
            v-else-if="activeTab === 'state'"
            :state="currentDetail.state"
          />

          <CheckpointExecutionRecord
            v-else-if="activeTab === 'execution'"
            :checkpoint="currentDetail"
          />

          <div v-else-if="activeTab === 'raw'" class="raw-json">
            <CodeHighlight
              :code="JSON.stringify(currentDetail, null, 2)"
              language="json"
              theme="github-dark"
            />
          </div>
        </div>
      </div>

      <!-- 错误状态 -->
      <div v-else class="text-center py-16 text-gray-500 dark:text-gray-400">
        <p>加载 Checkpoint 详情失败</p>
      </div>
    </div>
  </BaseModal>
</template>

<style scoped>
.checkpoint-detail-modal {
  min-height: 400px;
}

.raw-json {
  padding: 1rem;
}
</style>
