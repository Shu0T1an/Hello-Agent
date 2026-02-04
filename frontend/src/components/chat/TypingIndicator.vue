<template>
  <div class="typing-indicator">
    <span class="typing-dot"></span>
    <span class="typing-dot"></span>
    <span class="typing-dot"></span>
  </div>
</template>

<script setup lang="ts">
// 无需 props 或状态，纯动画组件
</script>

<style scoped>
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
}

.typing-dot {
  width: 8px;
  height: 8px;
  background-color: #4f46e5;
  border-radius: 50%;
  animation: typing-bounce 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-dot:nth-child(2) {
  animation-delay: -0.16s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0s;
}

[data-theme="dark"] .typing-dot {
  background-color: #6366f1;
}

@keyframes typing-bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
