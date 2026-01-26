/**
 * 模型配置类型定义
 */

/**
 * 模型配置接口
 */
export interface ModelConfig {
  id: number
  modelName: string
  displayName: string
  provider: string
  modelId: string
  baseUrl?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 创建模型配置 DTO
 */
export interface CreateModelDTO {
  modelName: string
  displayName: string
  provider: string
  modelId: string
  baseUrl?: string
  apiKey: string
  isActive?: boolean
}

/**
 * 更新模型配置 DTO
 */
export interface UpdateModelDTO {
  displayName: string
  provider: string
  modelId: string
  baseUrl?: string
  apiKey?: string
  isActive?: boolean
}

/**
 * 模型提供商枚举
 */
export enum ModelProvider {
  OPENAI = 'OPENAI',
  DEEPSEEK = 'DEEPSEEK',
  GROQ = 'GROQ',
  PERPLEXITY = 'PERPLEXITY',
  OPENAI_COMPATIBLE = 'OPENAI_COMPATIBLE'
}

/**
 * 提供商显示名称映射
 */
export const ProviderDisplayNames: Record<ModelProvider, string> = {
  [ModelProvider.OPENAI]: 'OpenAI',
  [ModelProvider.DEEPSEEK]: 'DeepSeek',
  [ModelProvider.GROQ]: 'Groq',
  [ModelProvider.PERPLEXITY]: 'Perplexity',
  [ModelProvider.OPENAI_COMPATIBLE]: 'OpenAI 兼容'
}
