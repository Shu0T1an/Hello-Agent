<template>
  <button
    @click="toggleTheme"
    :class="[
      'glass rounded-full p-2.5 transition-all duration-300',
      'hover:scale-110 active:scale-95',
      'hover:bg-glass-200',
      'focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2',
      'data-[theme=dark]:focus:ring-offset-zinc-900'
    ]"
    :title="isDark === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
    :data-theme="isDark"
  >
    <transition name="fade" mode="out-in">
      <Sun v-if="isDark === 'dark'" :size="20" class="text-amber-400" />
      <Moon v-else :size="20" class="text-indigo-600" />
    </transition>
  </button>
</template>

<script setup lang="ts">
import { useTheme } from '@/composables/useTheme'
import { Sun, Moon } from 'lucide-vue-next'

const { isDark, toggleTheme, initTheme } = useTheme()

// 组件挂载时初始化主题
initTheme()
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: rotate(-90deg) scale(0.5);
}

.fade-leave-to {
  opacity: 0;
  transform: rotate(90deg) scale(0.5);
}
</style>
