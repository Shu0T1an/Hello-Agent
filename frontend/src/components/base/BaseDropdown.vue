<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { cn } from '@/lib/utils'
import { ChevronDown } from 'lucide-vue-next'

interface DropdownItem {
  label: string
  value: string
  icon?: any
  disabled?: boolean
  danger?: boolean
}

interface Props {
  items: DropdownItem[]
  modelValue: string
  placeholder?: string
  disabled?: boolean
  size?: 'sm' | 'md' | 'lg'
  align?: 'left' | 'right'
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  disabled: false,
  size: 'md',
  align: 'left',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string, item: DropdownItem]
}>()

const isOpen = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)

const selectedItem = computed(() => {
  return props.items.find(item => item.value === props.modelValue)
})

const baseClasses = 'inline-flex items-center justify-between gap-2 font-medium rounded-xl transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-0 disabled:opacity-50 disabled:cursor-not-allowed'

const sizeClasses = computed(() => {
  const sizes = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2 text-sm',
    lg: 'px-4 py-3 text-base',
  }
  return sizes[props.size]
})

const dropdownClasses = computed(() => {
  return cn(
    baseClasses,
    sizeClasses.value,
    'bg-white border border-slate-200 hover:border-slate-300 focus:border-indigo-500 focus:ring-indigo-500 cursor-pointer min-w-[160px]',
    props.disabled && 'cursor-not-allowed bg-slate-50'
  )
})

const dropdownPosition = computed(() => {
  return props.align === 'left' ? 'left-0' : 'right-0'
})

const toggleDropdown = () => {
  if (!props.disabled) {
    isOpen.value = !isOpen.value
  }
}

const selectItem = (item: DropdownItem) => {
  if (!item.disabled) {
    emit('update:modelValue', item.value)
    emit('change', item.value, item)
    isOpen.value = false
  }
}

const handleClickOutside = (e: MouseEvent) => {
  if (dropdownRef.value && !dropdownRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div
    ref="dropdownRef"
    class="relative"
  >
    <button
      type="button"
      :disabled="disabled"
      :class="dropdownClasses"
      @click="toggleDropdown"
    >
      <span class="truncate">
        {{ selectedItem?.label || placeholder }}
      </span>
      <ChevronDown
        :class="[
          'w-4 h-4 text-slate-400 flex-shrink-0 transition-transform duration-200',
          isOpen && 'rotate-180'
        ]"
      />
    </button>

    <div
      v-if="isOpen"
      :class="[
        'absolute z-50 mt-1 w-full bg-white border border-slate-200 rounded-xl shadow-soft py-1 max-h-60 overflow-auto custom-scrollbar',
        dropdownPosition
      ]"
    >
      <div
        v-for="item in items"
        :key="item.value"
        :class="[
          'px-4 py-2 cursor-pointer transition-colors flex items-center gap-2',
          item.disabled && 'opacity-50 cursor-not-allowed',
          item.danger ? 'text-red-600 hover:bg-red-50' : 'hover:bg-slate-50',
          !item.disabled && modelValue === item.value && 'bg-indigo-50 text-indigo-600'
        ]"
        @click="selectItem(item)"
      >
        <component
          v-if="item.icon"
          :is="item.icon"
          class="w-4 h-4 flex-shrink-0"
        />
        <span class="truncate">{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>
