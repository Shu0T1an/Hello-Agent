<template>
  <div class="main-shell" data-theme-container>
    <aside
      v-if="currentView === 'chat'"
      :class="[
        'h-full transition-all duration-300 relative z-50 flex-shrink-0 shell-sidebar',
        sidebarClasses
      ]"
    >
      <Sidebar :collapsed="isCollapsed" @toggle="isCollapsed = !isCollapsed" />
    </aside>

    <Transition name="fade">
      <div
        v-if="isMobileMenuOpen"
        class="fixed inset-0 bg-slate-900/35 backdrop-blur-sm z-40 lg:hidden"
        @click="closeMobileMenu"
      />
    </Transition>

    <main class="shell-main">
      <div class="mobile-header">
        <button
          @click="toggleMobileMenu"
          class="mobile-header-btn"
          aria-label="打开菜单"
        >
          <Menu :size="20" />
        </button>
        <h1 class="mobile-header-title">{{ getCurrentViewLabel() }}</h1>
        <ThemeToggle />
      </div>

      <nav class="editorial-nav">
        <div class="editorial-nav-left">
          <p class="editorial-kicker">HELLO AGENT</p>
          <h2 class="editorial-title">Workspace</h2>
        </div>

        <div class="editorial-tabs custom-scrollbar">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            :class="['editorial-tab', { active: currentView === tab.id }]"
            @click="switchView(tab.id)"
          >
            <component :is="tab.icon" :size="16" />
            <span>{{ tab.label }}</span>
          </button>
        </div>

        <div class="editorial-nav-right hidden lg:flex">
          <ThemeToggle />
        </div>
      </nav>

      <div class="content-scroll custom-scrollbar">
        <div v-if="currentView === 'chat'" class="h-full min-h-0">
          <ChatContainer
            :knowledge-base-id="selectedKnowledgeBase"
            :knowledge-bases="knowledgeBases"
          />
        </div>

        <div v-else class="content-pane">
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

const selectedKnowledgeBase = ref('')
const knowledgeBases = ref<Array<{ kbId: string; kbName: string }>>([])

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

const sidebarClasses = computed(() => {
  if (isMobile.value) {
    return isMobileMenuOpen.value
      ? 'w-[280px] fixed left-0 top-0'
      : 'fixed -translate-x-full'
  }

  if (isTablet.value) {
    return isCollapsed.value ? 'w-16' : 'w-[280px]'
  }

  return isCollapsed.value ? 'w-16' : 'w-[280px]'
})

const getCurrentViewLabel = () => tabs.find(t => t.id === currentView.value)?.label || ''

const switchView = (viewId: ViewId) => {
  currentView.value = viewId
  if (isMobile.value) {
    isMobileMenuOpen.value = false
  }
}

const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value
}

const closeMobileMenu = () => {
  isMobileMenuOpen.value = false
}

const handleResize = () => {
  if (isTablet.value && !isMobile.value) {
    isCollapsed.value = true
  } else if (isDesktop.value) {
    isCollapsed.value = false
  }
}

onMounted(async () => {
  await agentStore.fetchAgents()
  chatStore.loadSessions()
  await loadKnowledgeBases()
  handleResize()
})
</script>

<style scoped>
.main-shell {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
  background: var(--bg-primary);
}

.shell-sidebar {
  border-right: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 92%, transparent);
}

.shell-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.mobile-header {
  display: none;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--line-subtle);
  background: var(--surface-1);
  padding: 0.75rem 1rem;
}

.mobile-header-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-subtle);
  border-radius: 10px;
  width: 36px;
  height: 36px;
  color: var(--color-text-secondary);
  background: var(--surface-1);
}

.mobile-header-title {
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.editorial-nav {
  display: grid;
  grid-template-columns: 220px 1fr auto;
  align-items: center;
  gap: 1rem;
  padding: 0.9rem 1.25rem;
  border-bottom: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 86%, transparent);
}

.editorial-kicker {
  margin: 0;
  font-size: 0.68rem;
  letter-spacing: 0.14em;
  font-weight: 700;
  color: var(--color-primary);
}

.editorial-title {
  margin: 0.2rem 0 0;
  font-size: 1.12rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.editorial-tabs {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.1rem;
}

.editorial-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  padding: 0.46rem 0.72rem;
  border-radius: 999px;
  border: 1px solid transparent;
  color: var(--color-text-secondary);
  background: transparent;
  font-size: 0.82rem;
  font-weight: 600;
  white-space: nowrap;
  transition: all var(--transition-fast);
}

.editorial-tab:hover {
  border-color: var(--line-subtle);
  color: var(--color-text-primary);
  background: color-mix(in srgb, var(--surface-1) 65%, transparent);
}

.editorial-tab.active {
  color: var(--text-inverse);
  background: var(--gradient-primary);
  box-shadow: 0 10px 20px -14px rgba(29, 78, 216, 0.65);
}

.content-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.content-pane {
  padding: 1.25rem;
}

@media (max-width: 1024px) {
  .mobile-header {
    display: flex;
  }

  .editorial-nav {
    grid-template-columns: 1fr;
    gap: 0.8rem;
  }

  .editorial-nav-left {
    display: none;
  }

  .editorial-nav-right {
    display: none;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
