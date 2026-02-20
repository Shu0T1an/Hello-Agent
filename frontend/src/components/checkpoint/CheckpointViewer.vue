<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useCheckpointStore } from '@/stores/checkpoint'
import { useChatStore } from '@/stores/chat'
import CheckpointCard from './CheckpointCard.vue'
import CheckpointDetailModal from './CheckpointDetailModal.vue'
import ConfirmDialog from '@/components/base/ConfirmDialog.vue'
import { Filter, X } from 'lucide-vue-next'
import type { Checkpoint, CheckpointSource } from '@/types/checkpoint'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseEmpty from '@/components/base/BaseEmpty.vue'

interface Props {
  sessionId?: string
}

const props = defineProps<Props>()

const checkpointStore = useCheckpointStore()
const chatStore = useChatStore()

const {
  checkpoints,
  filteredCheckpoints,
  hasCheckpoints,
  loading,
  restoring,
  deleting,
  error,
  filter,
} = storeToRefs(checkpointStore)

const {
  loadCheckpoints,
  restoreCheckpoint,
  deleteCheckpoint,
  setFilter,
  clearFilter,
  clearError,
} = checkpointStore

// UI 状态
const selectedCheckpoint = ref<Checkpoint | null>(null)
const showDetailModal = ref(false)
const showRestoreConfirm = ref(false)
const showDeleteConfirm = ref(false)
const pendingCheckpoint = ref<Checkpoint | null>(null)
const sourceFilter = ref<CheckpointSource | ''>('')

// 确认对话框消息
const restoreConfirmMessage = computed(() => {
  if (!pendingCheckpoint.value) return ''
  return `确定要恢复 Checkpoint "${pendingCheckpoint.value.checkpointId.slice(0, 8)}..." 吗？恢复后，会话将从该 Checkpoint 的状态继续执行。`
})

const deleteConfirmMessage = computed(() => {
  if (!pendingCheckpoint.value) return ''
  return `确定要删除 Checkpoint "${pendingCheckpoint.value.checkpointId.slice(0, 8)}..." 吗？此操作无法撤销，删除后将无法从此 Checkpoint 恢复。`
})

// 节点 ID 列表（用于筛选）
const nodeIds = ref<string[]>([])

// 加载数据
async function loadData() {
  if (props.sessionId) {
    await loadCheckpoints(props.sessionId)
    // 收集唯一的节点 ID（确保是数组）
    if (Array.isArray(checkpoints.value)) {
      const ids = new Set(checkpoints.value.map((cp) => cp.nodeId))
      nodeIds.value = Array.from(ids).sort()
    } else {
      nodeIds.value = []
    }
  }
}

// 查看详情
function handleViewDetail(checkpoint: Checkpoint) {
  selectedCheckpoint.value = checkpoint
  showDetailModal.value = true
}

// 恢复操作
function handleRestoreClick(checkpoint: Checkpoint) {
  pendingCheckpoint.value = checkpoint
  showRestoreConfirm.value = true
}

async function confirmRestore() {
  if (pendingCheckpoint.value) {
    const success = await restoreCheckpoint(pendingCheckpoint.value.checkpointId)
    if (success) {
      showRestoreConfirm.value = false
      pendingCheckpoint.value = null
      // 重新加载数据
      await loadData()
    }
  }
}

// 删除操作
function handleDeleteClick(checkpoint: Checkpoint) {
  pendingCheckpoint.value = checkpoint
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (pendingCheckpoint.value) {
    const success = await deleteCheckpoint(pendingCheckpoint.value.checkpointId)
    if (success) {
      showDeleteConfirm.value = false
      pendingCheckpoint.value = null
    }
  }
}

// 筛选操作
function handleSourceFilterChange(value: CheckpointSource | '') {
  if (value) {
    setFilter({ source: value })
  } else {
    clearFilter()
  }
}

function clearAllFilters() {
  sourceFilter.value = ''
  clearFilter()
}

// 生命周期
onMounted(() => {
  loadData()
})

watch(() => props.sessionId, loadData)
</script>

