import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { CronJob, CreateCronJobRequest } from '@/types/cron'
import * as cronApi from '@/api/cron'

export const useCronStore = defineStore('cron', () => {
  const jobs = ref<CronJob[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)

  async function fetchJobs() {
    loading.value = true
    error.value = null
    try {
      jobs.value = await cronApi.fetchCronJobs()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load jobs'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function createJob(payload: CreateCronJobRequest) {
    saving.value = true
    error.value = null
    try {
      const created = await cronApi.createCronJob(payload)
      jobs.value = [created, ...jobs.value]
      return created
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create job'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function runNow(id: number) {
    saving.value = true
    error.value = null
    try {
      return await cronApi.runCronNow(id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to run job'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function toggleJob(id: number, enabled: boolean) {
    saving.value = true
    error.value = null
    try {
      const updated = await cronApi.toggleCronJob(id, enabled)
      jobs.value = jobs.value.map(item => (item.id === id ? updated : item))
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to toggle job'
      throw e
    } finally {
      saving.value = false
    }
  }

  return {
    jobs,
    loading,
    saving,
    error,
    fetchJobs,
    createJob,
    runNow,
    toggleJob
  }
})
