<template>
  <div class="flex h-screen w-full overflow-hidden" style="background-color: #f5f5f5;" data-theme-container>
    <!-- 侧边栏 -->
    <aside
      v-if="currentView === 'chat'"
      :class="[
        'h-full transition-all duration-300 relative z-50 flex-shrink-0',
        'glass-panel border-r border-r-glass-border',
        sidebarClasses
      ]"
    >
      <Sidebar :collapsed="isCollapsed" @toggle="isCollapsed = !isCollapsed" />
    </aside>

    <!-- 移动端遮罩 -->
    <Transition name="fade">
      <div
        v-if="isMobileMenuOpen"
        class="fixed inset-0 bg-black/30 backdrop-blur-sm z-40 lg:hidden"
        @click="closeMobileMenu"
      />
    </Transition>

    <!-- 主内容区 -->
    <main class="flex-1 flex flex-col h-full overflow-hidden min-w-0">
      <!-- 移动端顶部导航 -->
      <div class="lg:hidden flex items-center justify-between p-4 glass-panel border-b border-b-glass-border flex-shrink-0 relative z-[60]">
        <button
          @click="toggleMobileMenu"
          class="p-2 hover:bg-glass-200 rounded-lg transition-colors"
        >
          <Menu :size="24" />
        </button>
        <h1 class="text-lg font-semibold">{{ getCurrentViewLabel() }}</h1>
        <!-- 主题切换按钮 -->
        <ThemeToggle />
      </div>

      <!-- 导航标签 -->
      <nav class="flex items-center gap-1 sm:gap-2 p-3 sm:p-4 glass-panel border-b border-b-glass-border flex-shrink-0 overflow-x-auto relative z-[60]">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="[
            'flex items-center gap-2 px-3 py-2 sm:px-4 rounded-xl text-sm font-medium transition-all duration-200 whitespace-nowrap',
            currentView === tab.id
              ? 'nav-tab-glass active'
              : 'nav-tab-glass'
          ]"
          @click="switchView(tab.id)"
        >
          <component :is="tab.icon" :size="18" />
          <span class="hidden sm:inline">{{ tab.label }}</span>
        </button>
        <div class="ml-auto flex items-center gap-2">
          <!-- 桌面端主题切换按钮 -->
          <ThemeToggle class="hidden lg:block" />
        </div>
      </nav>

      <!-- 视图内容 -->
      <div class="flex-1 overflow-y-auto min-h-0 custom-scrollbar-glass">
        <!-- 聊天视图需要全高度 -->
        <div v-if="currentView === 'chat'" class="h-full min-h-0">
          <ChatContainer
            :knowledge-base-id="selectedKnowledgeBase"
            :knowledge-bases="knowledgeBases"
          />
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
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
import ThemeToggle from '@/components/common/ThemeToggle.vue'
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
  // 设置深色模式背景
  updateThemeBackground()
})

// 更新主题背景
const updateThemeBackground = () => {
  const container = document.querySelector('[data-theme-container]') as HTMLElement
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  if (container) {
    container.style.backgroundColor = isDark ? '#1a1a1a' : '#f5f5f5'
  }
}

// 监听主题变化
const observer = new MutationObserver(() => {
  updateThemeBackground()
})

onMounted(() => {
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })
})

onUnmounted(() => {
  observer.disconnect()
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
