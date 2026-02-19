import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { codeToHtml } from 'shiki'
import type { CitationReference } from '@/types/message'

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
  const safeContent = content || ''
  return `${theme}:${safeContent.length}:${safeContent.slice(0, 100)}`
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
    const safeCode = code || ''
    const cacheKey = `${shikiTheme}:${language}:${safeCode.slice(0, 50)}:${safeCode.length}`
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
      const highlighted = await codeToHtml(safeCode, {
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
 * 渲染引用标记为可点击元素
 */
function renderCitationMarkers(html: string, citations: CitationReference[]): string {
  // 如果没有 citations 数据，只做简单的样式渲染（不需要点击展开功能）
  if (!citations?.length) {
    console.log('[renderCitationMarkers] citations 为空，仅渲染样式')
    // 纯前端渲染：给所有引用标记添加样式，但不关联具体引用数据
    return html.replace(/\[([a-zA-Z]+\d*):p(\d+)\]/g, (match) => {
      return `<span class="citation-marker citation-simple">${match}</span>`
    })
  }

  // 创建 chunkId 到 citation 索引的映射
  const citationMap = new Map<string, number>();
  citations.forEach((citation, index) => {
    citationMap.set(citation.chunkId, index);
    console.log(`[renderCitationMarkers] 注册引用: ${citation.chunkId} -> index ${index}`);
  });

  // 替换引用标记 [doc0:p0] 或类似格式
  let matchCount = 0;
  const result = html.replace(/\[([a-zA-Z]+\d*):p(\d+)\]/g, (match, doc, page) => {
    const chunkId = `${doc}:p${page}`;
    const index = citationMap.get(chunkId);
    console.log(`[renderCitationMarkers] 匹配到引用标记: ${match}, chunkId: ${chunkId}, index: ${index}`);

    if (index !== undefined) {
      matchCount++;
      // 返回可点击的引用标记（不使用内联事件，通过事件委托处理）
      return `<span class="citation-marker" data-citation-index="${index}">${match}</span>`;
    }
    // 找不到对应的引用数据，仅渲染样式
    return `<span class="citation-marker citation-simple">${match}</span>`;
  });

  console.log(`[renderCitationMarkers] 总共处理了 ${matchCount} 个引用标记`)
  return result;
}

/**
 * 主渲染函数
 */
export async function renderMarkdown(
  content: string,
  options: {
    theme?: CodeTheme;
    citations?: CitationReference[];
  } = {}
): Promise<string> {
  const { theme = 'github-dark', citations } = options;
  currentTheme = theme

  console.log('[renderMarkdown] 开始渲染, citations 数量:', citations?.length || 0)

  // 处理空内容
  const safeContent = content || ''

  // 检查缓存
  const cacheKey = generateCacheKey(safeContent, theme)
  const cached = markdownCache.get(cacheKey)

  if (cached) {
    console.log('[renderMarkdown] 使用缓存, 是否包含 citation-marker:', cached.includes('citation-marker'))
    // 如果有引用标记且缓存结果没有应用引用标记渲染，则重新渲染
    if (citations?.length && !cached.includes('citation-marker')) {
      console.log('[renderMarkdown] 缓存中没有引用标记，重新渲染')
      const finalHtml = renderCitationMarkers(cached, citations);
      return finalHtml;
    }
    return cached
  }

  console.log('[renderMarkdown] 未命中缓存，开始完整渲染')
  const shikiTheme = THEMES[theme].shikiTheme

  // 渲染 markdown
  const rawHtml = md.render(safeContent)
  console.log('[renderMarkdown] markdown 渲染完成，检查是否有引用标记:', rawHtml.includes('[doc'))

  // 使用 DOMPurify 清理
  const cleanHtml = DOMPurify.sanitize(rawHtml)

  // 对代码块进行语法高亮
  let highlightedHtml = await highlightCodeBlocks(cleanHtml, shikiTheme)

  // 渲染引用标记
  if (citations?.length) {
    console.log('[renderMarkdown] 开始渲染引用标记')
    highlightedHtml = renderCitationMarkers(highlightedHtml, citations);
  }

  // 缓存结果
  markdownCache.set(cacheKey, highlightedHtml)
  clearCacheIfNeeded()

  console.log('[renderMarkdown] 渲染完成，是否包含 citation-marker:', highlightedHtml.includes('citation-marker'))
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

/**
 * 测试引用标记渲染（用于调试）
 */
export async function testCitationRendering() {
  console.log('=== 测试引用标记渲染 ===\n')

  const testContent = '根据文档 [doc0:p0]，系统支持多种功能。参考 [doc0:p1] 了解更多。'

  // 测试 1: 有完整引用数据
  console.log('【测试 1】有完整引用数据（可点击展开）')
  const testCitations: CitationReference[] = [
    {
      chunkId: 'doc0:p0',
      fileName: 'test.txt',
      content: '这是第一个文档块的内容',
      chunkIndex: 0
    },
    {
      chunkId: 'doc0:p1',
      fileName: 'test.txt',
      content: '这是第二个文档块的内容',
      chunkIndex: 1
    }
  ]

  console.log('测试内容:', testContent)
  console.log('测试引用:', testCitations)

  const result1 = await renderMarkdown(testContent, {
    theme: 'github-dark',
    citations: testCitations
  })

  console.log('✅ 渲染结果包含 data-citation-index:', result1.includes('data-citation-index'))
  console.log('✅ 渲染结果包含 citation-marker:', result1.includes('citation-marker'))
  console.log()

  // 测试 2: 无引用数据（仅样式）
  console.log('【测试 2】无引用数据（仅样式，不可点击）')
  const result2 = await renderMarkdown(testContent, {
    theme: 'github-dark',
    citations: []
  })

  console.log('✅ 渲染结果包含 citation-simple:', result2.includes('citation-simple'))
  console.log('✅ 渲染结果包含 citation-marker:', result2.includes('citation-marker'))
  console.log()

  console.log('=== 测试完成 ===')
  console.log('💡 如果看到上述结果，说明引用标记渲染功能正常')
  console.log('💡 如果后端传递了 citations 数据，标记可点击展开')
  console.log('💡 如果后端未传递 citations 数据，标记仅显示样式')

  return { withCitations: result1, withoutCitations: result2 }
}
