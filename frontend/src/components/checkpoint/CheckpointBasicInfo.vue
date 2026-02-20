<script setup lang="ts">
import { computed } from 'vue'
import type { CheckpointDetail } from '@/types/checkpoint'
import {
  getCheckpointSourceLabel,
  getCheckpointSourceColor,
  getCheckpointSourceLightBgColor,
  getCheckpointNodeLabel,
  formatCheckpointFullTime,
} from '@/utils/checkpointHelpers'
import {
  Calendar,
  Hash,
  Layers,
  GitBranch,
  Activity,
  Clock,
} from 'lucide-vue-next'

interface Props {
  checkpoint: CheckpointDetail
}

const props = defineProps<Props>()

const sourceLabel = computed(() => getCheckpointSourceLabel(props.checkpoint.source))
const sourceColor = computed(() => getCheckpointSourceColor(props.checkpoint.source))
const sourceLightBgColor = computed(() => getCheckpointSourceLightBgColor(props.checkpoint.source))
const nodeLabel = computed(() => getCheckpointNodeLabel(props.checkpoint.nodeId))
const formattedTime = computed(() => formatCheckpointFullTime(props.checkpoint.createdAt))

const infoItems = computed(() => [
  {
    icon: Hash,
    label: 'Checkpoint ID',
    value: props.checkpoint.checkpointId,
    monospace: true,
  },
  {
    icon: Layers,
    label: 'Thread ID',
    value: props.checkpoint.threadId,
    monospace: true,
  },
  {
    icon: Activity,
    label: '节点',
    value: nodeLabel.value,
  },
  {
    icon: GitBranch,
    label: '迭代次数',
    value: String(props.checkpoint.iteration),
  },
  {
    icon: Clock,
    label: '创建时间',
    value: formattedTime.value,
  },
  {
    icon: Calendar,
    label: '来源',
    value: sourceLabel.value,
    badge: true,
    badgeColor: sourceColor.value,
    badgeBg: sourceLightBgColor.value,
  },
  {
    icon: GitBranch,
    label: '父 Checkpoint',
    value: props.checkpoint.parentId || '无',
    monospace: !!props.checkpoint.parentId,
  },
])
</script>

<template>
  <div class="checkpoint-basic-info space-y-4">
    <!-- 来源标签 -->
    <div class="flex items-center gap-2">
      <span
        class="px-3 py-1 text-sm font-medium rounded-full"
        :class="[sourceLightBgColor, sourceColor]"
      >
        {{ sourceLabel }}
      </span>
    </div>

    <!-- 基础信息列表 -->
    <div class="space-y-3">
      <div
        v-for="item in infoItems"
        :key="item.label"
        class="flex items-start gap-3"
      >
        <component
          :is="item.icon"
          class="w-5 h-5 text-gray-500 dark:text-gray-400 flex-shrink-0 mt-0.5"
        />
        <div class="flex-1 min-w-0">
          <div class="text-sm text-gray-500 dark:text-gray-400 mb-1">
            {{ item.label }}
          </div>
          <div
            v-if="item.badge"
            class="inline-flex px-2 py-1 text-sm rounded"
            :class="[item.badgeBg, item.badgeColor]"
          >
            {{ item.value }}
          </div>
          <div
            v-else
            class="text-gray-900 dark:text-gray-100 break-all"
            :class="{ 'font-mono text-xs': item.monospace }"
          >
            {{ item.value }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
