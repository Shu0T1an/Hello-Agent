/**
 * RAG API 接口
 */

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

// ========== 类型定义 ==========

/**
 * RAG 查询请求
 */
export interface RagQueryRequest {
  query: string
  knowledgeBaseId?: string
  topK?: number
  similarityThreshold?: number
}

/**
 * RAG 查询结果
 */
export interface RagQueryResult {
  response: string
  sourceDocuments: SourceDocument[]
  query: string
}

/**
 * 来源文档
 */
export interface SourceDocument {
  fileName?: string
  knowledgeBaseId?: string
  score?: number
}

/**
 * 上传结果
 */
export interface UploadResult {
  fileName: string
  knowledgeBaseId: string
  chunkCount: number
  message: string
}

/**
 * 批量上传结果
 */
export interface BatchUploadResult {
  fileCount: number
  successCount: number
  knowledgeBaseId: string
  totalChunks: number
  message: string
}

/**
 * API 响应
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// ========== API 函数 ==========

/**
 * 上传文档
 */
export async function uploadDocument(
  file: File,
  knowledgeBaseId = 'default'
): Promise<ApiResponse<UploadResult>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('knowledgeBaseId', knowledgeBaseId)

  const res = await fetch(`${API_BASE}/api/rag/documents/upload`, {
    method: 'POST',
    body: formData
  })
  if (!res.ok) throw new Error('Failed to upload document')
  return await res.json()
}

/**
 * 批量上传文档
 */
export async function batchUploadDocuments(
  files: File[],
  knowledgeBaseId = 'default'
): Promise<ApiResponse<BatchUploadResult>> {
  const formData = new FormData()
  files.forEach(file => {
    formData.append('files', file)
  })
  formData.append('knowledgeBaseId', knowledgeBaseId)

  const res = await fetch(`${API_BASE}/api/rag/documents/batch-upload`, {
    method: 'POST',
    body: formData
  })
  if (!res.ok) throw new Error('Failed to upload documents')
  return await res.json()
}

/**
 * RAG 查询（非流式）
 */
export async function ragQuery(
  data: RagQueryRequest
): Promise<ApiResponse<RagQueryResult>> {
  const res = await fetch(`${API_BASE}/api/rag/query`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error('Failed to query')
  return await res.json()
}

/**
 * RAG 流式查询
 *
 * @param data 查询参数
 * @param onMessage 接收消息块的回调
 * @param onComplete 完成回调
 * @param onError 错误回调
 * @returns 关闭 EventSource 的函数
 */
export function ragQueryStream(
  data: RagQueryRequest,
  onMessage: (chunk: string) => void,
  onComplete?: () => void,
  onError?: (error: Error) => void
): (() => void) {
  // 构建查询参数
  const params = new URLSearchParams()
  params.append('query', data.query)
  if (data.knowledgeBaseId) {
    params.append('knowledgeBaseId', data.knowledgeBaseId)
  }
  if (data.topK) {
    params.append('topK', data.topK.toString())
  }
  if (data.similarityThreshold) {
    params.append('similarityThreshold', data.similarityThreshold.toString())
  }

  // 创建 SSE 连接
  const eventSource = new EventSource(
    `${API_BASE}/api/rag/query/stream?${params.toString()}`
  )

  eventSource.onmessage = (event) => {
    const chunk = event.data
    onMessage(chunk)
  }

  eventSource.onerror = (error) => {
    console.error('SSE error:', error)
    onError?.(new Error('Stream error'))
    eventSource.close()
  }

  eventSource.addEventListener('complete', () => {
    onComplete?.()
    eventSource.close()
  })

  // 返回关闭函数
  return () => {
    eventSource.close()
  }
}

/**
 * 搜索相似文档（不调用 LLM）
 */
export async function searchDocuments(
  data: RagQueryRequest
): Promise<ApiResponse<SourceDocument[]>> {
  const res = await fetch(`${API_BASE}/api/rag/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error('Failed to search documents')
  return await res.json()
}

/**
 * 删除知识库的所有文档
 */
export async function deleteKnowledgeBaseDocuments(
  kbId: string
): Promise<ApiResponse<void>> {
  const res = await fetch(`${API_BASE}/api/rag/knowledge-bases/${kbId}/documents`, {
    method: 'DELETE'
  })
  if (!res.ok) throw new Error('Failed to delete documents')
  return await res.json()
}
