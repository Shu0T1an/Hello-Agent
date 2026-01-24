<template>
  <div class="timeline-test-page min-h-screen bg-slate-100 p-8">
    <div class="max-w-4xl mx-auto">
      <h1 class="text-2xl font-bold text-slate-900 mb-6">AgentTimeline 组件测试页面</h1>

      <!-- 控制面板 -->
      <div class="bg-white rounded-lg shadow-sm p-4 mb-6 flex flex-wrap items-center gap-4">
        <button
          @click="loadSampleData"
          class="px-4 py-2 bg-indigo-500 text-white rounded-lg hover:bg-indigo-600 transition-colors text-sm"
        >
          加载示例数据
        </button>
        <button
          @click="addRandomEvent"
          class="px-4 py-2 bg-emerald-500 text-white rounded-lg hover:bg-emerald-600 transition-colors text-sm"
        >
          添加随机事件
        </button>
        <button
          @click="clearEvents"
          class="px-4 py-2 bg-slate-500 text-white rounded-lg hover:bg-slate-600 transition-colors text-sm"
        >
          清空事件
        </button>
        <button
          @click="toggleLoading"
          class="px-4 py-2 border rounded-lg hover:bg-slate-50 transition-colors text-sm"
          :class="loading ? 'border-amber-500 text-amber-500' : 'border-slate-300 text-slate-600'"
        >
          {{ loading ? '停止加载' : '模拟加载' }}
        </button>
        <span class="text-sm text-slate-500">事件数量: {{ events.length }}</span>
      </div>

      <!-- AgentTimeline 组件 -->
      <AgentTimeline :events="events" :loading="loading" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AgentTimeline from '@/components/agent/AgentTimeline.vue'
import { type AgentEvent } from '@/types/agent'

const events = ref<AgentEvent[]>([])
const loading = ref(false)

// 示例数据 - 模拟完整的 Agent 执行流程
const sampleEvents: AgentEvent[] = [
  {
    eventType: 'starting',
    nodeId: 'agent_start',
    nodeType: 'custom',
    stateData: {
      input: '计算 1231 x 1231'
    },
    message: '开始执行 Agent',
    timestamp: new Date(Date.now() - 5000).toISOString()
  },
  {
    eventType: 'running',
    nodeId: 'llm_node',
    nodeType: 'llm',
    stateData: {
      input: '计算 1231 x 1231',
      execution_record: {
        toolCalls: [
          {
            id: 'call_abc123',
            name: 'multiply',
            arguments: JSON.stringify({ a: 1231, b: 1231 })
          }
        ]
      }
    },
    message: 'LLM 正在思考...',
    timestamp: new Date(Date.now() - 4000).toISOString()
  },
  {
    eventType: 'running',
    nodeId: 'tool_node',
    nodeType: 'tool',
    stateData: {
      input: '执行 multiply 函数',
      execution_record: {
        executions: [
          {
            id: 'exec_xyz789',
            name: 'multiply',
            success: true,
            arguments: JSON.stringify({ a: 1231, b: 1231 }),
            result: '1515361.0'
          }
        ]
      }
    },
    message: '执行工具调用',
    timestamp: new Date(Date.now() - 3000).toISOString()
  },
  {
    eventType: 'running',
    nodeId: 'llm_node',
    nodeType: 'llm',
    stateData: {
      input: '工具执行结果: 1515361.0',
      execution_record: {
        output: '计算完成！1231 x 1231 = 1,515,361'
      }
    },
    message: 'LLM 生成最终回复',
    timestamp: new Date(Date.now() - 2000).toISOString()
  },
  {
    eventType: 'GRAPH_COMPLETED',
    nodeId: 'agent_end',
    nodeType: 'custom',
    stateData: {},
    message: '图执行完成',
    timestamp: new Date(Date.now() - 1000).toISOString()
  }
]

// 加载示例数据
function loadSampleData() {
  events.value = [...sampleEvents]
}

// 添加随机事件
function addRandomEvent() {
  const eventTypes: AgentEvent['eventType'][] = ['starting', 'running', 'completed', 'failed']
  const nodeTypes: AgentEvent['nodeType'][] = ['llm', 'tool', 'custom']

  const randomEventType = eventTypes[Math.floor(Math.random() * eventTypes.length)]
  const randomNodeType = nodeTypes[Math.floor(Math.random() * nodeTypes.length)]

  const newEvent: AgentEvent = {
    eventType: randomEventType,
    nodeId: `node_${Date.now()}`,
    nodeType: randomNodeType,
    stateData: {},
    timestamp: new Date().toISOString()
  }

  // 根据节点类型添加不同的数据
  if (randomNodeType === 'llm') {
    const hasToolCalls = Math.random() > 0.5
    if (hasToolCalls) {
      newEvent.stateData.execution_record = {
        toolCalls: [
          {
            id: `call_${Date.now()}`,
            name: ['search', 'calculate', 'fetch'][Math.floor(Math.random() * 3)],
            arguments: JSON.stringify({ query: 'test data', limit: 10 })
          }
        ]
      }
    } else {
      newEvent.stateData.execution_record = {
        output: '这是一个模拟的 AI 回复内容，展示 LLM 响应节点的显示效果。'
      }
    }
  } else if (randomNodeType === 'tool') {
    newEvent.stateData.execution_record = {
      executions: [
        {
          id: `exec_${Date.now()}`,
          name: 'demo_tool',
          success: Math.random() > 0.2,
          arguments: JSON.stringify({ param1: 'value1', param2: 'value2' }),
          result: JSON.stringify({ status: 'success', data: [1, 2, 3, 4, 5] }, null, 2)
        }
      ]
    }
  }

  events.value.push(newEvent)
}

// 清空事件
function clearEvents() {
  events.value = []
}

// 切换加载状态
function toggleLoading() {
  loading.value = !loading.value
}

// 初始化时加载示例数据
loadSampleData()
</script>

<style scoped>
/* 无需自定义样式，使用 Tailwind */
</style>
