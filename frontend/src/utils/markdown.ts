import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { codeToHtml } from 'shiki'

// 主题类型
export type CodeTheme = 'light' | 'dark' | 'github-light' | 'github-dark' | 'nord' | 'monokai'

// 支持的主题配置
const THEMES: Record<CodeTheme, { name: string; shikiTheme: string }> = {
  light: { name: '浅色', shikiTheme: 'github-light' },
  dark: { name: '深色', shikiTheme: 'github-dark' },
  'github-light': { name: 'GitHub Light', shikiTheme: 'github-light' },
  'github-dark': { name: 'GitHub Dark', shikiTheme: 'github-dark' },
  nord: { name: 'Nord', shikiTheme: 'nord' },
  monokai: { name: 'Monokai', shikiTheme: 'monokai' }
}

let currentTheme: CodeTheme = 'github-dark'

// Markdown 渲染缓存
const markdownCache = new Map<string, string>()

// 代码高亮缓存
const codeHighlightCache = new Map<string, string>()

// 最大缓存大小
const MAX_CACHE_SIZE = 100

// 配置 markdown-it
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

/**
 * 清理缓存
 */
function clearCacheIfNeeded() {
  if (markdownCache.size > MAX_CACHE_SIZE) {
    const firstKey = markdownCache.keys().next().value
    if (firstKey) {
      markdownCache.delete(firstKey)
    }
  }

  if (codeHighlightCache.size > MAX_CACHE_SIZE) {
    const firstKey = codeHighlightCache.keys().next().value
    if (firstKey) {
      codeHighlightCache.delete(firstKey)
    }
  }
}

/**
 * 生成缓存键
 */
function generateCacheKey(content: string, theme: CodeTheme): string {
  return `${theme}:${content.length}:${content.slice(0, 100)}`
}

/**
 * 对代码块进行语法高亮
 */
async function highlightCodeBlocks(html: string, shikiTheme: string): Promise<string> {
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = html

  const codeBlocks = tempDiv.querySelectorAll('pre code')

  for (const codeEl of codeBlocks) {
    const language = codeEl.className.match(/language-(\w+)/)?.[1] || 'text'
    const code = codeEl.textContent || ''

    // 检查缓存
    const cacheKey = `${shikiTheme}:${language}:${code.slice(0, 50)}:${code.length}`
    const cached = codeHighlightCache.get(cacheKey)

    if (cached) {
      const shikiContainer = document.createElement('div')
      shikiContainer.innerHTML = cached
      const newPre = shikiContainer.querySelector('pre')
      if (newPre && codeEl.parentElement) {
        codeEl.parentElement.replaceWith(newPre)
      }
      continue
    }

    // 渲染代码高亮
    try {
      const highlighted = await codeToHtml(code, {
        lang: language,
        theme: shikiTheme
      })

      // 缓存结果
      codeHighlightCache.set(cacheKey, highlighted)
      clearCacheIfNeeded()

      // 替换原有的 pre 标签
      const shikiContainer = document.createElement('div')
      shikiContainer.innerHTML = highlighted

      const newPre = shikiContainer.querySelector('pre')
      if (newPre && codeEl.parentElement) {
        codeEl.parentElement.replaceWith(newPre)
      }
    } catch (e) {
      console.warn(`Language ${language} not supported, falling back to plain text`)
    }
  }

  return tempDiv.innerHTML
}

/**
 * 主渲染函数
 */
export async function renderMarkdown(content: string, theme: CodeTheme = 'github-dark'): Promise<string> {
  currentTheme = theme

  // 检查缓存
  const cacheKey = generateCacheKey(content, theme)
  const cached = markdownCache.get(cacheKey)

  if (cached) {
    return cached
  }

  const shikiTheme = THEMES[theme].shikiTheme

  // 渲染 markdown
  const rawHtml = md.render(content)

  // 使用 DOMPurify 清理
  const cleanHtml = DOMPurify.sanitize(rawHtml)

  // 对代码块进行语法高亮
  const highlightedHtml = await highlightCodeBlocks(cleanHtml, shikiTheme)

  // 缓存结果
  markdownCache.set(cacheKey, highlightedHtml)
  clearCacheIfNeeded()

  return highlightedHtml
}

/**
 * 同步版本（无语法高亮）
 */
export function renderMarkdownSync(content: string): string {
  const rawHtml = md.render(content)
  return DOMPurify.sanitize(rawHtml)
}

/**
 * 获取当前主题
 */
export function getCurrentTheme(): CodeTheme {
  return currentTheme
}

/**
 * 设置主题
 */
export function setTheme(theme: CodeTheme) {
  currentTheme = theme
}

/**
 * 获取所有可用主题
 */
export function getAvailableThemes(): Record<CodeTheme, string> {
  return Object.fromEntries(
    Object.entries(THEMES).map(([key, value]) => [key, value.name])
  ) as Record<CodeTheme, string>
}

/**
 * 清除所有缓存
 */
export function clearCache() {
  markdownCache.clear()
  codeHighlightCache.clear()
}
