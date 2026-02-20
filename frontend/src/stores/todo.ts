import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { TodoItem, TodoMeta, TodoPriority, TodoStatus } from '@/types/todo'

const STATUS_ORDER: Record<TodoStatus, number> = {
  in_progress: 0,
  pending: 1,
  blocked: 2,
  completed: 3
}

function toIsoOrUndefined(value: unknown): string | undefined {
  if (typeof value !== 'string' || value.trim().length === 0) {
    return undefined
  }
  return value
}

function toPriority(value: unknown): TodoPriority | undefined {
  if (value === 'low' || value === 'medium' || value === 'high') {
    return value
  }
  return undefined
}

function toStatus(value: unknown): TodoStatus | undefined {
  if (value === 'pending' || value === 'in_progress' || value === 'completed' || value === 'blocked') {
    return value
  }
  return undefined
}

function parseTodoItems(raw: unknown): TodoItem[] {
  if (!Array.isArray(raw)) {
    return []
  }

  const result: TodoItem[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue
    }
    const row = item as Record<string, unknown>
    const id = typeof row.id === 'string' ? row.id.trim() : ''
    const content = typeof row.content === 'string' ? row.content.trim() : ''
    const status = toStatus(row.status)
    if (!id || !content || !status) {
      continue
    }
    result.push({
      id,
      content,
      status,
      priority: toPriority(row.priority),
      createdAt: toIsoOrUndefined(row.createdAt),
      updatedAt: toIsoOrUndefined(row.updatedAt)
    })
  }
  return result
}

function parseTodoMeta(raw: unknown): TodoMeta | null {
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const map = raw as Record<string, unknown>
  const versionValue = map.version
  let version = 0
  if (typeof versionValue === 'number' && Number.isFinite(versionValue)) {
    version = Math.max(0, Math.floor(versionValue))
  } else if (typeof versionValue === 'string') {
    const parsed = Number(versionValue)
    if (Number.isFinite(parsed)) {
      version = Math.max(0, Math.floor(parsed))
    }
  }
  return {
    version,
    updatedAt: toIsoOrUndefined(map.updatedAt),
    updatedByToolCallId: typeof map.updatedByToolCallId === 'string' ? map.updatedByToolCallId : undefined,
    lastOperation: typeof map.lastOperation === 'string' ? map.lastOperation : undefined
  }
}

function sortTodos(items: TodoItem[]): TodoItem[] {
  return [...items].sort((a, b) => {
    const statusDiff = STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
    if (statusDiff !== 0) {
      return statusDiff
    }
    const aTime = a.updatedAt ? Date.parse(a.updatedAt) : 0
    const bTime = b.updatedAt ? Date.parse(b.updatedAt) : 0
    if (Number.isFinite(aTime) && Number.isFinite(bTime) && aTime !== bTime) {
      return bTime - aTime
    }
    return a.content.localeCompare(b.content)
  })
}

export const useTodoStore = defineStore('todo', () => {
  const items = ref<TodoItem[]>([])
  const meta = ref<TodoMeta>({ version: 0 })
  const lastSyncAt = ref<string | null>(null)
  const syncSource = ref<string>('')
  const toolSeenInSession = ref(false)

  const sortedItems = computed(() => sortTodos(items.value))
  const hasTodoPanel = computed(() => toolSeenInSession.value)
  const groupedItems = computed(() => {
    const groups: Record<TodoStatus, TodoItem[]> = {
      in_progress: [],
      pending: [],
      blocked: [],
      completed: []
    }
    for (const item of sortedItems.value) {
      groups[item.status].push(item)
    }
    return groups
  })
  const activeCount = computed(() =>
    items.value.filter((item) => item.status !== 'completed').length
  )

  function syncFromStateData(stateData?: Record<string, unknown>, source = 'sse'): boolean {
    if (!stateData || typeof stateData !== 'object') {
      return false
    }
    const hasTodos = Object.prototype.hasOwnProperty.call(stateData, 'todos')
    const hasMeta = Object.prototype.hasOwnProperty.call(stateData, 'todos_meta')
    if (!hasTodos && !hasMeta) {
      return false
    }

    const incomingMeta = parseTodoMeta((stateData as Record<string, unknown>).todos_meta)
    const nextVersion = incomingMeta?.version ?? 0
    if (meta.value.version > 0 && nextVersion <= meta.value.version) {
      if (nextVersion < meta.value.version) {
        console.warn('[todo] dropped stale todo state update', {
          currentVersion: meta.value.version,
          incomingVersion: nextVersion,
          source
        })
      }
      return false
    }

    const nextItems = parseTodoItems((stateData as Record<string, unknown>).todos)
    items.value = nextItems
    meta.value = incomingMeta ?? { version: nextVersion }
    lastSyncAt.value = new Date().toISOString()
    syncSource.value = source
    return true
  }

  function markTodoToolSeen(_source?: string) {
    toolSeenInSession.value = true
  }

  function clearOnSessionReset() {
    items.value = []
    meta.value = { version: 0 }
    lastSyncAt.value = null
    syncSource.value = ''
    toolSeenInSession.value = false
  }

  return {
    items,
    meta,
    lastSyncAt,
    syncSource,
    toolSeenInSession,
    sortedItems,
    hasTodoPanel,
    groupedItems,
    activeCount,
    syncFromStateData,
    markTodoToolSeen,
    clearOnSessionReset
  }
})
