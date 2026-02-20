<template>
  <section
    class="h-full flex flex-col glass-panel border-l border-l-glass-border"
    :class="{ 'todo-flash': highlight }"
  >
    <header class="px-4 py-3 border-b border-b-glass-border">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold tracking-wide text-zinc-800">TODO</h3>
        <span class="text-xs text-zinc-500">v{{ todoStore.meta.version }}</span>
      </div>
      <p class="text-xs text-zinc-600 mt-1">未完成 {{ todoStore.activeCount }} / 总计 {{ todoStore.items.length }}</p>
    </header>

    <div class="flex-1 overflow-y-auto custom-scrollbar-glass px-3 py-3">
      <div v-if="todoStore.items.length === 0" class="text-sm text-zinc-500 px-2 py-4">
        当前无待办
      </div>

      <div v-else class="space-y-4">
        <div v-for="group in groups" :key="group.status" v-show="group.items.length > 0">
          <div class="text-xs font-medium text-zinc-500 uppercase tracking-wide px-2 mb-2">
            {{ group.label }} ({{ group.items.length }})
          </div>
          <div class="space-y-2">
            <article
              v-for="item in group.items"
              :key="item.id"
              class="rounded-xl border border-glass-border bg-white/70 dark:bg-zinc-900/50 px-3 py-2"
            >
              <div class="text-sm text-zinc-800 dark:text-zinc-100 leading-5">{{ item.content }}</div>
              <div class="mt-1 text-[11px] text-zinc-500 flex items-center gap-2">
                <span class="font-mono">{{ item.id.slice(0, 8) }}</span>
                <span v-if="item.priority">P: {{ item.priority }}</span>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useTodoStore } from '@/stores/todo'

const todoStore = useTodoStore()
const highlight = ref(false)
let timer: number | undefined

watch(
  () => todoStore.meta.version,
  (next, prev) => {
    if (next > prev) {
      highlight.value = true
      if (timer) {
        window.clearTimeout(timer)
      }
      timer = window.setTimeout(() => {
        highlight.value = false
      }, 1000)
    }
  }
)

onUnmounted(() => {
  if (timer) {
    window.clearTimeout(timer)
  }
})

const groups = computed(() => [
  { status: 'in_progress', label: '进行中', items: todoStore.groupedItems.in_progress },
  { status: 'pending', label: '待处理', items: todoStore.groupedItems.pending },
  { status: 'blocked', label: '阻塞', items: todoStore.groupedItems.blocked },
  { status: 'completed', label: '已完成', items: todoStore.groupedItems.completed }
])
</script>

<style scoped>
.todo-flash {
  animation: todoFlash 1s ease;
}

@keyframes todoFlash {
  0% {
    background-color: rgba(99, 102, 241, 0.15);
  }
  100% {
    background-color: transparent;
  }
}
</style>

