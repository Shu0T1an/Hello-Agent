<template>
  <div class="session-summary h-full flex flex-col overflow-hidden">
    <!-- 加载状态 -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="flex flex-col items-center gap-4 text-zinc-400">
        <Loader :size="40" class="animate-spin" />
        <p class="text-sm">加载摘要数据中...</p>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="flex-1 flex items-center justify-center p-6">
      <BaseEmpty
        type="error"
        :title="'加载失败'"
        :description="error"
        :action-text="'重试'"
        @action="$emit('retry')"
      />
    </div>

    <!-- 空状态 -->
    <div v-else-if="!summary" class="flex-1 flex items-center justify-center p-6">
      <BaseEmpty
        type="inbox"
        :title="'暂无统计数据'"
        :description="'该会话尚未生成统计数据，请先进行一些对话'"
      />
    </div>

    <!-- 摘要内容 -->
    <div v-else class="flex-1 overflow-y-auto custom-scrollbar-glass">
      <div class="max-w-4xl mx-auto p-6 space-y-6">
        <!-- 标题 -->
        <div class="flex items-center gap-3">
          <BarChart3 :size="24" class="text-indigo-600" />
          <h2 class="text-xl font-semibold text-zinc-900">{{ summary.title }}</h2>
        </div>

        <!-- 基础统计卡片 -->
        <div v-if="summary.basicStats" class="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <!-- 总 Token 数 -->
          <StatCard
            :icon="Coins"
            :label="'总 Token'"
            :value="formatNumber(summary.basicStats.totalTokens)"
            :color="'indigo'"
          />
          <!-- 工具调用次数 -->
          <StatCard
            :icon="Wrench"
            :label="'工具调用'"
            :value="formatNumber(summary.basicStats.totalToolCalls)"
            :color="'amber'"
          />
          <!-- 执行时长 -->
          <StatCard
            :icon="Clock"
            :label="'执行时长'"
            :value="formatDuration(summary.basicStats.totalDuration)"
            :color="'emerald'"
          />
          <!-- 迭代次数 -->
          <StatCard
            :icon="RefreshCw"
            :label="'迭代次数'"
            :value="formatNumber(summary.basicStats.totalIterations)"
            :color="'purple'"
          />
        </div>

        <!-- 时间信息 -->
        <div v-if="summary.basicStats?.startTime || summary.basicStats?.endTime" class="glass-card rounded-xl p-4">
          <h3 class="text-sm font-medium text-zinc-700 mb-3 flex items-center gap-2">
            <Calendar :size="16" />
            时间信息
          </h3>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div v-if="summary.basicStats.startTime">
              <span class="text-zinc-500">开始时间：</span>
              <span class="text-zinc-900 font-mono">{{ formatDateTime(summary.basicStats.startTime) }}</span>
            </div>
            <div v-if="summary.basicStats.endTime">
              <span class="text-zinc-500">结束时间：</span>
              <span class="text-zinc-900 font-mono">{{ formatDateTime(summary.basicStats.endTime) }}</span>
            </div>
          </div>
        </div>

        <!-- 工具调用统计 -->
        <div v-if="summary.toolStats && summary.toolStats.length > 0" class="glass-card rounded-xl overflow-hidden">
          <div class="px-4 py-3 border-b border-zinc-200">
            <h3 class="text-sm font-medium text-zinc-700 flex items-center gap-2">
              <Wrench :size="16" />
              工具调用统计
            </h3>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead class="bg-zinc-50">
                <tr>
                  <th class="px-4 py-3 text-left font-medium text-zinc-600">工具名称</th>
                  <th class="px-4 py-3 text-center font-medium text-zinc-600">调用次数</th>
                  <th class="px-4 py-3 text-center font-medium text-zinc-600">成功/失败</th>
                  <th class="px-4 py-3 text-center font-medium text-zinc-600">成功率</th>
                  <th class="px-4 py-3 text-right font-medium text-zinc-600">总耗时</th>
                  <th class="px-4 py-3 text-right font-medium text-zinc-600">平均耗时</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-zinc-100">
                <tr v-for="tool in summary.toolStats" :key="tool.toolName" class="hover:bg-zinc-50">
                  <td class="px-4 py-3">
                    <code class="px-2 py-1 bg-amber-100 text-amber-700 rounded text-xs font-mono">
                      {{ tool.toolName }}
                    </code>
                  </td>
                  <td class="px-4 py-3 text-center">{{ tool.callCount }}</td>
                  <td class="px-4 py-3 text-center">
                    <span class="text-emerald-600">{{ tool.successCount }}</span>
                    <span class="text-zinc-400">/</span>
                    <span class="text-red-600">{{ tool.failureCount }}</span>
                  </td>
                  <td class="px-4 py-3 text-center">
                    <span
                      :class="[
                        'px-2 py-1 rounded-full text-xs font-medium',
                        tool.successRate >= 80 ? 'bg-emerald-100 text-emerald-700' :
                        tool.successRate >= 50 ? 'bg-amber-100 text-amber-700' :
                        'bg-red-100 text-red-700'
                      ]"
                    >
                      {{ tool.successRate.toFixed(1) }}%
                    </span>
                  </td>
                  <td class="px-4 py-3 text-right font-mono text-zinc-600">
                    {{ formatDuration(tool.totalDuration) }}
                  </td>
                  <td class="px-4 py-3 text-right font-mono text-zinc-600">
                    {{ formatDuration(tool.avgDuration) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- LLM 调用详情 -->
        <div v-if="summary.llmCalls && summary.llmCalls.length > 0" class="glass-card rounded-xl overflow-hidden">
          <div class="px-4 py-3 border-b border-zinc-200">
            <h3 class="text-sm font-medium text-zinc-700 flex items-center gap-2">
              <Sparkles :size="16" />
              LLM 调用详情
            </h3>
          </div>
          <div class="divide-y divide-zinc-100">
            <div
              v-for="(call, index) in summary.llmCalls"
              :key="`${call.nodeId}-${index}`"
              class="p-4 hover:bg-zinc-50 transition-colors"
            >
              <div class="flex items-start justify-between gap-4">
                <div class="flex-1 space-y-2">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-sm font-medium text-zinc-900">调用 #{{ index + 1 }}</span>
                    <code v-if="call.nodeId" class="px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded text-xs font-mono">
                      {{ call.nodeId }}
                    </code>
                    <span v-if="call.iteration" class="text-xs px-2 py-0.5 bg-zinc-100 text-zinc-600 rounded-full">
                      迭代 {{ call.iteration }}
                    </span>
                  </div>

                  <!-- Token 统计 -->
                  <div v-if="call.totalTokens" class="flex items-center gap-4 text-xs text-zinc-600">
                    <span class="flex items-center gap-1">
                      <Coins :size="12" />
                      Prompt: {{ call.promptTokens }}
                    </span>
                    <span class="flex items-center gap-1">
                      <Coins :size="12" />
                      Completion: {{ call.completionTokens }}
                    </span>
                    <span class="flex items-center gap-1 font-medium text-indigo-600">
                      <Coins :size="12" />
                      Total: {{ call.totalTokens }}
                    </span>
                  </div>

                  <!-- 工具调用 -->
                  <div v-if="call.toolCalls && call.toolCalls.length > 0" class="flex flex-wrap gap-2">
                    <span
                      v-for="tool in call.toolCalls"
                      :key="tool"
                      class="px-2 py-1 bg-amber-100 text-amber-700 rounded text-xs font-mono"
                    >
                      {{ tool }}
                    </span>
                  </div>
                </div>

                <div class="flex flex-col items-end gap-1 text-xs text-zinc-500">
                  <span v-if="call.duration" class="flex items-center gap-1">
                    <Clock :size="12" />
                    {{ formatDuration(call.duration) }}
                  </span>
                  <span v-if="call.timestamp" class="text-zinc-400">
                    {{ formatTime(call.timestamp) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 无详细信息提示 -->
        <div v-if="!summary.basicStats && (!summary.toolStats || summary.toolStats.length === 0) && (!summary.llmCalls || summary.llmCalls.length === 0)" class="glass-card rounded-xl p-8">
          <BaseEmpty
            type="inbox"
            :title="'暂无详细统计数据'"
            :description="'该会话尚未生成详细的执行统计信息'"
            :show-action="false"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineComponent } from 'vue'
import {
  BarChart3,
  Coins,
  Wrench,
  Clock,
  RefreshCw,
  Calendar,
  Sparkles,
  Loader
} from 'lucide-vue-next'
import BaseEmpty from '@/components/base/BaseEmpty.vue'
import type { SessionSummary } from '@/types/summary'
import { formatDateTime, formatTime, formatDuration, formatNumber } from '@/utils/helpers'

interface Props {
  sessionId: string
  loading?: boolean
  summary?: SessionSummary | null
  error?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  summary: null,
  error: null
})

defineEmits<{
  retry: []
}>()

// 统计卡片组件
const StatCard = defineComponent({
  props: {
    icon: { type: Object, required: true },
    label: { type: String, required: true },
    value: { type: [String, Number], required: true },
    color: { type: String, default: 'indigo' }
  },
  computed: {
    colorClass() {
      const colors = {
        indigo: 'text-indigo-600',
        amber: 'text-amber-600',
        emerald: 'text-emerald-600',
        purple: 'text-purple-600'
      }
      return colors[this.color as keyof typeof colors] || 'text-zinc-600'
    }
  },
  template: `
    <div class="glass-card rounded-xl p-4 text-center">
      <component :is="icon" :size="24" :class="['mx-auto mb-2', colorClass]" />
      <div class="text-2xl font-bold text-zinc-900 mb-1">{{ value }}</div>
      <div class="text-xs text-zinc-500">{{ label }}</div>
    </div>
  `
})
</script>

<style scoped>
.session-summary {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
