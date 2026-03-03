import type { ChannelConfig, CreateChannelRequest } from '@/types/channel'
const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function fetchChannels(): Promise<ChannelConfig[]> {
  const res = await fetch(`${API_BASE}/api/channels`)
  if (!res.ok) throw new Error('Failed to fetch channels')
  const json: ApiResponse<ChannelConfig[]> = await res.json()
  return json.data || []
}

export async function createChannel(payload: CreateChannelRequest): Promise<ChannelConfig> {
  const res = await fetch(`${API_BASE}/api/channels`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error('Failed to create channel')
  const json: ApiResponse<ChannelConfig> = await res.json()
  return json.data
}

export async function toggleChannel(id: number, enabled: boolean): Promise<ChannelConfig> {
  const res = await fetch(`${API_BASE}/api/channels/${id}/enable?enabled=${enabled}`, { method: 'POST' })
  if (!res.ok) throw new Error('Failed to toggle channel')
  const json: ApiResponse<ChannelConfig> = await res.json()
  return json.data
}
