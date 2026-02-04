<template>
  <div class="welcome-screen">
    <!-- Logo 区域 -->
    <div class="welcome-logo">
      <div class="logo-container">
        <Bot :size="48" class="logo-icon" />
      </div>
    </div>

    <!-- 大标题 -->
    <h1 class="welcome-title">需要为你做点什么？</h1>

    <!-- 快捷提示词网格 -->
    <div class="suggestions-grid">
      <BaseCard
        v-for="(suggestion, index) in visibleSuggestions"
        :key="suggestion.id"
        variant="glass"
        hoverable
        clickable
        class="suggestion-card"
        :style="{ animationDelay: `${index * 60}ms` }"
        @click="handleSelectPrompt(suggestion.prompt)"
      >
        <div class="suggestion-content">
          <div class="suggestion-icon">
            <component :is="getIconComponent(suggestion.icon)" :size="24" />
          </div>
          <div class="suggestion-text">
            <div class="suggestion-title">{{ suggestion.title }}</div>
            <div v-if="suggestion.description" class="suggestion-description">
              {{ suggestion.description }}
            </div>
          </div>
        </div>
      </BaseCard>
    </div>

    <!-- 提示信息 -->
    <p class="welcome-hint">
      <Sparkles :size="16" class="hint-icon" />
      您也可以直接输入问题开始对话
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Bot,
  Code2,
  Lightbulb,
  FileText,
  Bug,
  BarChart3,
  Sparkles
} from 'lucide-vue-next'
import BaseCard from '@/components/base/BaseCard.vue'
import { DEFAULT_SUGGESTIONS, type SuggestionPrompt } from '@/config/suggestions'
import { useBreakpoints } from '@/composables/useBreakpoints'

// Emits
const emit = defineEmits<{
  'select-prompt': [prompt: string]
}>()

const { isMobile, isTablet } = useBreakpoints()

// 根据屏幕尺寸显示不同数量的卡片
const visibleSuggestions = computed<SuggestionPrompt[]>(() => {
  if (isMobile.value) {
    // 移动端显示 4 个
    return DEFAULT_SUGGESTIONS.slice(0, 4)
  } else if (isTablet.value) {
    // 平板端显示 6 个
    return DEFAULT_SUGGESTIONS
  } else {
    // 桌面端显示全部 6 个
    return DEFAULT_SUGGESTIONS
  }
})

// 图标映射
const iconMap: Record<string, any> = {
  Code2,
  Lightbulb,
  FileText,
  Bug,
  BarChart3,
  Sparkles
}

// 获取图标组件
function getIconComponent(iconName?: string) {
  return iconName ? iconMap[iconName] || Sparkles : Sparkles
}

// 处理提示词选择
function handleSelectPrompt(prompt: string) {
  emit('select-prompt', prompt)
}
</script>

<style scoped>
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  min-height: 400px;
  text-align: center;
}

/* Logo 区域 */
.welcome-logo {
  margin-bottom: 24px;
}

.logo-container {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(168, 85, 247, 0.1) 100%);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.logo-icon {
  color: #6366f1;
  stroke-width: 1.5;
}

[data-theme="dark"] .logo-icon {
  color: #818cf8;
}

/* 标题 */
.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 32px;
  letter-spacing: -0.02em;
}

/* 网格布局 */
.suggestions-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  width: 100%;
  max-width: 600px;
  margin-bottom: 24px;
}

/* 平板端 2 列 */
@media (min-width: 640px) {
  .suggestions-grid {
    grid-template-columns: repeat(2, 1fr);
    max-width: 700px;
  }
}

/* 桌面端 3 列 */
@media (min-width: 1024px) {
  .suggestions-grid {
    grid-template-columns: repeat(3, 1fr);
    max-width: 850px;
  }
}

/* 提示词卡片 */
.suggestion-card {
  animation: fadeInUp 0.4s ease-out forwards;
  opacity: 0;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.suggestion-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-align: left;
}

.suggestion-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(168, 85, 247, 0.05) 100%);
  color: #6366f1;
}

[data-theme="dark"] .suggestion-icon {
  background: linear-gradient(135deg, rgba(129, 140, 248, 0.15) 0%, rgba(168, 85, 247, 0.1) 100%);
  color: #818cf8;
}

.suggestion-text {
  flex: 1;
  min-width: 0;
}

.suggestion-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.suggestion-description {
  font-size: 13px;
  color: #71717a;
  line-height: 1.4;
}

[data-theme="dark"] .suggestion-description {
  color: rgba(255, 255, 255, 0.5);
}

/* 提示信息 */
.welcome-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #71717a;
  margin-top: 8px;
}

[data-theme="dark"] .welcome-hint {
  color: rgba(255, 255, 255, 0.5);
}

.hint-icon {
  flex-shrink: 0;
  color: #fbbf24;
}
</style>
