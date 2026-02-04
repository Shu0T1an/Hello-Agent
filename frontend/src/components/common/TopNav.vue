<template>
  <div class="top-nav">
    <ArrowLeft class="back-btn" :size="20" />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'
</script>

<style scoped>
.top-nav {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

[data-theme="dark"] .top-nav {
  background: rgba(30, 41, 59, 0.6);
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.back-btn {
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color 150ms ease;
}

.back-btn:hover {
  color: var(--color-primary);
}
</style>
