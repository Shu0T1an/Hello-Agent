/**
 * 快捷提示词配置
 */

export interface SuggestionPrompt {
  id: string
  title: string
  description?: string
  icon?: string
  prompt: string
}

/**
 * 默认快捷提示词
 */
export const DEFAULT_SUGGESTIONS: SuggestionPrompt[] = [
  {
    id: 'code-help',
    title: '帮我写代码',
    description: '生成、优化或解释代码片段',
    icon: 'Code2',
    prompt: '我需要你帮我写一段代码，请告诉我你需要什么具体功能？'
  },
  {
    id: 'explain-concept',
    title: '解释概念',
    description: '深入讲解技术或业务概念',
    icon: 'Lightbulb',
    prompt: '请帮我详细解释一个概念，你想了解什么？'
  },
  {
    id: 'write-docs',
    title: '写文档',
    description: '创建技术文档或说明',
    icon: 'FileText',
    prompt: '我需要帮助编写文档，请描述文档类型和主要内容'
  },
  {
    id: 'debug-code',
    title: '调试代码',
    description: '定位并修复代码问题',
    icon: 'Bug',
    prompt: '我遇到了代码问题，请粘贴代码和描述错误信息'
  },
  {
    id: 'data-analysis',
    title: '数据分析',
    description: '分析和可视化数据',
    icon: 'BarChart3',
    prompt: '请提供数据，我将帮助您进行分析和可视化'
  },
  {
    id: 'creative-writing',
    title: '创意写作',
    description: '生成创意内容或故事',
    icon: 'Sparkles',
    prompt: '让我们一起开始创意写作，你想要什么类型的内容？'
  }
]
