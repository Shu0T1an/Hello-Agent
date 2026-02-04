<script setup lang="ts">
import { computed } from 'vue'
import {
  FileQuestion,
  SearchX,
  Inbox,
  AlertCircle,
  type Component,
} from 'lucide-vue-next'
import { cn } from '@/lib/utils'

interface Props {
  type?: 'default' | 'search' | 'inbox' | 'error' | 'custom'
  icon?: Component | null
  title?: string
  description?: string
  actionText?: string
  showAction?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default',
  icon: null,
  title: '',
  description: '',
  actionText: '',
  showAction: true,
})

const emit = defineEmits<{
  action: []
}>()

const defaultIconComponent = computed(() => {
  const icons = {
    default: FileQuestion,
    search: SearchX,
    inbox: Inbox,
    error: AlertCircle,
    custom: null,
  }
  return props.icon || icons[props.type]
})

const defaultTitles = {
  default: '暂无数据',
  search: '未找到相关内容',
  inbox: '暂无消息',
  error: '出错了',
  custom: '',
}

const defaultDescriptions = {
  default: '当前没有可显示的数据',
  search: '请尝试其他搜索关键词',
  inbox: '您的消息列表为空',
  error: '页面遇到一些问题，请稍后再试',
  custom: '',
}

const displayTitle = computed(() => props.title || defaultTitles[props.type])
const displayDescription = computed(() => props.description || defaultDescriptions[props.type])

const handleAction = () => {
  emit('action')
}
</script>

<template>
  <div
    :class="cn(
      'flex flex-col items-center justify-center py-16 px-4 text-center',
      props.class
    )"
  >
    <!-- 插画区域 -->
    <div class="relative mb-6">
      <!-- 背景装饰 -->
      <div class="absolute inset-0 bg-gradient-radial from-indigo-100 to-transparent opacity-50 rounded-full blur-2xl" />
      <div class="relative">
        <component
          :is="defaultIconComponent"
          v-if="defaultIconComponent"
          class="text-slate-300"
          :size="80"
          stroke-width="1.5"
        />
        <slot v-else name="icon">
          <FileQuestion
            class="text-zinc-300"
            :size="80"
            stroke-width="1.5"
          />
        </slot>
      </div>
    </div>

    <!-- 标题 -->
    <h3 class="text-lg font-semibold text-zinc-900 mb-2">
      <slot v-if="$slots.title" name="title" />
      <template v-else>{{ displayTitle }}</template>
    </h3>

    <!-- 描述 -->
    <p class="text-sm text-zinc-500 mb-6 max-w-sm">
      <slot v-if="$slots.description" name="description" />
      <template v-else>{{ displayDescription }}</template>
    </p>

    <!-- 操作按钮 -->
    <slot name="action">
      <button
        v-if="showAction && ($slots['action-button'] || actionText)"
        class="inline-flex items-center gap-2 px-4 py-2 bg-indigo-500 text-white text-sm font-medium rounded-xl hover:bg-indigo-600 transition-colors btn-hover-lift"
        @click="handleAction"
      >
        <slot name="action-button">
          {{ actionText || '了解更多' }}
        </slot>
      </button>
    </slot>
  </div>
</template>
