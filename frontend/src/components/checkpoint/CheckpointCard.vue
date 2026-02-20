<script setup lang="ts">
import { computed } from 'vue'
import type { Checkpoint } from '@/types/checkpoint'
import {
  getCheckpointSourceColor,
  getCheckpointSourceBgColor,
  getCheckpointSourceBorderColor,
  getCheckpointSourceLightBgColor,
  getCheckpointSourceLabel,
  formatCheckpointTime,
  getCheckpointNodeLabel,
  getCheckpointNodeIcon,
} from '@/utils/checkpointHelpers'
import { Play, Wrench, Cpu, Flag, Circle, Eye, RotateCcw, Trash2 } from 'lucide-vue-next'

interface Props {
  checkpoint: Checkpoint
  isDeleting?: boolean
  isRestoring?: boolean
}

interface Emits {
  (e: 'viewDetail', checkpoint: Checkpoint): void
  (e: 'restore', checkpoint: Checkpoint): void
  (e: 'delete', checkpoint: Checkpoint): void
}

const props = withDefaults(defineProps<Props>(), {
  isDeleting: false,
  isRestoring: false,
})

const emit = defineEmits<Emits>()

const nodeIcon = computed(() => {
  const iconName = getCheckpointNodeIcon(props.checkpoint.nodeId)
  const icons: Record<string, typeof Play> = {
    play: Play,
    wrench: Wrench,
    cpu: Cpu,
    flag: Flag,
    circle: Circle,
  }
  return icons[iconName] || Circle
})

const nodeLabel = computed(() => getCheckpointNodeLabel(props.checkpoint.nodeId))
const sourceLabel = computed(() => getCheckpointSourceLabel(props.checkpoint.source))
const sourceColor = computed(() => getCheckpointSourceColor(props.checkpoint.source))
const sourceBgColor = computed(() => getCheckpointSourceBgColor(props.checkpoint.source))
const sourceBorderColor = computed(() => getCheckpointSourceBorderColor(props.checkpoint.source))
const sourceLightBgColor = computed(() => getCheckpointSourceLightBgColor(props.checkpoint.source))
const formattedTime = computed(() => formatCheckpointTime(props.checkpoint.createdAt))

function handleViewDetail() {
  emit('viewDetail', props.checkpoint)
}

function handleRestore() {
  emit('restore', props.checkpoint)
}

function handleDelete() {
  emit('delete', props.checkpoint)
}
</script>

<template>
  <div
    class="checkpoint-card group relative overflow-hidden rounded-lg border-2 bg-white dark:bg-gray-800 transition-all duration-200 hover:shadow-lg"
    :class="[
      sourceBorderColor,
      'border-l-4',
    ]"
  >
    <!-- 状态指示条 -->
    <div
      class="absolute left-0 top-0 bottom-0 w-1 transition-all duration-200 group-hover:w-1.5"
      :class="sourceBgColor"
    />

    <div class="flex items-start gap-3 p-4">
      <!-- 节点图标 -->
      <div
        class="flex-shrink-0 flex items-center justify-center w-10 h-10 rounded-full"
        :class="sourceLightBgColor"
      >
        <component :is="nodeIcon" class="w-5 h-5" :class="sourceColor" />
      </div>

      <!-- 内容 -->
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-1">
          <h3 class="font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ nodeLabel }}
          </h3>
          <span
            class="px-2 py-0.5 text-xs font-medium rounded-full"
            :class="[sourceLightBgColor, sourceColor]"
          >
            {{ sourceLabel }}
          </span>
        </div>

        <div class="flex items-center gap-3 text-sm text-gray-500 dark:text-gray-400">
          <span class="font-mono text-xs">{{ checkpoint.checkpointId.slice(0, 8) }}</span>
          <span>迭代 {{ checkpoint.iteration }}</span>
          <span>{{ formattedTime }}</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
          title="查看详情"
          @click="handleViewDetail"
        >
          <Eye class="w-4 h-4" />
        </button>

        <button
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 hover:text-green-600 dark:hover:text-green-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="恢复"
          :disabled="isRestoring"
          @click="handleRestore"
        >
          <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': isRestoring }" />
        </button>

        <button
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 hover:text-red-600 dark:hover:text-red-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="删除"
          :disabled="isDeleting"
          @click="handleDelete"
        >
          <Trash2 class="w-4 h-4" :class="{ 'animate-pulse': isDeleting }" />
        </button>
      </div>
    </div>
  </div>
</template>
