<template>
  <div class="pane">
    <header class="header-row">
      <h2>Cron Management</h2>
      <button class="btn" @click="load">Refresh</button>
    </header>

    <form class="form" @submit.prevent="create">
      <input v-model="form.jobName" placeholder="job name" required />
      <input v-model="form.cronExpression" placeholder="cron expression" required />
      <input v-model="form.agentName" placeholder="agent name" required />
      <button class="btn">Create</button>
    </form>

    <div v-if="error" class="error">{{ error }}</div>

    <table class="table">
      <thead>
      <tr>
        <th>Name</th>
        <th>Cron</th>
        <th>Status</th>
        <th>Actions</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="item in jobs" :key="item.id">
        <td>{{ item.jobName }}</td>
        <td>{{ item.cronExpression }}</td>
        <td>{{ item.lastStatus || '-' }}</td>
        <td>
          <button class="btn" @click="runNow(item)">Run now</button>
          <button class="btn" @click="toggle(item)">{{ item.enabled ? 'Disable' : 'Enable' }}</button>
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted, reactive } from 'vue'
import { useCronStore } from '@/stores/cron'
import type { CronJob, CreateCronJobRequest } from '@/types/cron'

const cronStore = useCronStore()
const { jobs, error } = storeToRefs(cronStore)

const form = reactive<CreateCronJobRequest>({
  jobName: '',
  cronExpression: '0 0/5 * * * ?',
  agentName: 'general-purpose',
  zoneId: 'Asia/Shanghai',
  enabled: true
})

async function load() {
  try {
    await cronStore.fetchJobs()
  } catch (e) {
    console.error(e)
  }
}

async function create() {
  try {
    await cronStore.createJob(form)
    form.jobName = ''
  } catch (e) {
    console.error(e)
  }
}

async function runNow(item: CronJob) {
  if (!item.id) return
  try {
    await cronStore.runNow(item.id)
    await load()
  } catch (e) {
    console.error(e)
  }
}

async function toggle(item: CronJob) {
  if (!item.id) return
  try {
    await cronStore.toggleJob(item.id, !item.enabled)
  } catch (e) {
    console.error(e)
  }
}

onMounted(load)
</script>

<style scoped>
.pane { padding: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.form { display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 8px; margin: 12px 0; }
.error { color: #b91c1c; margin-bottom: 8px; }
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { border-bottom: 1px solid #e2e8f0; padding: 8px; text-align: left; }
.btn { border: 1px solid #cbd5e1; border-radius: 8px; padding: 6px 10px; background: #fff; margin-right: 6px; }
</style>
