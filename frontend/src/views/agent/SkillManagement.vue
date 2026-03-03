<template>
  <div class="skills-page">
    <header class="skills-header">
      <div>
        <h1>Skills 管理</h1>
        <p>按“列表 → 详情 → 引用内容”逐层读取 Skill 规范</p>
      </div>
      <div class="skills-header-actions">
        <input
          v-model="searchText"
          class="skills-search"
          type="text"
          placeholder="搜索 skills..."
          @keyup.enter="refresh"
        />
        <button class="btn btn-secondary" :disabled="skillStore.loadingList" @click="refresh">
          刷新
        </button>
        <button class="btn btn-primary" :disabled="skillStore.syncing" @click="reindex">
          {{ skillStore.syncing ? '重建中...' : '重建索引' }}
        </button>
      </div>
    </header>

    <div v-if="skillStore.error" class="skills-error">{{ skillStore.error }}</div>

    <section class="skills-grid">
      <aside class="skills-list">
        <div class="panel-title">Skill 列表</div>
        <div v-if="skillStore.loadingList" class="panel-empty">加载中...</div>
        <button
          v-for="skill in skillStore.skills"
          :key="skill.id"
          class="skill-item"
          :class="{ active: skill.id === skillStore.selectedSkillId }"
          @click="openSkill(skill.id)"
        >
          <div class="skill-item-title">{{ skill.name }}</div>
          <div class="skill-item-desc">{{ skill.description || '无描述' }}</div>
          <div class="skill-item-trigger">{{ skill.triggerSummary || '无触发摘要' }}</div>
        </button>
        <div v-if="!skillStore.loadingList && skillStore.skills.length === 0" class="panel-empty">
          未找到 skills
        </div>
      </aside>

      <main class="skills-detail">
        <div class="panel-title">Skill 详情</div>
        <div v-if="!skillStore.hasSelectedSkill" class="panel-empty">
          请选择左侧 skill
        </div>
        <div v-else-if="skillStore.loadingDetail" class="panel-empty">正在加载详情...</div>
        <template v-else-if="skillStore.selectedSkillDetail">
          <div class="detail-basic">
            <h2>{{ skillStore.selectedSkillDetail.name }}</h2>
            <code>{{ skillStore.selectedSkillDetail.skillFile }}</code>
          </div>

          <div class="detail-block">
            <h3>Front Matter</h3>
            <div v-if="frontMatterEntries.length === 0" class="panel-empty small">无 front matter</div>
            <div v-else class="kv-list">
              <div v-for="[k, v] in frontMatterEntries" :key="k" class="kv-item">
                <span class="kv-key">{{ k }}</span>
                <span class="kv-value">{{ stringify(v) }}</span>
              </div>
            </div>
          </div>

          <div class="detail-block">
            <h3>章节</h3>
            <div
              v-for="(section, idx) in skillStore.selectedSkillDetail.sections"
              :key="`${section.heading}-${idx}`"
              class="section-item"
            >
              <h4>{{ section.heading }}</h4>
              <pre>{{ section.content }}</pre>
            </div>
            <div v-if="skillStore.selectedSkillDetail.sections.length === 0" class="panel-empty small">
              无章节
            </div>
          </div>
        </template>
      </main>

      <aside class="skills-reference">
        <div class="panel-title">引用内容</div>
        <div v-if="!skillStore.selectedSkillDetail" class="panel-empty">
          请选择 skill 后查看引用
        </div>
        <template v-else>
          <div class="reference-list">
            <button
              v-for="ref in skillStore.selectedSkillDetail.references"
              :key="ref.refId"
              class="reference-item"
              :class="{ active: skillStore.selectedReference?.refId === ref.refId }"
              @click="openReference(ref)"
            >
              <div class="reference-path">{{ ref.relativePath }}</div>
              <div class="reference-meta">{{ ref.category }} · {{ formatBytes(ref.size) }}</div>
            </button>
            <div v-if="skillStore.selectedSkillDetail.references.length === 0" class="panel-empty small">
              无引用文件
            </div>
          </div>

          <div class="reference-content">
            <div v-if="skillStore.loadingReference" class="panel-empty small">加载引用内容中...</div>
            <div v-else-if="skillStore.selectedReferenceContent">
              <div class="reference-content-head">
                <span>{{ skillStore.selectedReferenceContent.contentType }}</span>
                <span>{{ formatBytes(skillStore.selectedReferenceContent.size) }}</span>
              </div>
              <pre>{{ skillStore.selectedReferenceContent.content }}</pre>
              <div v-if="skillStore.selectedReferenceContent.truncated" class="truncate-tip">
                内容已截断，请按需缩小读取范围。
              </div>
            </div>
            <div v-else class="panel-empty small">请选择一个引用文件</div>
          </div>
        </template>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useSkillStore } from '@/stores/skill'
