<script setup lang="ts">
import { computed } from 'vue'
import type { CheckpointDetail } from '@/types/checkpoint'
import { Activity, Cpu, Wrench, Clock, Hash } from 'lucide-vue-next'
import CodeHighlight from '@/components/base/CodeHighlight.vue'

interface Props {
  checkpoint: CheckpointDetail
}

const props = defineProps<Props>()

// 从状态中提取执行记录
const executionRecord = computed(() => {
  const state = props.checkpoint.state as Record<string, unknown>
  return state.execution_record as Record<string, unknown> | null
})

const executionHistory = computed(() => {
  const state = props.checkpoint.state as Record<string, unknown>
  const history = state.execution_history as unknown[]
  return Array.isArray(history) ? history : []
})

// LLM 执行记录
const llmRecord = computed(() => {
  if (!executionRecord.value) return null
  return executionRecord.value.llm as {
    nodeId: string
    usage?: { prompt: number; completion: number; total: number }
    toolCalls?: unknown[]
    inputMessages?: unknown[]
    startTime?: string
    endTime?: string
    duration?: number
  } | null
})

// 工具执行记录
const toolRecord = computed(() => {
  if (!executionRecord.value) return null
  return executionRecord.value.tool as {
    executions?: Array<{
      id: string
      name: string
      arguments: Record<string, unknown>
      result: unknown
      success: boolean
      duration: number
    }>
  } | null
})

// 格式化持续时间
function formatDuration(ms?: number): string {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

// 格式化时间戳
function formatTime(timestamp?: string): string {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

// 检查是否有执行记录
const hasExecutionRecord = computed(() => {
  return executionRecord.value || executionHistory.value.length > 0
})
</script>

<template>
  <div class="checkpoint-execution-record space-y-6">
    <!-- 空状态 -->
    <div v-if="!hasExecutionRecord" class="text-center py-8 text-gray-500 dark:text-gray-400">
      暂无执行记录
    </div>

    <!-- 当前执行记录 -->
    <div v-if="executionRecord">
      <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3 flex items-center gap-2">
        <Activity class="w-4 h-4" />
        当前执行记录
      </h3>

      <!-- LLM 记录 -->
      <div v-if="llmRecord" class="mb-4">
        <div class="flex items-center gap-2 mb-2">
          <Cpu class="w-4 h-4 text-blue-500" />
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100">LLM 调用</span>
        </div>

        <div class="pl-6 space-y-2 text-sm">
          <div class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">节点 ID:</span>
            <span class="font-mono text-gray-900 dark:text-gray-100">{{ llmRecord.nodeId }}</span>
          </div>

          <div v-if="llmRecord.usage" class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">Token 使用:</span>
            <span class="text-gray-900 dark:text-gray-100">
              {{ llmRecord.usage.total }} (P: {{ llmRecord.usage.prompt }}, C: {{ llmRecord.usage.completion }})
            </span>
          </div>

          <div v-if="llmRecord.startTime" class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">开始时间:</span>
            <span class="text-gray-900 dark:text-gray-100">{{ formatTime(llmRecord.startTime) }}</span>
          </div>

          <div v-if="llmRecord.duration" class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">持续时间:</span>
            <span class="text-gray-900 dark:text-gray-100">{{ formatDuration(llmRecord.duration) }}</span>
          </div>

          <div v-if="llmRecord.toolCalls && llmRecord.toolCalls.length > 0">
            <div class="text-gray-500 dark:text-gray-400 mb-1">工具调用:</div>
            <div class="space-y-2 pl-4">
              <div
                v-for="(call, idx) in llmRecord.toolCalls"
                :key="idx"
                class="tool-call-item"
              >
                <div class="flex items-center gap-1 mb-1">
                  <Hash class="w-3 h-3 text-gray-500" />
                  <span class="text-xs text-gray-600 dark:text-gray-400">调用 #{{ idx + 1 }}</span>
                </div>
                <CodeHighlight
                  :code="JSON.stringify(call, null, 2)"
                  language="json"
                  size="xs"
                  :show-header="false"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 工具记录 -->
      <div v-if="toolRecord && toolRecord.executions && toolRecord.executions.length > 0">
        <div class="flex items-center gap-2 mb-2">
          <Wrench class="w-4 h-4 text-green-500" />
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100">工具执行</span>
        </div>

        <div class="pl-6 space-y-2">
          <div
            v-for="(exec, idx) in toolRecord.executions"
            :key="idx"
            class="border border-gray-200 dark:border-gray-700 rounded-lg p-3"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="font-medium text-gray-900 dark:text-gray-100">{{ exec.name }}</span>
              <span
                class="px-2 py-0.5 text-xs rounded-full"
                :class="exec.success
                  ? 'bg-green-100 dark:bg-green-900 text-green-700 dark:text-green-300'
                  : 'bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300'"
              >
                {{ exec.success ? '成功' : '失败' }}
              </span>
            </div>

            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-gray-500 dark:text-gray-400">ID:</span>
                <span class="font-mono text-xs text-gray-900 dark:text-gray-100">{{ exec.id }}</span>
              </div>

              <div class="flex justify-between">
                <span class="text-gray-500 dark:text-gray-400">持续时间:</span>
                <span class="text-gray-900 dark:text-gray-100">{{ formatDuration(exec.duration) }}</span>
              </div>

              <details class="group">
                <summary class="cursor-pointer text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 text-xs">
                  查看参数和结果
                </summary>
                <div class="mt-3 space-y-3">
                  <div>
                    <div class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 flex items-center gap-1">
                      <span class="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
                      参数
                    </div>
                    <CodeHighlight
                      :code="JSON.stringify(exec.arguments, null, 2)"
                      language="json"
                      size="xs"
                      :show-header="false"
                    />
                  </div>
                  <div>
                    <div class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 flex items-center gap-1">
                      <span :class="[
                        'w-1.5 h-1.5 rounded-full',
                        exec.success ? 'bg-green-500' : 'bg-red-500'
                      ]"></span>
                      结果
                    </div>
                    <CodeHighlight
                      :code="JSON.stringify(exec.result, null, 2)"
                      language="json"
                      size="xs"
                      :show-header="false"
                    />
                  </div>
                </div>
              </details>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 执行历史 -->
    <div v-if="executionHistory.length > 0">
      <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3 flex items-center gap-2">
        <Clock class="w-4 h-4" />
        执行历史 ({{ executionHistory.length }})
      </h3>

      <div class="space-y-3">
        <div
          v-for="(record, idx) in executionHistory"
          :key="idx"
          class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden"
        >
          <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 dark:bg-gray-800/50 border-b border-gray-200 dark:border-gray-700">
            <Hash class="w-4 h-4 text-gray-500" />
            <span class="font-medium text-sm text-gray-900 dark:text-gray-100">执行记录 #{{ idx + 1 }}</span>
          </div>
          <div class="p-3">
            <CodeHighlight
              :code="JSON.stringify(record, null, 2)"
              language="json"
              size="xs"
              :show-header="false"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
