<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { codeToHtml } from 'shiki'
import { Copy, Check } from 'lucide-vue-next'

type CodeTheme = 'github-light' | 'github-dark' | 'nord' | 'monokai'
type CodeSize = 'xs' | 'sm' | 'md' | 'lg'

const props = withDefaults(defineProps<{
  code: string
  language?: string
  theme?: CodeTheme
  size?: CodeSize
  showHeader?: boolean
  showCopyButton?: boolean
}>(), {
  language: 'json',
  theme: 'github-dark',
  size: 'sm',
  showHeader: true,
  showCopyButton: true
})

const highlightedCode = ref('')
const copied = ref(false)
const copyTitle = ref('复制代码')
const loading = ref(true)

// 尺寸对应的字体大小
const sizeClasses = computed(() => {
  const sizes: Record<CodeSize, string> = {
    xs: 'text-[10px]',
    sm: 'text-xs',
    md: 'text-sm',
    lg: 'text-base'
  }
  return sizes[props.size]
})

async function highlight() {
  loading.value = true
  try {
    highlightedCode.value = await codeToHtml(props.code, {
      lang: props.language,
      theme: props.theme
    })
  } catch (error) {
    console.warn(`Language ${props.language} not supported, falling back to plain text`, error)
    // 降级到纯文本
    highlightedCode.value = `<pre class="shiki ${props.theme}"><code>${escapeHtml(props.code)}</code></pre>`
  } finally {
    loading.value = false
  }
}

// HTML 转义，用于降级处理
function escapeHtml(text: string): string {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(props.code)
    copied.value = true
    copyTitle.value = '已复制'
    setTimeout(() => {
      copied.value = false
      copyTitle.value = '复制代码'
    }, 2000)
  } catch (error) {
    console.error('复制失败:', error)
    copyTitle.value = '复制失败'
  }
}

onMounted(highlight)
watch(() => [props.code, props.language, props.theme], highlight)
</script>

<template>
  <div class="code-highlight">
    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center py-8">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500"></div>
    </div>

    <!-- 代码内容 -->
    <template v-else>
      <!-- 头部：语言和复制按钮 -->
      <div v-if="showHeader" class="code-header flex items-center justify-between px-4 py-2 bg-gray-800/50 rounded-t-lg">
        <span class="lang-badge text-xs font-medium text-gray-400 uppercase">{{ language }}</span>
        <button
          v-if="showCopyButton"
          @click="copyCode"
          class="copy-btn flex items-center gap-1.5 px-2 py-1 text-xs text-gray-400 hover:text-gray-200 hover:bg-gray-700/50 rounded transition-colors"
          :title="copyTitle"
        >
          <Check v-if="copied" :size="14" />
          <Copy v-else :size="14" />
          <span>{{ copied ? '已复制' : '复制' }}</span>
        </button>
      </div>

      <!-- 代码内容 -->
      <div
        v-html="highlightedCode"
        class="shiki-code overflow-x-auto"
        :class="[
          sizeClasses,
          showHeader ? 'rounded-b-lg' : 'rounded-lg'
        ]"
      ></div>
    </template>
  </div>
</template>

<style scoped>
.code-highlight {
  position: relative;
}

.code-header {
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.shiki-code {
  background-color: #1e1e1e;
  padding: 1rem;
}

.theme-github-light .shiki-code {
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
}

.theme-github-dark .shiki-code {
  background-color: #1e1e1e;
}

.theme-nord .shiki-code {
  background-color: #2e3440;
}

.theme-monokai .shiki-code {
  background-color: #272822;
}

/* Shiki 样式覆盖 */
.shiki-code :deep(pre) {
  margin: 0;
  padding: 0;
  background: transparent !important;
}

.shiki-code :deep(code) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace;
  font-size: inherit;
}
</style>
