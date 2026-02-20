<template>
  <div class="welcome-screen">
    <header class="welcome-header">
      <p class="welcome-kicker">EDITORIAL ASSISTANT</p>
      <h1 class="welcome-title">今天要推进哪项任务？</h1>
      <p class="welcome-subtitle">选择一个常见场景快速开始，或者直接在下方输入你的问题。</p>
    </header>

    <section class="prompt-list">
      <button
        v-for="(suggestion, index) in visibleSuggestions"
        :key="suggestion.id"
        class="prompt-item"
        :style="{ animationDelay: `${index * 70}ms` }"
        @click="handleSelectPrompt(suggestion.prompt)"
      >
        <span class="prompt-icon">
          <component :is="getIconComponent(suggestion.icon)" :size="16" />
        </span>
        <span class="prompt-main">
          <span class="prompt-title">{{ suggestion.title }}</span>
          <span v-if="suggestion.description" class="prompt-desc">{{ suggestion.description }}</span>
        </span>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Code2,
  Lightbulb,
  FileText,
  Bug,
  BarChart3,
  Sparkles
} from 'lucide-vue-next'
import { DEFAULT_SUGGESTIONS, type SuggestionPrompt } from '@/config/suggestions'
import { useBreakpoints } from '@/composables/useBreakpoints'

const emit = defineEmits<{
  'select-prompt': [prompt: string]
}>()

const { isMobile } = useBreakpoints()

const visibleSuggestions = computed<SuggestionPrompt[]>(() => {
  if (isMobile.value) {
    return DEFAULT_SUGGESTIONS.slice(0, 4)
  }
  return DEFAULT_SUGGESTIONS
})

const iconMap: Record<string, any> = {
  Code2,
  Lightbulb,
  FileText,
  Bug,
  BarChart3,
  Sparkles
}

function getIconComponent(iconName?: string) {
  return iconName ? iconMap[iconName] || Sparkles : Sparkles
}

function handleSelectPrompt(prompt: string) {
  emit('select-prompt', prompt)
}
</script>

<style scoped>
.welcome-screen {
  padding: clamp(2rem, 5vw, 3.4rem) 0;
}

.welcome-header {
  margin-bottom: 1.2rem;
}

.welcome-kicker {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  font-weight: 700;
  color: var(--color-primary);
}

.welcome-title {
  margin: 0.5rem 0 0;
  font-size: clamp(1.5rem, 3vw, 2rem);
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.welcome-subtitle {
  margin: 0.62rem 0 0;
  max-width: 640px;
  font-size: 0.95rem;
  color: var(--color-text-secondary);
}

.prompt-list {
  display: grid;
  gap: 0.65rem;
}

.prompt-item {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
  width: 100%;
  text-align: left;
  border: 1px solid var(--line-subtle);
  border-radius: 14px;
  background: color-mix(in srgb, var(--surface-1) 88%, transparent);
  padding: 0.9rem 1rem;
  transition: border-color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
  animation: rise-in 320ms ease both;
}

.prompt-item:hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.prompt-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  flex-shrink: 0;
}

.prompt-main {
  display: flex;
  flex-direction: column;
  gap: 0.22rem;
}

.prompt-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.prompt-desc {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
