<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <Sidebar />
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <ChatContainer />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import Sidebar from '@/components/sidebar/Sidebar.vue'
import ChatContainer from '@/components/chat/ChatContainer.vue'

const chatStore = useChatStore()
const agentStore = useAgentStore()

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
</style>
