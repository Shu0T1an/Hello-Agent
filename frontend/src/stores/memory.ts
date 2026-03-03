import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { MemoryDocument } from '@/types/memory'
import * as memoryApi from '@/api/memory'

export const useMemoryStore = defineStore('memory', () => {
  const document = ref<MemoryDocument | null>(null)
  const draftContent = ref('')
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)

  const isDirty = computed(() => {
    if (!document.value) {
      return false
    }
    return draftContent.value !== document.value.content
  })

  const remainingChars = computed(() => {
    if (!document.value) {
      return null
    }
    return document.value.maxChars - draftContent.value.length
  })

  async function loadDocument() {
    loading.value = true
    error.value = null
    try {
      const doc = await memoryApi.fetchMemoryDocument()
      document.value = doc
      draftContent.value = doc.content
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load memory document'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function saveDocument() {
    saving.value = true
    error.value = null
    try {
      const doc = await memoryApi.updateMemoryDocument(draftContent.value)
      document.value = doc
      draftContent.value = doc.content
      return doc
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to save memory document'
      throw e
    } finally {
      saving.value = false
    }
  }

  function resetDraft() {
    draftContent.value = document.value?.content ?? ''
  }

  return {
    document,
    draftContent,
    loading,
    saving,
    error,
    isDirty,
    remainingChars,
    loadDocument,
    saveDocument,
    resetDraft
  }
})
