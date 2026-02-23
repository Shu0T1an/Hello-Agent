<template>
  <div class="session-audit h-full flex flex-col overflow-hidden">
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="flex flex-col items-center gap-4 text-zinc-400">
        <Loader :size="40" class="animate-spin" />
        <p class="text-sm">加载审计数据中...</p>
      </div>
    </div>

    <div v-else-if="error" class="flex-1 flex items-center justify-center p-6">
      <BaseEmpty
        type="error"
        :title="'加载失败'"
        :description="error"
        :action-text="'重试'"
        @action="$emit('retry')"
      />
    </div>

    <div v-else-if="!audits || audits.records.length === 0" class="flex-1 flex items-center justify-center p-6">
      <BaseEmpty
        type="inbox"
        :title="'暂无审计数据'"
        :description="'该会话尚未产生可展示的模型调用审计记录'"
      />
    </div>

    <div v-else class="flex-1 overflow-y-auto custom-scrollbar-glass">
      <div class="max-w-5xl mx-auto p-6 space-y-4">
        <div class="glass-card rounded-xl p-4 flex items-center justify-between gap-4 flex-wrap">
          <div class="flex items-center gap-2">
            <Shield :size="18" class="text-emerald-600" />
            <h2 class="text-base font-semibold text-zinc-900">会话审计</h2>
          </div>
          <div class="text-sm text-zinc-600 flex items-center gap-3">
            <span>总计: <strong class="text-zinc-900">{{ audits.total }}</strong></span>
            <span>展示: <strong class="text-zinc-900">{{ audits.records.length }}</strong></span>
            <span>Limit: <strong class="text-zinc-900">{{ audits.limit }}</strong></span>
          </div>
        </div>

        <article
          v-for="record in audits.records"
          :key="record.id ?? `${record.traceId}-${record.phase}-${record.createdAt}`"
          class="glass-card rounded-xl p-4 space-y-3"
        >
          <header class="flex items-start justify-between gap-3 flex-wrap">
            <div class="flex items-center gap-2 flex-wrap">
              <span :class="['phase-badge', phaseClass(record.phase)]">{{ record.phase }}</span>
              <code class="meta-code">{{ record.agentName || 'unknown-agent' }}</code>
              <code class="meta-code">{{ record.traceId }}</code>
            </div>
            <span class="text-xs text-zinc-500">{{ formatDateTime(record.createdAt) }}</span>
          </header>

          <div class="grid md:grid-cols-2 gap-2 text-xs text-zinc-600">
            <div>
              <span class="text-zinc-500">Session:</span>
              <code class="meta-code">{{ record.sessionId || '-' }}</code>
            </div>
            <div>
              <span class="text-zinc-500">Execution:</span>
              <code class="meta-code">{{ record.executionId || '-' }}</code>
            </div>
          </div>

          <div v-if="record.requestJson" class="payload-block">
            <button class="toggle-btn" @click="togglePayload(payloadKey(record, 'request'))">
              {{ isPayloadExpanded(payloadKey(record, 'request')) ? '收起请求 JSON' : '展开请求 JSON' }}
            </button>
            <pre v-if="isPayloadExpanded(payloadKey(record, 'request'))" class="payload-pre">{{ formatJSON(record.requestJson) }}</pre>
          </div>

          <div v-if="record.responseJson" class="payload-block">
            <button class="toggle-btn" @click="togglePayload(payloadKey(record, 'response'))">
              {{ isPayloadExpanded(payloadKey(record, 'response')) ? '收起响应 JSON' : '展开响应 JSON' }}
            </button>
            <pre v-if="isPayloadExpanded(payloadKey(record, 'response'))" class="payload-pre">{{ formatJSON(record.responseJson) }}</pre>
          </div>

          <div v-if="record.errorMessage" class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {{ record.errorMessage }}
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Loader, Shield } from 'lucide-vue-next'
import BaseEmpty from '@/components/base/BaseEmpty.vue'
import type { SessionAuditData, SessionAuditRecord } from '@/types/session-audit'
import { formatDateTime, formatJSON } from '@/utils/helpers'

interface Props {
  sessionId: string
  loading?: boolean
  audits?: SessionAuditData | null
  error?: string | null
}

withDefaults(defineProps<Props>(), {
  loading: false,
  audits: null,
  error: null
})

defineEmits<{
  retry: []
}>()

const expandedPayloadKeys = ref<string[]>([])

function payloadKey(record: SessionAuditRecord, part: 'request' | 'response'): string {
  const base = record.id ?? `${record.traceId}-${record.createdAt}`
  return `${base}-${part}`
}

function togglePayload(key: string) {
  if (expandedPayloadKeys.value.includes(key)) {
    expandedPayloadKeys.value = expandedPayloadKeys.value.filter(item => item !== key)
  } else {
    expandedPayloadKeys.value = [...expandedPayloadKeys.value, key]
  }
}

function isPayloadExpanded(key: string): boolean {
  return expandedPayloadKeys.value.includes(key)
}

function phaseClass(phase: string): string {
  if (phase === 'REQUEST') return 'phase-request'
  if (phase === 'RESPONSE') return 'phase-response'
  if (phase === 'ERROR') return 'phase-error'
  return 'phase-default'
}
</script>

<style scoped>
.session-audit {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.meta-code {
  padding: 0.12rem 0.45rem;
  border-radius: 0.45rem;
  background: rgba(15, 23, 42, 0.06);
  font-size: 0.72rem;
}

.phase-badge {
  border-radius: 999px;
  padding: 0.12rem 0.5rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.phase-request {
  color: #0e7490;
  background: #ecfeff;
}

.phase-response {
  color: #166534;
  background: #f0fdf4;
}

.phase-error {
  color: #b91c1c;
  background: #fef2f2;
}

.phase-default {
  color: #52525b;
  background: #f4f4f5;
}

.payload-block {
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 0.75rem;
  overflow: hidden;
}

.toggle-btn {
  width: 100%;
  text-align: left;
  padding: 0.5rem 0.7rem;
  font-size: 0.78rem;
  color: #334155;
  background: rgba(148, 163, 184, 0.08);
  border: none;
}

.toggle-btn:hover {
  background: rgba(148, 163, 184, 0.16);
}

.payload-pre {
  margin: 0;
  padding: 0.8rem;
  overflow: auto;
  max-height: 360px;
  font-size: 0.75rem;
  line-height: 1.45;
  color: #0f172a;
  background: rgba(15, 23, 42, 0.03);
}
</style>
