<template>
  <div class="app-root">
    <!-- 切换按钮 (仅在开发环境显示) -->
    <div v-if="showToggle" class="view-toggle-group">
      <button @click="currentView = 'main'" :class="{ active: currentView === 'main' }">应用</button>
      <button @click="currentView = 'timeline'" :class="{ active: currentView === 'timeline' }">时间线测试</button>
      <button @click="currentView = 'approval'" :class="{ active: currentView === 'approval' }">审批测试</button>
      <button @click="currentView = 'api-debug'" :class="{ active: currentView === 'api-debug' }">API 调试</button>
    </div>

    <!-- 主应用 -->
    <MainLayout v-if="currentView === 'main'" />

    <!-- 时间线测试页面 -->
    <AgentTimelineTest v-else-if="currentView === 'timeline'" />

    <!-- 审批UI测试页面 -->
    <ApprovalTestView v-else-if="currentView === 'approval'" />

    <!-- API 调试页面 -->
    <ApiDebugView v-else-if="currentView === 'api-debug'" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import AgentTimelineTest from '@/views/AgentTimelineTest.vue'
import ApprovalTestView from '@/views/ApprovalTestView.vue'
import ApiDebugView from '@/views/ApiDebugView.vue'

const currentView = ref<'main' | 'timeline' | 'approval' | 'api-debug'>('main')
const showToggle = ref(import.meta.env.DEV) // 仅开发环境显示切换按钮
</script>

<style>
/* 全局样式已在 main.ts 中导入 */

.view-toggle-group {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 9999;
  display: flex;
  gap: 8px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  padding: 8px;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.view-toggle-group button {
  padding: 8px 16px;
  background: transparent;
  color: #6b7280;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  font-size: 14px;
  transition: all 0.2s;
}

.view-toggle-group button:hover {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

.view-toggle-group button.active {
  background: #6366f1;
  color: white;
}
</style>
