<template>
  <div class="p-6 min-h-screen bg-gray-50">
    <h1 class="text-2xl font-bold mb-4">审批UI测试</h1>

    <!-- 调试信息 -->
    <div class="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-4">
      <h3 class="font-bold text-yellow-800 mb-2">调试信息</h3>
      <pre class="text-xs">{{ JSON.stringify(testInterruptedMessage, null, 2) }}</pre>
    </div>

    <div class="space-y-4">
      <div class="bg-white rounded-lg p-4 shadow-sm">
        <h2 class="font-semibold mb-2">测试中断消息（应该显示审批按钮）</h2>
        <ChatMessage
          :message="testInterruptedMessage"
          agent-name="TestAgent"
          session-id="test-session-123"
        />
      </div>

      <div class="bg-white rounded-lg p-4 shadow-sm">
        <h2 class="font-semibold mb-2">普通AI消息（对比）</h2>
        <ChatMessage
          :message="testNormalMessage"
          agent-name="TestAgent"
        />
      </div>

      <!-- 直接显示审批按钮测试 -->
      <div class="bg-white rounded-lg p-4 shadow-sm">
        <h2 class="font-semibold mb-2">直接测试ApprovalDialog组件</h2>
        <button
          @click="showDirectDialog = true"
          class="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
        >
          打开审批对话框
        </button>
        <ApprovalDialog
          :is-open="showDirectDialog"
          agent-name="TestAgent"
          checkpoint-id="test-checkpoint"
          session-id="test-session"
          message="需要人工审批以下工具调用"
          :tool-calls="testToolCalls"
          @close="showDirectDialog = false"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import ApprovalDialog from '@/components/chat/ApprovalDialog.vue'
import type { Message } from '@/types/message'

const showDirectDialog = ref(false)

const testInterruptedMessage = ref<Message>({
  id: 'msg-1',
  role: 'assistant',
  content: '我需要执行以下操作，请审批：',
  timestamp: new Date().toLocaleString('zh-CN'),
  status: 'interrupted',
  checkpointId: 'checkpoint-123',
  interruptionData: {
    message: '需要人工审批以下工具调用',
    tool_feedbacks: [
      {
        id: 'tool-1',
        name: 'search_database',
        description: '搜索数据库中的用户信息',
        arguments: {
          query: 'SELECT * FROM users WHERE age > 18',
          limit: 10
        },
        result: 'PENDING'
      },
      {
        id: 'tool-2',
        name: 'send_email',
        description: '发送邮件通知',
        arguments: {
          to: 'user@example.com',
          subject: '通知',
          body: '您的请求已处理'
        },
        result: 'PENDING'
      }
    ]
  }
})

const testNormalMessage = ref<Message>({
  id: 'msg-2',
  role: 'assistant',
  content: '这是一条普通的AI回复消息，没有需要审批的内容。',
  timestamp: new Date().toLocaleString('zh-CN'),
  status: 'completed'
})

const testToolCalls = ref([
  {
    id: 'tool-1',
    name: 'search_database',
    description: '搜索数据库中的用户信息',
    arguments: {
      query: 'SELECT * FROM users WHERE age > 18',
      limit: 10
    }
  },
  {
    id: 'tool-2',
    name: 'send_email',
    description: '发送邮件通知',
    arguments: {
      to: 'user@example.com',
      subject: '通知',
      body: '您的请求已处理'
    }
  }
])
</script>
