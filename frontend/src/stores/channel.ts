import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChannelConfig, CreateChannelRequest, UpdateChannelRequest } from '@/types/channel'
import * as channelApi from '@/api/channel'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref<ChannelConfig[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)

  async function fetchChannels() {
    loading.value = true
    error.value = null
    try {
      channels.value = await channelApi.fetchChannels()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load channels'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function createChannel(payload: CreateChannelRequest) {
    saving.value = true
    error.value = null
    try {
      const created = await channelApi.createChannel(payload)
      channels.value = [created, ...channels.value]
      return created
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create channel'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function updateChannel(id: number, payload: UpdateChannelRequest) {
    saving.value = true
    error.value = null
    try {
      const updated = await channelApi.updateChannel(id, payload)
      channels.value = channels.value.map(item => (item.id === id ? updated : item))
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to update channel'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function toggleChannel(id: number, enabled: boolean) {
    saving.value = true
    error.value = null
    try {
      const updated = await channelApi.toggleChannel(id, enabled)
      channels.value = channels.value.map(item => (item.id === id ? updated : item))
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to toggle channel'
      throw e
    } finally {
      saving.value = false
    }
  }

  return {
    channels,
    loading,
    saving,
    error,
    fetchChannels,
    createChannel,
    updateChannel,
    toggleChannel
  }
})
