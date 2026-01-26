/**
 * 模型配置管理 API
 */

import type { ModelConfig, CreateModelDTO, UpdateModelDTO } from '@/types/model'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

/**
 * 统一响应接口
 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 获取所有模型配置
 */
export async function fetchModels(): Promise<ModelConfig[]> {
  const res = await fetch(`${API_BASE}/api/models`)
  if (!res.ok) throw new Error('Failed to fetch models')
  const json: ApiResponse<ModelConfig[]> = await res.json()
  return json.data
}

/**
 * 根据 ID 获取单个模型配置
 */
export async function fetchModel(id: number): Promise<ModelConfig> {
  const res = await fetch(`${API_BASE}/api/models/${id}`)
  if (!res.ok) throw new Error(`Failed to fetch model ${id}`)
  const json: ApiResponse<ModelConfig> = await res.json()
  return json.data
}

/**
 * 获取激活的模型配置
 */
export async function fetchActiveModels(): Promise<ModelConfig[]> {
  const res = await fetch(`${API_BASE}/api/models/active`)
  if (!res.ok) throw new Error('Failed to fetch active models')
  const json: ApiResponse<ModelConfig[]> = await res.json()
  return json.data
}

/**
 * 根据提供商获取模型配置
 */
export async function fetchModelsByProvider(provider: string): Promise<ModelConfig[]> {
  const res = await fetch(`${API_BASE}/api/models/provider/${encodeURIComponent(provider)}`)
  if (!res.ok) throw new Error(`Failed to fetch models for provider ${provider}`)
  const json: ApiResponse<ModelConfig[]> = await res.json()
  return json.data
}

/**
 * 获取所有提供商
 */
export async function fetchProviders(): Promise<string[]> {
  const res = await fetch(`${API_BASE}/api/models/providers`)
  if (!res.ok) throw new Error('Failed to fetch providers')
  const json: ApiResponse<string[]> = await res.json()
  return json.data
}

/**
 * 创建模型配置
 */
export async function createModel(data: CreateModelDTO): Promise<ModelConfig> {
  const res = await fetch(`${API_BASE}/api/models`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error('Failed to create model')
  const json: ApiResponse<ModelConfig> = await res.json()
  return json.data
}

/**
 * 更新模型配置
 */
export async function updateModel(id: number, data: UpdateModelDTO): Promise<ModelConfig> {
  const res = await fetch(`${API_BASE}/api/models/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error(`Failed to update model ${id}`)
  const json: ApiResponse<ModelConfig> = await res.json()
  return json.data
}

/**
 * 删除模型配置
 */
export async function deleteModel(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/models/${id}`, {
    method: 'DELETE'
  })
  if (!res.ok) throw new Error(`Failed to delete model ${id}`)
}
