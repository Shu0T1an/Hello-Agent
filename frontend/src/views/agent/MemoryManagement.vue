<template>
  <div class="memory-page">
    <header class="memory-header">
      <div>
        <h1>Memory 规则看板</h1>
        <p>编辑全局 <code>memory.md</code>，用于模型请求前的只读规则注入。</p>
      </div>
      <div class="memory-actions">
        <button class="btn btn-secondary" :disabled="memoryStore.loading" @click="reload">
          刷新
        </button>
        <button
          class="btn btn-secondary"
          :disabled="!memoryStore.isDirty || memoryStore.saving"
          @click="reset"
        >
          重置
        </button>
        <button
          class="btn btn-primary"
          :disabled="!memoryStore.isDirty || memoryStore.saving"
          @click="save"
        >
          {{ memoryStore.saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </header>

    <div v-if="memoryStore.error" class="memory-error">{{ memoryStore.error }}</div>

    <section class="memory-card">
      <div class="memory-meta">
        <div class="meta-item">
          <span class="meta-label">文件路径</span>
          <code class="meta-value">{{ memoryStore.document?.filePath || '-' }}</code>
        </div>
        <div class="meta-item">
          <span class="meta-label">状态</span>
          <span class="meta-value">{{ memoryStore.document?.exists ? '已存在' : '尚未创建' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">字符数</span>
          <span class="meta-value">{{ memoryStore.draftContent.length }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">注入上限</span>
          <span class="meta-value">{{ memoryStore.document?.maxChars ?? '-' }}</span>
        </div>
      </div>

      <div class="editor-wrap">
        <textarea
          v-model="memoryStore.draftContent"
          class="memory-editor"
          spellcheck="false"
          placeholder="在此输入 memory.md 规则内容..."
        />
      </div>

      <div class="memory-footer">
        <span v-if="memoryStore.remainingChars !== null">
          注入剩余字符：{{ memoryStore.remainingChars }}
        </span>
        <span v-if="memoryStore.isDirty" class="dirty-tip">有未保存变更</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useMemoryStore } from '@/stores/memory'

const memoryStore = useMemoryStore()

onMounted(async () => {
  await memoryStore.loadDocument()
})

async function reload() {
  await memoryStore.loadDocument()
}

function reset() {
  memoryStore.resetDraft()
}

async function save() {
  await memoryStore.saveDocument()
}
</script>

<style scoped>
.memory-page {
  padding: 20px;
}

.memory-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.memory-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.memory-header p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
}

.memory-actions {
  display: flex;
  gap: 8px;
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

.memory-error {
  margin-bottom: 12px;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fee2e2;
  color: #991b1b;
  font-size: 13px;
}

.memory-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  overflow: hidden;
}

.memory-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 11px;
  color: #64748b;
}

.meta-value {
  font-size: 13px;
  color: #0f172a;
  word-break: break-all;
}

.editor-wrap {
  padding: 14px;
}

.memory-editor {
  width: 100%;
  min-height: 430px;
  resize: vertical;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  background: #0f172a;
  color: #f8fafc;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px;
}

.memory-footer {
  border-top: 1px solid #e2e8f0;
  padding: 10px 14px;
  display: flex;
  justify-content: space-between;
  color: #475569;
  font-size: 12px;
}

.dirty-tip {
  color: #b45309;
  font-weight: 600;
}

@media (max-width: 1024px) {
  .memory-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .memory-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .memory-meta {
    grid-template-columns: 1fr;
  }
}
</style>
