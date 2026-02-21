<template>
  <div class="agent-timeline h-full flex flex-col">
    <div class="timeline-header flex items-center gap-2 pb-4 border-b border-zinc-200">
      <Activity class="text-indigo-500" :size="18" />
      <span class="font-semibold text-zinc-900">Execution Timeline</span>
      <span v-if="processedEvents.length > 0" class="text-xs text-zinc-500 ml-2">({{ processedEvents.length }} events)</span>
    </div>

    <div v-if="subAgentOverview.total > 0" class="px-4 pt-3 pb-2 border-b border-zinc-100 bg-zinc-50">
      <div class="flex items-center gap-2 flex-wrap text-xs">
        <span class="px-2 py-1 rounded bg-cyan-100 text-cyan-800">SubAgents {{ subAgentOverview.total }}</span>
        <span class="px-2 py-1 rounded bg-zinc-200 text-zinc-800">Running {{ subAgentOverview.running }}</span>
        <span class="px-2 py-1 rounded bg-amber-100 text-amber-800">Queued {{ subAgentOverview.queued }}</span>
        <span class="px-2 py-1 rounded bg-sky-100 text-sky-800">Done {{ subAgentOverview.completed }}</span>
        <span class="px-2 py-1 rounded bg-rose-100 text-rose-800">Failed {{ subAgentOverview.failed }}</span>
      </div>
    </div>

    <div class="timeline-content flex-1 overflow-y-auto">
      <div v-if="processedEvents.length === 0" class="flex flex-col items-center justify-center py-12 text-zinc-400">
        <Activity :size="48" class="opacity-30 mb-3" />
        <p class="text-sm">{{ loading ? 'Waiting for events...' : 'No events recorded' }}</p>
      </div>

      <div v-else class="space-y-4">
        <div v-for="(event, index) in processedEvents" :key="getEventId(event, index)" class="timeline-event flex gap-3">
          <div class="timeline-dot w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 mt-1" :class="getNodeConfig(event).dotBg">
            <component :is="getNodeConfig(event).icon" :size="16" class="text-white" />
          </div>

          <div class="card flex-1 bg-white border rounded-lg overflow-hidden transition-all duration-200 hover:shadow-md" :class="getNodeConfig(event).borderClass">
            <div class="header flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-zinc-50 transition-colors" @click="toggleExpand(getEventId(event, index))">
              <div class="flex items-center gap-2">
                <span class="font-medium text-sm" :class="getNodeConfig(event).labelClass">{{ getNodeConfig(event).label }}</span>
                <span v-if="event.nodeId" class="text-xs px-2 py-0.5 rounded-full bg-zinc-100 text-zinc-600 font-mono">{{ event.nodeId }}</span>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-xs text-zinc-500">{{ formatDateTime(event.timestamp) }}</span>
                <ChevronDown v-if="isExpanded(getEventId(event, index))" :size="16" class="text-zinc-400" />
                <ChevronRight v-else :size="16" class="text-zinc-400" />
              </div>
            </div>

            <div v-if="isExpanded(getEventId(event, index))" class="body px-4 py-3 bg-zinc-50 border-t border-zinc-100">
              <div v-if="isLLMToolCall(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-indigo-700">
                  <Sparkles :size="14" />
                  <span class="text-sm font-medium">Function Call</span>
                </div>
                <div v-for="(toolCall, idx) in event.stateData?.execution_record?.toolCalls" :key="toolCall.id || idx" class="tool-call-info">
                  <div class="flex items-center gap-2 mb-2">
                    <code class="text-xs font-mono px-2 py-1 bg-indigo-100 text-indigo-700 rounded">{{ toolCall.name }}</code>
                  </div>
                  <div class="text-xs text-zinc-600 mb-1">Arguments:</div>
                  <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-3 overflow-x-auto text-zinc-700">{{ formatJSON(toolCall.arguments) }}</pre>
                </div>
              </div>

              <div v-else-if="isLLMResponse(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-emerald-700">
                  <MessageSquare :size="14" />
                  <span class="text-sm font-medium">AI Response</span>
                </div>
                <div class="response-info text-sm text-zinc-700 whitespace-pre-wrap">{{ event.stateData?.execution_record?.output || 'No output' }}</div>
              </div>

              <div v-else-if="isToolNode(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-amber-700">
                  <Terminal :size="14" />
                  <span class="text-sm font-medium">Tool Execution</span>
                </div>
                <div v-for="(execution, idx) in event.stateData?.execution_record?.executions" :key="execution.id || idx" class="tool-execution">
                  <div class="flex items-center gap-2 mb-2">
                    <Wrench :size="14" class="text-zinc-500" />
                    <code class="text-xs font-mono px-2 py-1 bg-amber-100 text-amber-700 rounded">{{ execution.name }}</code>
                    <span class="text-xs px-2 py-0.5 rounded-full" :class="execution.success ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'">{{ execution.success ? 'Success' : 'Failed' }}</span>
                  </div>
                  <div class="text-xs text-zinc-600 mb-1">Arguments:</div>
                  <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-2 mb-2 overflow-x-auto text-zinc-700">{{ formatJSON(execution.arguments) }}</pre>
                  <div class="text-xs text-zinc-600 mb-1">Result:</div>
                  <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-3 overflow-x-auto text-zinc-700">{{ execution.result }}</pre>
                </div>
              </div>

              <div v-else-if="isSubAgentEvent(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-cyan-700">
                  <GitBranch :size="14" />
                  <span class="text-sm font-medium">SubAgent Progress</span>
                </div>
                <div class="text-xs text-zinc-700 space-y-1">
                  <div><span class="font-medium">Type:</span> {{ event.metadata?.subagentType || 'unknown' }}</div>
                  <div><span class="font-medium">Phase:</span> {{ event.metadata?.phase || event.eventType }}</div>
                  <div><span class="font-medium">Progress:</span> {{ event.metadata?.progress ?? 0 }}%</div>
                  <div v-if="event.metadata?.durationMs !== undefined">
                    <span class="font-medium">Duration:</span> {{ event.metadata?.durationMs }} ms
                  </div>
                </div>
                <div v-if="event.metadata?.summary" class="text-sm text-zinc-700 whitespace-pre-wrap">
                  {{ event.metadata?.summary }}
                </div>
                <div v-if="event.metadata?.errorMessage" class="text-sm text-rose-700 whitespace-pre-wrap">
                  {{ event.metadata?.errorMessage }}
                </div>
              </div>

              <div v-else class="space-y-3">
                <div class="flex items-center gap-2 text-zinc-700">
                  <Cog :size="14" />
                  <span class="text-sm font-medium">Node Event</span>
                </div>
                <div v-if="event.message" class="text-sm text-zinc-600">{{ event.message }}</div>
                <div v-if="event.stateData?.input" class="text-xs text-zinc-600">
                  <span class="font-medium">Input:</span>
                  <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-2 mt-1 overflow-x-auto">{{ event.stateData.input }}</pre>
                </div>
              </div>

              <div class="raw-data-actions mt-4 flex items-center gap-3">
                <button @click.stop="toggleRawData(getEventId(event, index))" class="raw-data-toggle text-xs flex items-center gap-1 text-zinc-500 hover:text-zinc-700 transition-colors">
                  <Eye v-if="!showRawData[getEventId(event, index)]" :size="12" />
                  <EyeOff v-else :size="12" />
                  <span>{{ showRawData[getEventId(event, index)] ? 'Hide' : 'Show' }} Raw Data</span>
                </button>
                <button v-if="showRawData[getEventId(event, index)]" @click.stop="cycleStyle" class="style-toggle text-xs flex items-center gap-1 text-zinc-500 hover:text-zinc-700 transition-colors">
                  <Palette :size="12" />
                  <span>切换风格 ({{ currentStyle }})</span>
                </button>
              </div>

              <pre v-if="showRawData[getEventId(event, index)]" class="raw-json font-mono text-xs rounded p-3 mt-3 overflow-x-auto" :class="[rawDataStyles[currentStyle].bg, rawDataStyles[currentStyle].text]">{{ JSON.stringify(event, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading && processedEvents.length > 0" class="flex items-center justify-center gap-2 py-4 text-zinc-400">
        <Loader :size="16" class="animate-spin" />
        <span class="text-sm">Processing...</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Activity, ChevronDown, ChevronRight, Sparkles, MessageSquare, Wrench, Cog, Loader, Eye, EyeOff, Palette, GitBranch } from 'lucide-vue-next'
import type { AgentEvent } from '@/types/agent'
import { formatDateTime, formatJSON } from '@/utils/helpers'
import { isToolNode, isLLMToolCall, isLLMResponse, isSubAgentEvent, getNodeConfig, getEventId } from '@/utils/agentEvents'

// Raw Data 风格类型定义
type RawDataStyle = 'dark' | 'light' | 'solarized'

// Raw Data 风格配置
const rawDataStyles: Record<RawDataStyle, { bg: string; text: string }> = {
  dark: { bg: 'bg-zinc-900', text: 'text-zinc-100' },
  light: { bg: 'bg-zinc-100', text: 'text-zinc-900' },
  solarized: { bg: 'bg-amber-50', text: 'text-amber-950' }
}

interface Props {
  events: AgentEvent[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const expandedEvents = ref<Set<string>>(new Set())
const showRawData = ref<Record<string, boolean>>({})
const currentStyle = ref<RawDataStyle>('dark')

const subAgentLatestEvents = computed(() => {
  const latestByTaskId = new Map<string, AgentEvent>()

  for (const event of props.events) {
    if (!isSubAgentEvent(event)) continue
    const taskId = event.metadata?.subagentTaskId
    if (!taskId || typeof taskId !== 'string') continue
    latestByTaskId.set(taskId, event)
  }

  return Array.from(latestByTaskId.values())
})

const subAgentOverview = computed(() => {
  let queued = 0
  let running = 0
  let completed = 0
  let failed = 0

  for (const event of subAgentLatestEvents.value) {
    const status = getSubAgentStatus(event)
    if (status === 'queued') queued++
    if (status === 'running') running++
    if (status === 'completed') completed++
    if (status === 'failed') failed++
  }

  return {
    total: subAgentLatestEvents.value.length,
    queued,
    running,
    completed,
    failed
  }
})

const processedEvents = computed(() => {
  const merged: AgentEvent[] = []
  const latestByTaskId = new Map<string, AgentEvent>()

  for (const event of props.events) {
    if (!isSubAgentEvent(event)) {
      merged.push(event)
      continue
    }
    const taskId = event.metadata?.subagentTaskId
    if (!taskId || typeof taskId !== 'string') {
      merged.push(event)
      continue
    }
    latestByTaskId.set(taskId, event)
  }

  merged.push(...latestByTaskId.values())

  merged.sort((a, b) => {
    const timeA = Date.parse(a.timestamp || '')
    const timeB = Date.parse(b.timestamp || '')
    return timeA - timeB
  })

  return merged
})

type SubAgentStatus = 'queued' | 'running' | 'completed' | 'failed'

function getSubAgentStatus(event: AgentEvent): SubAgentStatus {
  if (event.eventType === 'SUBAGENT_FAILED') return 'failed'
  if (event.eventType === 'SUBAGENT_COMPLETED') return 'completed'
  if (event.metadata?.phase === 'queued') return 'queued'
  return 'running'
}

// 判断事件是否展开
function isExpanded(eventId: string): boolean {
  return expandedEvents.value.has(eventId)
}

// 切换事件展开状态
function toggleExpand(eventId: string): void {
  if (expandedEvents.value.has(eventId)) {
    expandedEvents.value.delete(eventId)
  } else {
    expandedEvents.value.add(eventId)
  }
}

// 切换原始数据显示
function toggleRawData(eventId: string): void {
  showRawData.value[eventId] = !showRawData.value[eventId]
}

// 循环切换 Raw Data 风格
function cycleStyle(): void {
  const styles: RawDataStyle[] = ['dark', 'light', 'solarized']
  const currentIndex = styles.indexOf(currentStyle.value)
  const nextStyle = styles[(currentIndex + 1) % styles.length]
  if (nextStyle) {
    currentStyle.value = nextStyle
  }
}
</script>

<style scoped>
.agent-timeline {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.timeline-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
</style>
