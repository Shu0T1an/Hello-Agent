<template>
  <div class="agent-timeline h-full flex flex-col">
    <div class="timeline-header flex items-center gap-2 pb-4 border-b border-slate-200">
      <Activity class="text-indigo-500" :size="18" />
      <span class="font-semibold text-slate-900">Execution Timeline</span>
      <span v-if="events.length > 0" class="text-xs text-slate-500 ml-2">({{ events.length }} events)</span>
    </div>

    <div class="timeline-content flex-1 overflow-y-auto">
      <div v-if="events.length === 0" class="flex flex-col items-center justify-center py-12 text-slate-400">
        <Activity :size="48" class="opacity-30 mb-3" />
        <p class="text-sm">{{ loading ? 'Waiting for events...' : 'No events recorded' }}</p>
      </div>

      <div v-else class="space-y-4">
        <div v-for="(event, index) in processedEvents" :key="getEventId(event, index)" class="timeline-event flex gap-3">
          <div class="timeline-dot w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 mt-1" :class="getNodeConfig(event).dotBg">
            <component :is="getNodeConfig(event).icon" :size="16" class="text-white" />
          </div>

          <div class="card flex-1 bg-white border rounded-lg overflow-hidden transition-all duration-200 hover:shadow-md" :class="getNodeConfig(event).borderClass">
            <div class="header flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-slate-50 transition-colors" @click="toggleExpand(getEventId(event, index))">
              <div class="flex items-center gap-2">
                <span class="font-medium text-sm" :class="getNodeConfig(event).labelClass">{{ getNodeConfig(event).label }}</span>
                <span v-if="event.nodeId" class="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">{{ event.nodeId }}</span>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-xs text-slate-500">{{ formatTimestamp(event.timestamp) }}</span>
                <ChevronDown v-if="isExpanded(getEventId(event, index))" :size="16" class="text-slate-400" />
                <ChevronRight v-else :size="16" class="text-slate-400" />
              </div>
            </div>

            <div v-if="isExpanded(getEventId(event, index))" class="body px-4 py-3 bg-slate-50 border-t border-slate-100">
              <div v-if="isLLMToolCall(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-indigo-700">
                  <Sparkles :size="14" />
                  <span class="text-sm font-medium">Function Call</span>
                </div>
                <div v-for="(toolCall, idx) in event.stateData?.execution_record?.toolCalls" :key="toolCall.id || idx" class="tool-call-info">
                  <div class="flex items-center gap-2 mb-2">
                    <code class="text-xs font-mono px-2 py-1 bg-indigo-100 text-indigo-700 rounded">{{ toolCall.name }}</code>
                  </div>
                  <div class="text-xs text-slate-600 mb-1">Arguments:</div>
                  <pre class="font-mono text-xs bg-white border border-slate-200 rounded p-3 overflow-x-auto text-slate-700">{{ formatJSON(toolCall.arguments) }}</pre>
                </div>
              </div>

              <div v-else-if="isLLMResponse(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-emerald-700">
                  <MessageSquare :size="14" />
                  <span class="text-sm font-medium">AI Response</span>
                </div>
                <div class="response-info text-sm text-slate-700 whitespace-pre-wrap">{{ event.stateData?.execution_record?.output || 'No output' }}</div>
              </div>

              <div v-else-if="isToolNode(event)" class="space-y-3">
                <div class="flex items-center gap-2 text-amber-700">
                  <Terminal :size="14" />
                  <span class="text-sm font-medium">Tool Execution</span>
                </div>
                <div v-for="(execution, idx) in event.stateData?.execution_record?.executions" :key="execution.id || idx" class="tool-execution">
                  <div class="flex items-center gap-2 mb-2">
                    <Wrench :size="14" class="text-slate-500" />
                    <code class="text-xs font-mono px-2 py-1 bg-amber-100 text-amber-700 rounded">{{ execution.name }}</code>
                    <span class="text-xs px-2 py-0.5 rounded-full" :class="execution.success ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'">{{ execution.success ? 'Success' : 'Failed' }}</span>
                  </div>
                  <div class="text-xs text-slate-600 mb-1">Arguments:</div>
                  <pre class="font-mono text-xs bg-white border border-slate-200 rounded p-2 mb-2 overflow-x-auto text-slate-700">{{ formatJSON(execution.arguments) }}</pre>
                  <div class="text-xs text-slate-600 mb-1">Result:</div>
                  <pre class="font-mono text-xs bg-white border border-slate-200 rounded p-3 overflow-x-auto text-slate-700">{{ execution.result }}</pre>
                </div>
              </div>

              <div v-else class="space-y-3">
                <div class="flex items-center gap-2 text-slate-700">
                  <Cog :size="14" />
                  <span class="text-sm font-medium">Node Event</span>
                </div>
                <div v-if="event.message" class="text-sm text-slate-600">{{ event.message }}</div>
                <div v-if="event.stateData?.input" class="text-xs text-slate-600">
                  <span class="font-medium">Input:</span>
                  <pre class="font-mono text-xs bg-white border border-slate-200 rounded p-2 mt-1 overflow-x-auto">{{ event.stateData.input }}</pre>
                </div>
              </div>

              <div class="raw-data-actions mt-4 flex items-center gap-3">
                <button @click.stop="toggleRawData(getEventId(event, index))" class="raw-data-toggle text-xs flex items-center gap-1 text-slate-500 hover:text-slate-700 transition-colors">
                  <Eye v-if="!showRawData[getEventId(event, index)]" :size="12" />
                  <EyeOff v-else :size="12" />
                  <span>{{ showRawData[getEventId(event, index)] ? 'Hide' : 'Show' }} Raw Data</span>
                </button>
                <button v-if="showRawData[getEventId(event, index)]" @click.stop="cycleStyle" class="style-toggle text-xs flex items-center gap-1 text-slate-500 hover:text-slate-700 transition-colors">
                  <Palette :size="12" />
                  <span>切换风格 ({{ currentStyle }})</span>
                </button>
              </div>

              <pre v-if="showRawData[getEventId(event, index)]" class="raw-json font-mono text-xs rounded p-3 mt-3 overflow-x-auto" :class="[rawDataStyles[currentStyle].bg, rawDataStyles[currentStyle].text]">{{ JSON.stringify(event, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading && events.length > 0" class="flex items-center justify-center gap-2 py-4 text-slate-400">
        <Loader :size="16" class="animate-spin" />
        <span class="text-sm">Processing...</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Activity, Play, Bot, Terminal, ChevronDown, ChevronRight, Sparkles, MessageSquare, Wrench, Cog, Loader, Eye, EyeOff, Palette } from 'lucide-vue-next'
import { type AgentEvent } from '@/types/agent'

// Raw Data 风格类型定义
type RawDataStyle = 'dark' | 'light' | 'solarized'

// Raw Data 风格配置
const rawDataStyles: Record<RawDataStyle, { bg: string, text: string }> = {
  dark: { bg: 'bg-slate-900', text: 'text-slate-100' },
  light: { bg: 'bg-slate-100', text: 'text-slate-900' },
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

const processedEvents = computed(() => props.events)

function getEventId(event: AgentEvent, index: number): string {
  return `${event.nodeId}-${event.timestamp}-${index}`
}

function formatTimestamp(ts: string): string {
  try {
    const date = new Date(ts)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return ts
  }
}

function formatJSON(jsonStr: string): string {
  try {
    const parsed = JSON.parse(jsonStr)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return jsonStr
  }
}

function isExpanded(eventId: string): boolean {
  return expandedEvents.value.has(eventId)
}

function toggleExpand(eventId: string): void {
  if (expandedEvents.value.has(eventId)) {
    expandedEvents.value.delete(eventId)
  } else {
    expandedEvents.value.add(eventId)
  }
}

function toggleRawData(eventId: string): void {
  showRawData.value[eventId] = !showRawData.value[eventId]
}

function cycleStyle(): void {
  const styles: RawDataStyle[] = ['dark', 'light', 'solarized']
  const currentIndex = styles.indexOf(currentStyle.value)
  currentStyle.value = styles[(currentIndex + 1) % styles.length]
}

function isToolNode(event: AgentEvent): boolean {
  return event.nodeType === 'tool'
}

function isLLMToolCall(event: AgentEvent): boolean {
  if (event.nodeType !== 'llm') return false
  const toolCalls = event.stateData?.execution_record?.toolCalls
  return toolCalls !== undefined && toolCalls.length > 0
}

function isLLMResponse(event: AgentEvent): boolean {
  if (event.nodeType !== 'llm') return false
  const toolCalls = event.stateData?.execution_record?.toolCalls
  return !toolCalls || toolCalls.length === 0
}

const colorThemes = {
  starting: {
    icon: Play,
    label: '已启动',
    dotBg: 'bg-slate-400',
    borderClass: 'border-slate-200',
    labelClass: 'text-slate-600'
  },
  llm_tool_call: {
    icon: Bot,
    label: 'AI Thinking',
    dotBg: 'bg-indigo-500',
    borderClass: 'border-indigo-200',
    labelClass: 'text-indigo-700'
  },
  llm_response: {
    icon: MessageSquare,
    label: 'AI Response',
    dotBg: 'bg-emerald-500',
    borderClass: 'border-emerald-200',
    labelClass: 'text-emerald-700'
  },
  tool_execution: {
    icon: Terminal,
    label: 'Tool Execution',
    dotBg: 'bg-amber-500',
    borderClass: 'border-amber-200',
    labelClass: 'text-amber-700'
  },
  custom: {
    icon: Cog,
    label: 'System Node',
    dotBg: 'bg-slate-500',
    borderClass: 'border-slate-200',
    labelClass: 'text-slate-700'
  }
}

function getNodeConfig(event: AgentEvent) {
  if (event.eventType === 'starting') return colorThemes.starting
  if (isLLMToolCall(event)) return colorThemes.llm_tool_call
  if (isLLMResponse(event)) return colorThemes.llm_response
  if (isToolNode(event)) return colorThemes.tool_execution
  return colorThemes.custom
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
