export interface SkillSummary {
  id: string
  name: string
  description: string
  triggerSummary: string
  skillFile: string
  lastModified?: string
}

export interface SkillSection {
  heading: string
  content: string
}

export interface SkillReference {
  refId: string
  relativePath: string
  category: string
  size: number
}

export interface SkillDetail {
  id: string
  name: string
  skillFile: string
  frontMatter: Record<string, unknown>
  sections: SkillSection[]
  references: SkillReference[]
}

export interface SkillReferenceContent {
  contentType: string
  content: string
  truncated: boolean
  size: number
}

