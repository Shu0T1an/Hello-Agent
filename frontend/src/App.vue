<template>
  <div class="app-root">
    <!-- 切换按钮 (仅在开发环境显示) -->
    <button
      v-if="showToggle"
      @click="toggleView"
      class="view-toggle-btn"
      title="切换到测试页面"
    >
      {{ showTest ? '应用' : '测试' }}
    </button>

    <!-- 主应用 -->
    <MainLayout v-if="!showTest" />

    <!-- 测试页面 -->
    <AgentTimelineTest v-else />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import AgentTimelineTest from '@/views/AgentTimelineTest.vue'

const showTest = ref(false)
const showToggle = ref(import.meta.env.DEV) // 仅开发环境显示切换按钮

function toggleView() {
  showTest.value = !showTest.value
}
</script>

<style>
/* 全局样式已在 main.ts 中导入 */

.view-toggle-btn {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 9999;
  padding: 10px 16px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
  transition: all 0.2s;
}

.view-toggle-btn:hover {
  background: #4f46e5;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.5);
}
</style>
