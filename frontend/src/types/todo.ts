export type TodoStatus = 'pending' | 'in_progress' | 'completed' | 'blocked'
export type TodoPriority = 'low' | 'medium' | 'high'

export interface TodoItem {
  id: string
  content: string
  status: TodoStatus
  priority?: TodoPriority
  createdAt?: string
  updatedAt?: string
}

export interface TodoMeta {
  version: number
  updatedAt?: string
  updatedByToolCallId?: string
  lastOperation?: string
}

