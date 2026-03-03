import type { CronJob, CreateCronJobRequest, CronRunResult } from '@/types/cron'
const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function fetchCronJobs(): Promise<CronJob[]> {
  const res = await fetch(`${API_BASE}/api/cron/jobs`)
  if (!res.ok) throw new Error('Failed to fetch cron jobs')
  const json: ApiResponse<CronJob[]> = await res.json()
  return json.data || []
}

export async function createCronJob(payload: CreateCronJobRequest): Promise<CronJob> {
  const res = await fetch(`${API_BASE}/api/cron/jobs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error('Failed to create cron job')
  const json: ApiResponse<CronJob> = await res.json()
  return json.data
}

export async function runCronNow(id: number): Promise<number> {
  const res = await fetch(`${API_BASE}/api/cron/jobs/${id}/run`, { method: 'POST' })
  if (!res.ok) throw new Error('Failed to run cron job')
  const json: ApiResponse<CronRunResult> = await res.json()
  return json.data.runId
}

export async function toggleCronJob(id: number, enabled: boolean): Promise<CronJob> {
  const res = await fetch(`${API_BASE}/api/cron/jobs/${id}/enable?enabled=${enabled}`, { method: 'POST' })
  if (!res.ok) throw new Error('Failed to toggle cron job')
  const json: ApiResponse<CronJob> = await res.json()
  return json.data
}
