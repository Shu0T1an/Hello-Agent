import type { MemoryDocument } from '@/types/memory'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function throwHttpError(res: Response, fallbackMessage: string): Promise<never> {
  let details = ''
  try {
    const text = await res.text()
    if (text) {
      details = `: ${text.slice(0, 300)}`
    }
  } catch {
    details = ''
  }
  throw new Error(`${fallbackMessage} (${res.status})${details}`)
}

export async function fetchMemoryDocument(): Promise<MemoryDocument> {
  const res = await fetch(`${API_BASE}/api/memory`)
  if (!res.ok) {
    await throwHttpError(res, 'Failed to fetch memory document')
  }
  const json: ApiResponse<MemoryDocument> = await res.json()
  return json.data
}

export async function updateMemoryDocument(content: string): Promise<MemoryDocument> {
  const res = await fetch(`${API_BASE}/api/memory`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  })
  if (!res.ok) {
    await throwHttpError(res, 'Failed to update memory document')
  }
  const json: ApiResponse<MemoryDocument> = await res.json()
  return json.data
}
