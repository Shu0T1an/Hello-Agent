import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { SkillDetail, SkillReference, SkillReferenceContent, SkillSummary } from '@/types/skill'
import * as skillApi from '@/api/skill'

export const useSkillStore = defineStore('skill', () => {
  const skills = ref<SkillSummary[]>([])
  const selectedSkillId = ref<string | null>(null)
  const selectedSkillDetail = ref<SkillDetail | null>(null)
  const selectedReference = ref<SkillReference | null>(null)
  const selectedReferenceContent = ref<SkillReferenceContent | null>(null)

  const loadingList = ref(false)
  const loadingDetail = ref(false)
  const loadingReference = ref(false)
  const syncing = ref(false)
  const error = ref<string | null>(null)

  const hasSelectedSkill = computed(() => !!selectedSkillId.value)

  async function loadSkills(query = '') {
    loadingList.value = true
    error.value = null
    try {
      skills.value = await skillApi.fetchSkills(query)
      if (selectedSkillId.value && !skills.value.some(s => s.id === selectedSkillId.value)) {
        clearSelection()
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load skills'
      throw e
    } finally {
      loadingList.value = false
    }
  }

  async function selectSkill(skillId: string) {
    selectedSkillId.value = skillId
    selectedReference.value = null
    selectedReferenceContent.value = null
    loadingDetail.value = true
    error.value = null
    try {
      selectedSkillDetail.value = await skillApi.fetchSkillDetail(skillId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load skill detail'
      throw e
    } finally {
      loadingDetail.value = false
    }
  }

  async function openReference(ref: SkillReference) {
    if (!selectedSkillId.value) {
      return
    }
    selectedReference.value = ref
    loadingReference.value = true
    error.value = null
    try {
      selectedReferenceContent.value = await skillApi.fetchSkillReference(selectedSkillId.value, ref.refId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load reference content'
      throw e
    } finally {
      loadingReference.value = false
    }
  }

  async function reindex() {
    syncing.value = true
    error.value = null
    try {
      const result = await skillApi.reindexSkills()
      await loadSkills()
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to reindex skills'
      throw e
    } finally {
      syncing.value = false
    }
  }

  async function enableSkill(skillId: string) {
    await skillApi.enableSkill(skillId)
    await loadSkills()
  }

  async function disableSkill(skillId: string) {
    await skillApi.disableSkill(skillId)
    await loadSkills()
  }

  async function createSkill(name: string, content: string, enable = true) {
    await skillApi.createSkill({ name, content, enable })
    await loadSkills()
  }

  async function importFromGithub(url: string, overwrite = false, enableAfterImport = true) {
    const result = await skillApi.importSkillFromGithub({ url, overwrite, enableAfterImport })
    await loadSkills()
    return result
  }

  function clearSelection() {
    selectedSkillId.value = null
    selectedSkillDetail.value = null
    selectedReference.value = null
    selectedReferenceContent.value = null
  }

  return {
    skills,
    selectedSkillId,
    selectedSkillDetail,
    selectedReference,
    selectedReferenceContent,
    loadingList,
    loadingDetail,
    loadingReference,
    syncing,
    error,
    hasSelectedSkill,
    loadSkills,
    selectSkill,
    openReference,
    reindex,
    enableSkill,
    disableSkill,
    createSkill,
    importFromGithub,
    clearSelection
  }
})
