/**
 * 临时文件上传 API
 */

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

/**
 * 统一响应接口
 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
  summary?: Record<string, unknown>
}

/**
 * 文档块
 */
export interface DocumentChunk {
  chunkId: string
  fileName: string
  content: string
  chunkIndex: number
  startPosition: number
}

/**
 * 临时文件内容
 */
export interface TemporaryFileContent {
  fileId: string
  fileName: string
  size: number
  chunks: DocumentChunk[]
}

/**
 * 上传摘要
 */
export interface UploadSummary {
  fileCount: number
  totalChunks: number
  sessionId: string
}

/**
 * 上传响应
 */
export interface UploadResponse {
  fileContents: TemporaryFileContent[]
  summary: UploadSummary
}

/**
 * 获取支持的文件类型
 */
export async function getSupportedTypes(): Promise<string[]> {
  const res = await fetch(`${API_BASE}/api/files/temporary/supported-types`)
  if (!res.ok) throw new Error('Failed to get supported types')
  const json: ApiResponse<string[]> = await res.json()
  return json.data
}

/**
 * 上传临时文件
 *
 * @param files 要上传的文件列表
 * @param sessionId 会话 ID（可选）
 * @returns 上传响应，包含文件内容和分块信息
 */
export async function uploadTemporaryFiles(
  files: File[],
  sessionId?: string
): Promise<UploadResponse> {
  const formData = new FormData()

  // 添加文件
  files.forEach(file => {
    formData.append('files', file)
  })

  // 添加会话 ID
  if (sessionId) {
    formData.append('sessionId', sessionId)
  }

  const res = await fetch(`${API_BASE}/api/files/temporary/upload`, {
    method: 'POST',
    body: formData
  })

  if (!res.ok) {
    const error = await res.json()
    throw new Error(error.message || 'Failed to upload files')
  }

  const json: ApiResponse<TemporaryFileContent[]> & { summary?: UploadSummary } = await res.json()

  return {
    fileContents: json.data,
    summary: json.summary || {
      fileCount: json.data.length,
      totalChunks: json.data.reduce((sum, fc) => sum + (fc.chunks?.length || 0), 0),
      sessionId: sessionId || 'unknown'
    }
  }
}

/**
 * 验证文件类型是否支持
 *
 * @param fileName 文件名
 * @returns 是否支持
 */
export function isFileTypeSupported(fileName: string): boolean {
  const supportedTypes = ['pdf', 'txt', 'md', 'markdown']
  const extension = fileName.split('.').pop()?.toLowerCase()
  return extension ? supportedTypes.includes(extension) : false
}

/**
 * 验证文件大小是否在限制内
 *
 * @param fileSize 文件大小（字节）
 * @param maxSize 最大文件大小（字节，默认 10MB）
 * @returns 是否在限制内
 */
export function isFileSizeValid(fileSize: number, maxSize = 10 * 1024 * 1024): boolean {
  return fileSize <= maxSize
}

/**
 * 格式化文件大小
 *
 * @param bytes 文件大小（字节）
 * @returns 格式化后的字符串
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