<template>
  <div class="checkpoint-viewer h-full flex flex-col">
    <!-- 头部 -->
    <div class="flex items-center justify-between px-4 py-3 border-b dark:border-gray-700">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        Checkpoints
      </h2>
      <div v-if="hasCheckpoints" class="text-sm text-gray-500 dark:text-gray-400">
        共 {{ filteredCheckpoints.length }} 个
      </div>
    </div>

    <!-- 筛选栏 -->
    <div v-if="hasCheckpoints" class="flex items-center gap-3 px-4 py-2 border-b dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
      <Filter class="w-4 h-4 text-gray-500 dark:text-gray-400" />
      <select
        v-model="sourceFilter"
        class="text-sm bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-1.5 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        @change="handleSourceFilterChange(sourceFilter)"
      >
        <option value="">全部来源</option>
        <option value="auto">自动</option>
        <option value="manual">手动</option>
        <option value="error">错误</option>
        <option value="restore">恢复</option>
      </select>

      <BaseButton
        v-if="filter.source"
        variant="ghost"
        size="sm"
        @click="clearAllFilters"
      >
        <X class="w-4 h-4 mr-1" />
        清除筛选
      </BaseButton>
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="mx-4 mt-4 p-3 bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 rounded-lg">
      <p class="text-sm text-red-600 dark:text-red-400">{{ error }}</p>
      <BaseButton
        variant="ghost"
        size="sm"
        class="mt-2"
        @click="clearError"
      >
        关闭
      </BaseButton>
    </div>

    <!-- 内容区域 -->
    <div class="flex-1 overflow-y-auto p-4">
      <!-- 加载状态 -->
      <div v-if="loading" class="flex items-center justify-center h-64">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
      </div>

      <!-- 空状态 -->
      <BaseEmpty
        v-else-if="!hasCheckpoints"
        type="inbox"
        title="暂无 Checkpoint"
        description="当 Agent 执行时会自动创建 Checkpoint"
      />

      <!-- 筛选无结果 -->
      <BaseEmpty
        v-else-if="filteredCheckpoints.length === 0"
        type="search"
        title="没有符合条件的 Checkpoint"
        description="请尝试调整筛选条件"
      >
        <BaseButton
          variant="ghost"
          size="sm"
          @click="clearAllFilters"
        >
          清除筛选
        </BaseButton>
      </BaseEmpty>

      <!-- Checkpoint 列表 -->
      <div v-else class="space-y-3">
        <div
          v-for="checkpoint in filteredCheckpoints"
          :key="checkpoint.checkpointId"
        >
          <CheckpointCard
            :checkpoint="checkpoint"
            :is-deleting="deleting && pendingCheckpoint?.checkpointId === checkpoint.checkpointId"
            :is-restoring="restoring && pendingCheckpoint?.checkpointId === checkpoint.checkpointId"
            @view-detail="handleViewDetail"
            @restore="handleRestoreClick"
            @delete="handleDeleteClick"
          />
        </div>
      </div>
    </div>

    <!-- 详情模态框 -->
    <CheckpointDetailModal
      v-if="selectedCheckpoint"
      :visible="showDetailModal"
      :checkpoint="selectedCheckpoint"
      @close="showDetailModal = false"
    />

    <!-- 恢复确认对话框 -->
    <ConfirmDialog
      :visible="showRestoreConfirm"
      type="info"
      title="确认恢复 Checkpoint"
      :message="restoreConfirmMessage"
      :hint="pendingCheckpoint ? `${pendingCheckpoint.nodeId} - ${pendingCheckpoint.checkpointId}` : undefined"
      :confirm-text="restoring ? '恢复中...' : '确认恢复'"
      cancel-text="取消"
      @confirm="confirmRestore"
      @cancel="showRestoreConfirm = false"
    />

    <!-- 删除确认对话框 -->
    <ConfirmDialog
      :visible="showDeleteConfirm"
      type="danger"
      title="确认删除 Checkpoint"
      :message="deleteConfirmMessage"
      :hint="pendingCheckpoint ? `${pendingCheckpoint.nodeId} - ${pendingCheckpoint.checkpointId}` : undefined"
      :confirm-text="deleting ? '删除中...' : '确认删除'"
      cancel-text="取消"
      @confirm="confirmDelete"
      @cancel="showDeleteConfirm = false"
    />
  </div>
</template>
