const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function importWorkspace(file: File, strategy: 'merge' | 'overwrite' = 'merge'): Promise<number> {
  const form = new FormData()
  form.append('file', file)
  form.append('strategy', strategy)

  const res = await fetch(`${API_BASE}/api/workspace/import`, {
    method: 'POST',
    body: form
  })

  if (!res.ok) {
    throw new Error('Failed to import workspace')
  }

  const json: ApiResponse<{ importedFiles: number }> = await res.json()
  return json.data.importedFiles
}

export function getWorkspaceExportUrl(): string {
  return `${API_BASE}/api/workspace/export`
}
