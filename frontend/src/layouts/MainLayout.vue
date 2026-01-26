<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" v-if="currentView === 'chat'">
      <Sidebar />
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 导航标签 -->
      <nav class="nav-tabs" v-if="showNav">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="['nav-tab', { active: currentView === tab.id }]"
          @click="currentView = tab.id"
        >
          <component :is="tab.icon" class="tab-icon" />
          {{ tab.label }}
        </button>
      </nav>

      <!-- 视图内容 -->
      <div class="view-container">
        <AgentManagement v-if="currentView === 'agents'" />
        <ToolMarket v-else-if="currentView === 'tools'" />
        <ModelManagement v-else-if="currentView === 'models'" />
        <McpManagement v-else-if="currentView === 'mcp'" />
        <ChatContainer v-else />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import Sidebar from '@/components/sidebar/Sidebar.vue'
import ChatContainer from '@/components/chat/ChatContainer.vue'
import AgentManagement from '@/views/agent/AgentManagement.vue'
import ToolMarket from '@/views/agent/ToolMarket.vue'
import ModelManagement from '@/views/agent/ModelManagement.vue'
import McpManagement from '@/views/agent/McpManagement.vue'
import {
  MessageSquare,
  Bot,
  Wrench,
  Settings,
  Network
} from 'lucide-vue-next'

type ViewId = 'chat' | 'agents' | 'tools' | 'models' | 'mcp'

interface Tab {
  id: ViewId
  label: string
  icon: any
}

const chatStore = useChatStore()
const agentStore = useAgentStore()

const currentView = ref<ViewId>('chat')

const tabs: Tab[] = [
  { id: 'chat', label: '聊天', icon: MessageSquare },
  { id: 'agents', label: 'Agent 管理', icon: Bot },
  { id: 'tools', label: '工具市场', icon: Wrench },
  { id: 'models', label: '模型配置', icon: Settings },
  { id: 'mcp', label: 'MCP 连接', icon: Network }
]

// 在非聊天视图时显示导航标签
const showNav = computed(() => true)

// 应用启动时加载会话列表
onMounted(async () => {
  // 先加载 Agent 列表
  await agentStore.fetchAgents()
  // 再加载会话列表
  chatStore.loadSessions()
})
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.sidebar {
  width: 280px;
  min-width: 280px;
  height: 100%;
  background-color: var(--color-sidebar-bg);
  border-right: 1px solid #1e293b;
  flex-shrink: 0;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  min-width: 0;
}

.nav-tabs {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #6b7280;
  transition: all 0.2s;
}

.nav-tab:hover {
  background: #e5e7eb;
  color: #374151;
}

.nav-tab.active {
  background: #6366f1;
  color: white;
}

.tab-icon {
  width: 18px;
  height: 18px;
}

.view-container {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
</style>
