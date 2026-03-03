<template>
  <div class="pane">
    <header class="header-row">
      <h2>Channel Management</h2>
      <button class="btn" @click="load">Refresh</button>
    </header>

    <form class="form" @submit.prevent="create">
      <input v-model="form.channelName" placeholder="channel name" required />
      <select v-model="form.channelType">
        <option value="dingtalk">dingtalk</option>
      </select>
      <input v-model="form.clientId" placeholder="client id" required />
      <input v-model="form.clientSecret" type="password" placeholder="client secret" required />
      <input v-model="form.botPrefix" placeholder="bot prefix (optional)" />
      <button class="btn">Create</button>
    </form>

    <div v-if="error" class="error">{{ error }}</div>

    <table class="table">
      <thead>
      <tr>
        <th>Name</th>
        <th>Type</th>
        <th>Status</th>
        <th>Enabled</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="item in channels" :key="item.id">
        <td>{{ item.channelName }}</td>
        <td>{{ item.channelType }}</td>
        <td>{{ item.status || '-' }}</td>
        <td>
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
import { useChannelStore } from '@/stores/channel'
import type { ChannelConfig, CreateChannelRequest } from '@/types/channel'

const channelStore = useChannelStore()
const { channels, error } = storeToRefs(channelStore)

interface ChannelFormState {
  channelName: string
  channelType: string
  enabled: boolean
  clientId: string
  clientSecret: string
  botPrefix: string
}

const form = reactive<ChannelFormState>({
  channelName: '',
  channelType: 'dingtalk',
  enabled: true,
  clientId: '',
  clientSecret: '',
  botPrefix: ''
})

async function load() {
  try {
    await channelStore.fetchChannels()
  } catch (e) {
    console.error(e)
  }
}

async function create() {
  const payload: CreateChannelRequest = {
    channelName: form.channelName.trim(),
    channelType: form.channelType,
    enabled: form.enabled,
    config: {
      clientId: form.clientId.trim(),
      clientSecret: form.clientSecret.trim()
    }
  }
  const botPrefix = form.botPrefix.trim()
  if (botPrefix) {
    payload.config = {
      ...payload.config,
      botPrefix
    }
  }

  try {
    await channelStore.createChannel(payload)
    form.channelName = ''
    form.clientId = ''
    form.clientSecret = ''
    form.botPrefix = ''
  } catch (e) {
    console.error(e)
  }
}

async function toggle(item: ChannelConfig) {
  if (!item.id) return
  try {
    await channelStore.toggleChannel(item.id, !item.enabled)
  } catch (e) {
    console.error(e)
  }
}

onMounted(load)
</script>

<style scoped>
.pane { padding: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.form { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 8px; margin: 12px 0; }
.error { color: #b91c1c; margin-bottom: 8px; }
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { border-bottom: 1px solid #e2e8f0; padding: 8px; text-align: left; }
.btn { border: 1px solid #cbd5e1; border-radius: 8px; padding: 6px 10px; background: #fff; }
</style>
