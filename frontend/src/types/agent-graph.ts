export type AgentGraphNodeType = 'llm' | 'tool' | 'hook' | 'end' | 'custom'
export type AgentGraphEdgeType = 'normal' | 'conditional'
export type GraphLayoutMode = 'LR' | 'TB'

export interface AgentGraphNode {
  id: string
  label: string
  nodeType: AgentGraphNodeType
  className?: string
  metadata?: Record<string, unknown>
}

export interface AgentGraphEdge {
  id: string
  source: string
  target: string
  edgeType: AgentGraphEdgeType
  label?: string | null
}

export interface AgentGraphStats {
  nodeCount: number
  edgeCount: number
}

export interface AgentGraphResponse {
  agentId: number | null
  agentName: string
  entryPoint: string
  nodes: AgentGraphNode[]
  edges: AgentGraphEdge[]
  stats: AgentGraphStats
  generatedAt: string
}

export interface RuntimeAgentSummary {
  agentName: string
  displayName: string
  description?: string
  builtIn: boolean
  managed: boolean
}
