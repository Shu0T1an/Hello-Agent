import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  Checkpoint,
  CheckpointDetail,
  CheckpointFilter,
  RestoreCheckpointRequest,
} from '@/types/checkpoint'
import {
  fetchCheckpoints,
  fetchCheckpointDetail,
  restoreCheckpoint,
  deleteCheckpoint as deleteCheckpointApi,
} from '@/api/checkpoint'
import { useChatStore } from './chat'

export const useCheckpointStore = defineStore('checkpoint', () => {
  // 状态
  const checkpoints = ref<Checkpoint[]>([])
  const currentDetail = ref<CheckpointDetail | null>(null)
  const loading = ref(false)
  const detailLoading = ref(false)
  const restoring = ref(false)
  const deleting = ref(false)
  const error = ref<string | null>(null)
  const filter = ref<CheckpointFilter>({})

  // 计算属性
  const hasCheckpoints = computed(() => checkpoints.value.length > 0)

  const filteredCheckpoints = computed(() => {
    let result = [...checkpoints.value]

    if (filter.value.source) {
      result = result.filter((cp) => cp.source === filter.value.source)
    }

    if (filter.value.nodeId) {
      result = result.filter((cp) => cp.nodeId === filter.value.nodeId)
    }

    return result
  })

  // 操作
  async function loadCheckpoints(sessionId: string) {
    if (loading.value) return

    loading.value = true
    error.value = null

    try {
      checkpoints.value = await fetchCheckpoints(sessionId)
    } catch (err) {
      const message = err instanceof Error ? err.message : '加载 Checkpoint 失败'
      error.value = message
      console.error('Failed to load checkpoints:', err)
      checkpoints.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(checkpointId: string) {
    if (detailLoading.value) return

    detailLoading.value = true
    error.value = null

    try {
      currentDetail.value = await fetchCheckpointDetail(checkpointId)
      return currentDetail.value
    } catch (err) {
      const message = err instanceof Error ? err.message : '加载 Checkpoint 详情失败'
      error.value = message
      console.error('Failed to load checkpoint detail:', err)
      currentDetail.value = null
      return null
    } finally {
      detailLoading.value = false
    }
  }

  async function restoreCheckpoint(checkpointId: string, stateUpdates?: Record<string, unknown>) {
    if (restoring.value) return false

    restoring.value = true
    error.value = null

    try {
      const request: RestoreCheckpointRequest = { checkpointId, stateUpdates }
      const response = await restoreCheckpoint(request)

      // 刷新当前会话
      const chatStore = useChatStore()
      if (chatStore.currentSession) {
        await chatStore.loadSessions()
        const updatedSession = chatStore.sessions.find(
          (s) => s.sessionId === response.sessionId
        )
        if (updatedSession) {
          chatStore.currentSession = updatedSession
        }
      }

      return true
    } catch (err) {
      const message = err instanceof Error ? err.message : '恢复 Checkpoint 失败'
      error.value = message
      console.error('Failed to restore checkpoint:', err)
      return false
    } finally {
      restoring.value = false
    }
  }

  async function deleteCheckpoint(checkpointId: string) {
    if (deleting.value) return false

    deleting.value = true
    error.value = null

    try {
      await deleteCheckpointApi(checkpointId)

      // 从列表中移除
      const index = checkpoints.value.findIndex((cp) => cp.checkpointId === checkpointId)
      if (index !== -1) {
        checkpoints.value.splice(index, 1)
      }

      // 清除详情（如果是当前查看的）
      if (currentDetail.value?.checkpointId === checkpointId) {
        currentDetail.value = null
      }

      return true
    } catch (err) {
      const message = err instanceof Error ? err.message : '删除 Checkpoint 失败'
      error.value = message
      console.error('Failed to delete checkpoint:', err)
      return false
    } finally {
      deleting.value = false
    }
  }

  function setFilter(newFilter: CheckpointFilter) {
    filter.value = { ...newFilter }
  }

  function clearFilter() {
    filter.value = {}
  }

  function clearDetail() {
    currentDetail.value = null
  }

  function clearError() {
    error.value = null
  }

  return {
    // 状态
    checkpoints,
    currentDetail,
    loading,
    detailLoading,
    restoring,
    deleting,
    error,
    filter,

    // 计算属性
    hasCheckpoints,
    filteredCheckpoints,

    // 操作
    loadCheckpoints,
    loadDetail,
    restoreCheckpoint,
    deleteCheckpoint,
    setFilter,
    clearFilter,
    clearDetail,
    clearError,
  }
})
