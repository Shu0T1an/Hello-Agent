<script setup lang="ts">
import { ref, computed } from 'vue'
import { ChevronRight, ChevronDown, Braces } from 'lucide-vue-next'

interface Props {
  state: Record<string, unknown>
}

const props = defineProps<Props>()

// 展开状态
const expandedPaths = ref<Set<string>>(new Set())

function isExpanded(path: string): boolean {
  return expandedPaths.value.has(path)
}

function toggleExpand(path: string) {
  if (expandedPaths.value.has(path)) {
    expandedPaths.value.delete(path)
  } else {
    expandedPaths.value.add(path)
  }
}

function getType(value: unknown): string {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  return typeof value
}

function formatValue(value: unknown): string {
  const type = getType(value)
  if (type === 'null') return 'null'
  if (type === 'undefined') return 'undefined'
  if (type === 'string') return `"${value}"`
  if (type === 'array' || type === 'object') return ''
  return String(value)
}

function isExpandable(value: unknown): boolean {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

const stateKeys = computed(() => {
  return Object.keys(props.state).sort()
})

const stateEntries = computed(() => {
  return stateKeys.value.map(key => ({
    key,
    value: props.state[key],
    type: getType(props.state[key]),
  }))
})
</script>

<template>
  <div class="checkpoint-state-data space-y-2">
    <div v-if="stateKeys.length === 0" class="text-center py-8 text-gray-500 dark:text-gray-400">
      暂无状态数据
    </div>

    <div v-else class="space-y-1">
      <div
        v-for="{ key, value, type } in stateEntries"
        :key="key"
        class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden"
      >
        <!-- 键名行 -->
        <div
          class="flex items-center gap-2 px-3 py-2 bg-gray-50 dark:bg-gray-900 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          @click="toggleExpand(key)"
        >
          <component
            :is="isExpandable(value) ? (isExpanded(key) ? ChevronDown : ChevronRight) : Braces"
            class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0"
          />

          <span class="font-medium text-gray-900 dark:text-gray-100 font-mono text-sm">
            {{ key }}
          </span>

          <span class="text-xs text-gray-500 dark:text-gray-400 ml-auto">
            {{ type }}
          </span>
        </div>

        <!-- 值内容 -->
        <div v-if="!isExpandable(value) || isExpanded(key)" class="px-3 py-2 bg-white dark:bg-gray-800">
          <!-- 简单值 -->
          <div v-if="!isExpandable(value)" class="font-mono text-sm">
            <span v-if="type === 'string'" class="text-green-600 dark:text-green-400">
              "{{ value }}"
            </span>
            <span v-else-if="type === 'boolean'" class="text-blue-600 dark:text-blue-400">
              {{ value }}
            </span>
            <span v-else-if="type === 'number'" class="text-purple-600 dark:text-purple-400">
              {{ value }}
            </span>
            <span v-else class="text-gray-600 dark:text-gray-400">
              {{ value }}
            </span>
          </div>

          <!-- 对象值（递归） -->
          <div v-else class="pl-4 space-y-1">
            <template v-if="typeof value === 'object' && value !== null">
              <div
                v-for="(subValue, subKey) in value"
                :key="subKey"
                class="flex items-start gap-2 py-1"
              >
                <span class="font-mono text-sm text-gray-700 dark:text-gray-300 flex-shrink-0">
                  {{ subKey }}:
                </span>
                <span class="font-mono text-sm text-gray-600 dark:text-gray-400 break-all">
                  <template v-if="typeof subValue === 'string'">
                    "{{ subValue }}"
                  </template>
                  <template v-else-if="typeof subValue === 'object'">
                    {{ JSON.stringify(subValue, null, 2) }}
                  </template>
                  <template v-else>
                    {{ String(subValue) }}
                  </template>
                </span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
