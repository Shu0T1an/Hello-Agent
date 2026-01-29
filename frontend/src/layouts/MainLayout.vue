<template>
  <div class="flex h-screen w-full overflow-hidden">
    <!-- 侧边栏 -->
    <aside
      v-if="currentView === 'chat'"
      :class="[
        'h-full bg-slate-900 border-r border-slate-800 transition-all duration-300 relative z-50 flex-shrink-0',
        sidebarClasses
      ]"
    >
      <Sidebar :collapsed="isCollapsed" />
    </aside>

    <!-- 移动端遮罩 -->
    <Transition name="fade">
      <div
        v-if="isMobileMenuOpen"
        class="fixed inset-0 bg-black/50 z-40 lg:hidden"
        @click="closeMobileMenu"
      />
    </Transition>

    <!-- 主内容区 -->
    <main class="flex-1 flex flex-col h-full overflow-hidden min-w-0">
      <!-- 移动端顶部导航 -->
      <div class="lg:hidden flex items-center justify-between p-4 bg-white border-b border-slate-200 flex-shrink-0">
        <button
          @click="toggleMobileMenu"
          class="p-2 text-slate-600 hover:bg-slate-100 rounded-lg"
        >
          <Menu :size="24" />
        </button>
        <h1 class="text-lg font-semibold text-slate-900">{{ getCurrentViewLabel() }}</h1>
        <div class="w-10" />
      </div>

      <!-- 导航标签 -->
      <nav class="flex gap-1 sm:gap-2 p-3 sm:p-4 bg-slate-50 border-b border-slate-200 flex-shrink-0 overflow-x-auto">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="[
            'flex items-center gap-2 px-3 py-2 sm:px-4 rounded-lg text-sm font-medium transition-all duration-200 whitespace-nowrap',
            currentView === tab.id
              ? 'bg-indigo-500 text-white shadow-sm'
              : 'text-slate-600 hover:bg-white hover:shadow-sm'
          ]"
          @click="switchView(tab.id)"
        >
          <component :is="tab.icon" :size="18" />
          <span class="hidden sm:inline">{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 视图内容 -->
      <div class="flex-1 overflow-y-auto min-h-0">
        <!-- 知识库选择器（仅聊天视图显示） -->
        <div v-if="currentView === 'chat'" class="flex items-center gap-3 p-4 bg-white border-b border-slate-200 flex-shrink-0">
          <label class="text-sm font-medium text-slate-700">知识库：</label>
          <select
            v-model="selectedKnowledgeBase"
            class="flex-1 max-w-xs px-3 py-2 border border-slate-200 rounded-lg bg-white text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          >
            <option value="">不使用知识库</option>
            <option v-for="kb in knowledgeBases" :key="kb.kbId" :value="kb.kbId">
              {{ kb.kbName }}
            </option>
          </select>
        </div>

        <!-- 聊天视图需要全高度 -->
        <div v-if="currentView === 'chat'" class="h-full min-h-0">
          <ChatContainer :knowledge-base-id="selectedKnowledgeBase" />
        </div>

        <!-- 其他视图使用内边距 -->
        <div v-else class="p-4 sm:p-6">
          <AgentManagement v-if="currentView === 'agents'" />
          <ToolMarket v-else-if="currentView === 'tools'" />
          <ModelManagement v-else-if="currentView === 'models'" />
          <McpManagement v-else-if="currentView === 'mcp'" />
          <RagQueryView v-else-if="currentView === 'rag'" />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import { useBreakpoints } from '@/composables/useBreakpoints'
import Sidebar from '@/components/sidebar/Sidebar.vue'
import ChatContainer from '@/components/chat/ChatContainer.vue'
import AgentManagement from '@/views/agent/AgentManagement.vue'
import ToolMarket from '@/views/agent/ToolMarket.vue'
import ModelManagement from '@/views/agent/ModelManagement.vue'
import McpManagement from '@/views/agent/McpManagement.vue'
import RagQueryView from '@/views/RagQueryView.vue'
import {
  MessageSquare,
  Bot,
  Wrench,
  Settings,
  Network,
  FileText,
  Menu,
} from 'lucide-vue-next'

type ViewId = 'chat' | 'agents' | 'tools' | 'models' | 'mcp' | 'rag'

interface Tab {
  id: ViewId
  label: string
  icon: any
}

const chatStore = useChatStore()
const agentStore = useAgentStore()
const { isMobile, isTablet, isDesktop } = useBreakpoints()

const currentView = ref<ViewId>('chat')
const isCollapsed = ref(false)
const isMobileMenuOpen = ref(false)

// 知识库选择器状态
const selectedKnowledgeBase = ref('')
const knowledgeBases = ref<Array<{ kbId: string; kbName: string }>>([])

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    const response = await fetch('/api/rag/knowledge-bases')
    if (response.ok) {
      const data = await response.json()
      knowledgeBases.value = data
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
  }
}

const tabs: Tab[] = [
  { id: 'chat', label: '聊天', icon: MessageSquare },
  { id: 'agents', label: 'Agent 管理', icon: Bot },
  { id: 'tools', label: '工具市场', icon: Wrench },
  { id: 'models', label: '模型配置', icon: Settings },
  { id: 'mcp', label: 'MCP 连接', icon: Network },
  { id: 'rag', label: '知识库', icon: FileText }
]

// 侧边栏响应式类
const sidebarClasses = computed(() => {
  if (isMobile.value) {
    // 移动端：抽屉式
    return isMobileMenuOpen.value
      ? 'w-[280px] fixed left-0 top-0'
      : 'fixed -translate-x-full'
  } else if (isTablet.value) {
    // 平板端：可折叠
    return isCollapsed.value ? 'w-16' : 'w-[280px]'
  } else {
    // 桌面端：完整宽度
    return isCollapsed.value ? 'w-16' : 'w-[280px]'
  }
})

// 获取当前视图标签
const getCurrentViewLabel = () => {
  return tabs.find(t => t.id === currentView.value)?.label || ''
}

// 切换视图
const switchView = (viewId: ViewId) => {
  currentView.value = viewId
  if (isMobile.value) {
    isMobileMenuOpen.value = false
  }
}

// 切换移动端菜单
const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value
}

// 关闭移动端菜单
const closeMobileMenu = () => {
  isMobileMenuOpen.value = false
}

// 根据屏幕大小自动处理侧边栏折叠
const handleResize = () => {
  if (isTablet.value && !isMobile.value) {
    // 平板端默认折叠
    isCollapsed.value = true
  } else if (isDesktop.value) {
    // 桌面端默认展开
    isCollapsed.value = false
  }
}

// 应用启动时加载会话列表
onMounted(async () => {
  // 先加载 Agent 列表
  await agentStore.fetchAgents()
  // 再加载会话列表
  chatStore.loadSessions()
  // 加载知识库列表
  await loadKnowledgeBases()
  // 初始处理侧边栏状态
  handleResize()
})
</script>

<style scoped>
/* 淡入淡出动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 导航标签滚动条隐藏 */
nav {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

nav::-webkit-scrollbar {
  display: none;
}
</style>
