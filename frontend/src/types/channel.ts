export interface ChannelConfig {
  id?: number
  channelName: string
  channelType: string
  config?: Record<string, unknown>
  enabled?: boolean
  status?: string
}

export interface CreateChannelRequest {
  channelName: string
  channelType: string
  config?: Record<string, unknown>
  enabled?: boolean
}
