export interface CronJob {
  id?: number
  jobName: string
  cronExpression: string
  zoneId?: string
  agentName: string
  sessionId?: string
  inputText?: string
  enabled?: boolean
  lastStatus?: string
  nextRunAt?: string
}

export interface CreateCronJobRequest {
  jobName: string
  cronExpression: string
  zoneId?: string
  agentName: string
  sessionId?: string
  inputText?: string
  enabled?: boolean
}

export interface CronRunResult {
  runId: number
}