import type { SkillReference } from '@/types/skill'

const skillStore = useSkillStore()
const searchText = ref('')

const frontMatterEntries = computed(() => {
  const detail = skillStore.selectedSkillDetail
  if (!detail || !detail.frontMatter) {
    return [] as Array<[string, unknown]>
  }
  return Object.entries(detail.frontMatter)
})

onMounted(async () => {
  await skillStore.loadSkills()
})

async function refresh() {
  await skillStore.loadSkills(searchText.value)
}

async function reindex() {
  const result = await skillStore.reindex()
  alert(`重建完成：共 ${result.count} 个 skills，来自 ${result.roots} 个根目录`)
}

async function openSkill(skillId: string) {
  await skillStore.selectSkill(skillId)
}

async function openReference(ref: SkillReference) {
  await skillStore.openReference(ref)
}

function stringify(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<style scoped>
.skills-page {
  padding: 20px;
}

.skills-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.skills-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.skills-header p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
}

.skills-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skills-search {
  width: 220px;
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
}

.btn {
  border: 0;
  border-radius: 10px;
  height: 38px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #0f766e, #0ea5e9);
  color: #f8fafc;
}

.btn-secondary {
  background: #e2e8f0;
  color: #0f172a;
}

.skills-error {
  margin-bottom: 12px;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fee2e2;
  color: #991b1b;
  font-size: 13px;
}

.skills-grid {
  display: grid;
  grid-template-columns: 280px 1fr 360px;
  gap: 14px;
  min-height: calc(100vh - 220px);
}

.skills-list,
.skills-detail,
.skills-reference {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 12px;
  background: #f8fafc;
}

.panel-empty {
  padding: 20px 12px;
  text-align: center;
  color: #64748b;
  font-size: 13px;
}

.panel-empty.small {
  padding: 10px 12px;
}

.skill-item {
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  padding: 10px 12px;
  cursor: pointer;
  background: transparent;
}

.skill-item:hover {
  background: #f8fafc;
}

.skill-item.active {
  background: #ecfeff;
}

.skill-item-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.skill-item-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #475569;
}

.skill-item-trigger {
  margin-top: 3px;
  font-size: 11px;
  color: #0891b2;
}

.skills-detail {
  overflow-y: auto;
}

.detail-basic {
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.detail-basic h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #0f172a;
}

.detail-basic code {
  font-size: 12px;
  color: #334155;
  background: #f1f5f9;
  border-radius: 6px;
  padding: 4px 6px;
}

.detail-block {
  padding: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.detail-block h3 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #334155;
}

.kv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kv-item {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px;
}

.kv-key {
  font-size: 12px;
  color: #0f172a;
  font-weight: 600;
}

.kv-value {
  font-size: 12px;
  color: #475569;
  word-break: break-all;
}

.section-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  margin-bottom: 10px;
  overflow: hidden;
}

.section-item h4 {
  margin: 0;
  padding: 8px 10px;
  font-size: 13px;
  color: #0f172a;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.section-item pre {
  margin: 0;
  padding: 10px;
  font-size: 12px;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.skills-reference {
  overflow: hidden;
}

.reference-list {
  overflow-y: auto;
  border-bottom: 1px solid #e2e8f0;
  max-height: 42%;
}

.reference-item {
  width: 100%;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  padding: 9px 12px;
  background: transparent;
  cursor: pointer;
}

.reference-item:hover {
  background: #f8fafc;
}

.reference-item.active {
  background: #ecfeff;
}

.reference-path {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
  word-break: break-all;
}

.reference-meta {
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}

.reference-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
}

.reference-content-head {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 8px;
}

.reference-content pre {
  margin: 0;
  padding: 10px;
  border-radius: 10px;
  background: #0f172a;
  color: #f8fafc;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.truncate-tip {
  margin-top: 8px;
  font-size: 11px;
  color: #b45309;
}

@media (max-width: 1280px) {
  .skills-grid {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .reference-list {
    max-height: 220px;
  }

  .skills-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .skills-header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .skills-search {
    width: 100%;
  }
}
</style>

