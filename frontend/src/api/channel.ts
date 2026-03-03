import type { ChannelConfig, CreateChannelRequest, UpdateChannelRequest } from '@/types/channel'
const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function fetchChannels(): Promise<ChannelConfig[]> {
  const res = await fetch(`${API_BASE}/api/channels`)
  if (!res.ok) throw new Error(await parseErrorMessage(res, 'Failed to fetch channels'))
  const json: ApiResponse<ChannelConfig[]> = await res.json()
  return json.data || []
}

export async function createChannel(payload: CreateChannelRequest): Promise<ChannelConfig> {
  const res = await fetch(`${API_BASE}/api/channels`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error(await parseErrorMessage(res, 'Failed to create channel'))
  const json: ApiResponse<ChannelConfig> = await res.json()
  return json.data
}

export async function updateChannel(id: number, payload: UpdateChannelRequest): Promise<ChannelConfig> {
  const res = await fetch(`${API_BASE}/api/channels/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error(await parseErrorMessage(res, 'Failed to update channel'))
  const json: ApiResponse<ChannelConfig> = await res.json()
  return json.data
}

export async function toggleChannel(id: number, enabled: boolean): Promise<ChannelConfig> {
  const res = await fetch(`${API_BASE}/api/channels/${id}/enable?enabled=${enabled}`, { method: 'POST' })
  if (!res.ok) throw new Error(await parseErrorMessage(res, 'Failed to toggle channel'))
  const json: ApiResponse<ChannelConfig> = await res.json()
  return json.data
}

async function parseErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const json = await response.json() as { message?: string }
    if (json.message && json.message.trim()) {
      return json.message
    }
  } catch {
    // ignore parsing error and use fallback
  }
  return fallback
}
