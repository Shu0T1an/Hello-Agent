<template>
  <div class="message-skeleton">
    <div class="skeleton-avatar"></div>
    <div class="skeleton-content">
      <div class="skeleton-line skeleton-line-short"></div>
      <div class="skeleton-line"></div>
      <div class="skeleton-line skeleton-line-medium"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 无需 props 或状态，纯动画组件
</script>

<style scoped>
.message-skeleton {
  display: flex;
  gap: 12px;
  max-width: 80%;
  align-self: flex-start;
  animation: skeleton-fade-in 0.3s ease-out;
}

@keyframes skeleton-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.skeleton-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(
    90deg,
    #f0f0f0 0%,
    #e0e0e0 50%,
    #f0f0f0 100%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  flex-shrink: 0;
}

[data-theme="dark"] .skeleton-avatar {
  background: linear-gradient(
    90deg,
    #3a3a3a 0%,
    #4a4a4a 50%,
    #3a3a3a 100%
  );
  background-size: 200% 100%;
}

.skeleton-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.skeleton-line {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    #f0f0f0 0%,
    #e0e0e0 50%,
    #f0f0f0 100%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}

[data-theme="dark"] .skeleton-line {
  background: linear-gradient(
    90deg,
    #2d2d2d 0%,
    #3d3d3d 50%,
    #2d2d2d 100%
  );
  background-size: 200% 100%;
}

.skeleton-line-short {
  width: 40%;
}

.skeleton-line-medium {
  width: 70%;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}
</style>
