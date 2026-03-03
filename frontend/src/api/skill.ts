import type { SkillDetail, SkillReferenceContent, SkillSummary } from '@/types/skill'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function fetchSkills(query?: string, limit = 200): Promise<SkillSummary[]> {
  const params = new URLSearchParams()
  if (query && query.trim().length > 0) {
    params.set('q', query.trim())
  }
  params.set('limit', String(limit))

  const url = `${API_BASE}/api/skills${params.toString() ? `?${params.toString()}` : ''}`
  const res = await fetch(url)
  if (!res.ok) {
    throw new Error('Failed to fetch skills')
  }
  const json: ApiResponse<SkillSummary[]> = await res.json()
  return json.data || []
}

export async function fetchSkillDetail(skillId: string): Promise<SkillDetail> {
  const res = await fetch(`${API_BASE}/api/skills/${encodeURIComponent(skillId)}`)
  if (!res.ok) {
    throw new Error(`Failed to fetch skill detail: ${skillId}`)
  }
  const json: ApiResponse<SkillDetail> = await res.json()
  return json.data
}

export async function fetchSkillReference(skillId: string, refId: string): Promise<SkillReferenceContent> {
  const res = await fetch(
    `${API_BASE}/api/skills/${encodeURIComponent(skillId)}/references/${encodeURIComponent(refId)}`
  )
  if (!res.ok) {
    throw new Error(`Failed to fetch skill reference: ${refId}`)
  }
  const json: ApiResponse<SkillReferenceContent> = await res.json()
  return json.data
}

export async function reindexSkills(): Promise<{ count: number; roots: number }> {
  const res = await fetch(`${API_BASE}/api/skills/reindex`, { method: 'POST' })
  if (!res.ok) {
    throw new Error('Failed to reindex skills')
  }
  const json: ApiResponse<{ count: number; roots: number }> = await res.json()
  return json.data
}

export async function createSkill(payload: {
  name: string
  content: string
  enable?: boolean
}): Promise<{ skillId: string }> {
  const res = await fetch(`${API_BASE}/api/skills`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) {
    throw new Error('Failed to create skill')
  }
  const json: ApiResponse<{ skillId: string }> = await res.json()
  return json.data
}

export async function updateSkill(skillId: string, content: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/skills/${encodeURIComponent(skillId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  })
  if (!res.ok) {
    throw new Error('Failed to update skill')
  }
}

export async function deleteSkill(skillId: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/skills/${encodeURIComponent(skillId)}`, { method: 'DELETE' })
  if (!res.ok) {
    throw new Error('Failed to delete skill')
  }
}

export async function enableSkill(skillId: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/skills/${encodeURIComponent(skillId)}/enable`, { method: 'POST' })
  if (!res.ok) {
    throw new Error('Failed to enable skill')
  }
}

export async function disableSkill(skillId: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/skills/${encodeURIComponent(skillId)}/disable`, { method: 'POST' })
  if (!res.ok) {
    throw new Error('Failed to disable skill')
  }
}

export async function importSkillFromGithub(payload: {
  url: string
  overwrite?: boolean
  enableAfterImport?: boolean
}): Promise<{ imported: number; skipped: number; enabled: number }> {
  const res = await fetch(`${API_BASE}/api/skills/import/github`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) {
    throw new Error('Failed to import skill')
  }
  const json: ApiResponse<{ imported: number; skipped: number; enabled: number }> = await res.json()
  return json.data
}
