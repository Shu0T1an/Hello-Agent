export interface ParsedSearchSource {
  title: string
  url: string
}

export interface SearchToolParseResult {
  sources: ParsedSearchSource[]
  parseable: boolean
}

const SEARCH_TOOL_NAME_KEYWORDS = ['search', 'websearch', 'tavily']

function parseJsonSafely(value: string): unknown | null {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isValidHttpUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

function unwrapPayload(value: unknown): unknown {
  let current: unknown = value

  // Some tools return nested JSON in text fields; unwrap a few levels.
  for (let i = 0; i < 3; i += 1) {
    if (Array.isArray(current) && current.length > 0) {
      const first = current[0]
      if (isRecord(first) && typeof first.text === 'string') {
        const parsed = parseJsonSafely(first.text)
        if (parsed !== null) {
          current = parsed
          continue
        }
      }
    }

    if (isRecord(current) && typeof current.text === 'string') {
      const parsed = parseJsonSafely(current.text)
      if (parsed !== null) {
        current = parsed
        continue
      }
    }

    break
  }

  return current
}

function extractResults(value: unknown): unknown[] {
  if (!isRecord(value)) return []
  if (!Array.isArray(value.results)) return []
  return value.results
}

export function isSearchToolName(toolName: string): boolean {
  const normalized = toolName.trim().toLowerCase()
  if (!normalized) return false
  return SEARCH_TOOL_NAME_KEYWORDS.some(keyword => normalized.includes(keyword))
}

export function parseSearchToolSources(toolName: string, responseText: string): SearchToolParseResult {
  if (!isSearchToolName(toolName)) {
    return { sources: [], parseable: false }
  }

  const parsedRoot = parseJsonSafely(responseText)
  if (parsedRoot === null) {
    return { sources: [], parseable: false }
  }

  const unwrapped = unwrapPayload(parsedRoot)
  const rawResults = extractResults(unwrapped)
  if (rawResults.length === 0) {
    return { sources: [], parseable: true }
  }

  const seenUrls = new Set<string>()
  const sources: ParsedSearchSource[] = []

  for (const item of rawResults) {
    if (!isRecord(item)) continue

    const url = typeof item.url === 'string' ? item.url.trim() : ''
    if (!url || !isValidHttpUrl(url) || seenUrls.has(url)) continue

    const title = typeof item.title === 'string' && item.title.trim().length > 0
      ? item.title.trim()
      : url

    seenUrls.add(url)
    sources.push({ title, url })
  }

  return { sources, parseable: true }
}
