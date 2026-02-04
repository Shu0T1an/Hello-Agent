<template>
  <Transition name="fade-slide">
    <button
      v-if="visible"
      class="scroll-to-bottom-button"
      @click="scrollToBottom"
      :title="title"
    >
      <ChevronDown :size="20" />
    </button>
  </Transition>
</template>

<script setup lang="ts">
import { ChevronDown } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  title?: string
}>()

const emit = defineEmits<{
  scroll: []
}>()

function scrollToBottom() {
  emit('scroll')
}
</script>

<style scoped>
.scroll-to-bottom-button {
  position: absolute;
  bottom: 24px;
  right: 24px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #4f46e5;
  transition: all 0.2s ease;
  z-index: 10;
}

.scroll-to-bottom-button:hover {
  background: #f8f9fc;
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.scroll-to-bottom-button:active {
  transform: scale(0.95);
}

[data-theme="dark"] .scroll-to-bottom-button {
  background: #2d2d2d;
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

[data-theme="dark"] .scroll-to-bottom-button:hover {
  background: #3d3d3d;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

/* 淡入滑动动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
