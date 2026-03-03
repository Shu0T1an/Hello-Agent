<template>
  <section class="channel-page">
    <header class="channel-header">
      <div class="header-copy">
        <h2>渠道管理</h2>
        <p>统一管理 DingTalk 渠道配置，支持卡片快捷启停与侧边抽屉编辑。</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
        <button class="btn btn-primary" @click="openCreateDrawer">新增渠道</button>
      </div>
    </header>

    <p v-if="error" class="channel-error">{{ error }}</p>

    <div v-if="loading" class="channel-grid">
      <article v-for="n in 3" :key="n" class="channel-card skeleton-card">
        <div class="skeleton-line w-28" />
        <div class="skeleton-line w-20" />
        <div class="skeleton-line w-full" />
        <div class="skeleton-line w-24" />
      </article>
    </div>

    <div v-else-if="channels.length === 0" class="state-card">
      <h3>暂无渠道配置</h3>
      <p>先创建一个 DingTalk 渠道，随后填写 Client ID 和 Client Secret 即可接入。</p>
      <button class="btn btn-primary" @click="openCreateDrawer">立即创建</button>
    </div>

    <div v-else class="channel-grid">
      <article
        v-for="item in channels"
        :key="item.id"
        class="channel-card"
        :class="{ active: drawerOpen && editingId === item.id }"
        @click="openEditDrawer(item)"
      >
        <div class="card-top">
          <div class="card-status">
            <span class="status-dot" :class="{ on: Boolean(item.enabled) }" />
            <span>{{ item.enabled ? "已启用" : "已停用" }}</span>
          </div>
          <span class="channel-type-chip">{{ formatChannelType(item.channelType) }}</span>
        </div>

        <h3>{{ item.channelName }}</h3>
        <p class="card-meta">机器人前缀: {{ getBotPrefix(item) || "未设置" }}</p>
        <p class="card-hint">点击卡片进行编辑</p>

        <div class="card-footer">
          <span class="updated-at">{{ formatUpdatedAt(item.updatedAt || item.createdAt) }}</span>
          <button class="btn btn-ghost btn-xs" :disabled="saving" @click.stop="toggle(item)">
            {{ item.enabled ? "停用" : "启用" }}
          </button>
        </div>
      </article>
    </div>

    <Transition name="drawer-fade">
      <div v-if="drawerOpen" class="drawer-mask" @click="closeDrawer" />
    </Transition>

    <Transition name="drawer-slide">
      <aside v-if="drawerOpen" class="drawer-panel" role="dialog" aria-modal="true">
        <header class="drawer-header">
          <div class="drawer-title">
            <h3>{{ drawerTitle }}</h3>
            <a class="doc-link" href="https://open.dingtalk.com/document/" target="_blank" rel="noopener noreferrer">
              DingTalk Doc
            </a>
          </div>
          <button class="icon-btn close-btn" type="button" @click="closeDrawer">✕</button>
        </header>

        <form class="drawer-body" @submit.prevent="save">
          <label class="field">
            <span>渠道名称</span>
            <input v-model="form.channelName" placeholder="例如: DingTalk" />
          </label>

          <label class="field">
            <span>渠道类型</span>
            <input :value="formatChannelType(form.channelType)" disabled />
          </label>

          <label class="field switch-field">
            <span>Enabled</span>
            <button
              class="switch-btn"
              :class="{ on: form.enabled }"
              type="button"
              :aria-checked="form.enabled"
              @click="form.enabled = !form.enabled"
            >
              <span class="switch-dot" />
            </button>
          </label>

          <label class="field">
            <span>Bot Prefix</span>
            <input v-model="form.botPrefix" placeholder="@bot" />
          </label>

          <label class="field">
            <span>Client ID</span>
            <input v-model="form.clientId" placeholder="请输入 Client ID" />
          </label>

          <label class="field">
            <span>Client Secret</span>
            <div class="secret-input-wrap">
              <input
                v-model="form.clientSecret"
                :type="showSecret ? 'text' : 'password'"
                placeholder="请输入 Client Secret"
              />
              <button class="icon-btn icon-inline" type="button" @click="showSecret = !showSecret">
                {{ showSecret ? "隐藏" : "显示" }}
              </button>
            </div>
          </label>

          <p class="field-tip">Client ID 和 Client Secret 为必填项。</p>

          <footer class="drawer-footer">
            <button class="btn btn-ghost" type="button" @click="closeDrawer">取消</button>
            <button class="btn btn-primary" type="submit" :disabled="saving || !isFormValid">
              {{ saving ? "保存中..." : "保存" }}
            </button>
          </footer>
        </form>
      </aside>
    </Transition>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { storeToRefs } from "pinia"
import { useChannelStore } from "@/stores/channel"
import type { ChannelConfig, CreateChannelRequest, UpdateChannelRequest } from "@/types/channel"

interface ChannelEditorForm {
  channelName: string
  channelType: string
  enabled: boolean
  botPrefix: string
  clientId: string
  clientSecret: string
}

const channelStore = useChannelStore()
const { channels, error, loading, saving } = storeToRefs(channelStore)

const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const showSecret = ref(false)
const form = reactive<ChannelEditorForm>({
  channelName: "",
  channelType: "dingtalk",
  enabled: true,
  botPrefix: "",
  clientId: "",
  clientSecret: ""
})

const isFormValid = computed(() => {
  return (
    form.channelName.trim().length > 0 &&
    form.clientId.trim().length > 0 &&
    form.clientSecret.trim().length > 0
  )
})

const drawerTitle = computed(() => (editingId.value ? "DingTalk 设置" : "新增 DingTalk 渠道"))

onMounted(load)

async function load() {
  try {
    await channelStore.fetchChannels()
  } catch (e) {
    console.error(e)
  }
}

function openCreateDrawer() {
  editingId.value = null
  resetForm()
  drawerOpen.value = true
}

function openEditDrawer(item: ChannelConfig) {
  editingId.value = item.id ?? null
  form.channelName = item.channelName
  form.channelType = item.channelType || "dingtalk"
  form.enabled = Boolean(item.enabled)
  form.botPrefix = readConfigText(item, ["botPrefix"])
  form.clientId = readConfigText(item, ["clientId", "appKey"])
  form.clientSecret = readConfigText(item, ["clientSecret", "appSecret"])
  showSecret.value = false
  drawerOpen.value = true
}

function closeDrawer() {
  drawerOpen.value = false
}

async function save() {
  if (!isFormValid.value) {
    return
  }

  const clientId = form.clientId.trim()
  const clientSecret = form.clientSecret.trim()
  const config: Record<string, unknown> = {
    clientId,
    clientSecret,
    // 与历史字段保持兼容，便于旧链路读取
    appKey: clientId,
    appSecret: clientSecret
  }
  if (form.botPrefix.trim()) {
    config.botPrefix = form.botPrefix.trim()
  }

  const payload: CreateChannelRequest = {
    channelName: form.channelName.trim(),
    channelType: form.channelType,
    enabled: form.enabled,
    status: form.enabled ? "running" : "stopped",
    config
  }

  try {
    if (editingId.value) {
      await channelStore.updateChannel(editingId.value, payload as UpdateChannelRequest)
    } else {
      await channelStore.createChannel(payload)
    }
    drawerOpen.value = false
  } catch (e) {
    console.error(e)
  }
}

async function toggle(item: ChannelConfig) {
  if (!item.id) return
  try {
    await channelStore.toggleChannel(item.id, !item.enabled)
  } catch (e) {
    console.error(e)
  }
}

function resetForm() {
  form.channelName = ""
  form.channelType = "dingtalk"
  form.enabled = true
  form.botPrefix = ""
  form.clientId = ""
  form.clientSecret = ""
  showSecret.value = false
}

function formatChannelType(type: string | undefined) {
  if (!type) return "Unknown"
  return type.charAt(0).toUpperCase() + type.slice(1)
}

function getBotPrefix(item: ChannelConfig): string {
  return readConfigText(item, ["botPrefix"])
}

function readConfigText(item: ChannelConfig, keys: string[]): string {
  const config = parseConfigObject(item.config)
  for (const key of keys) {
    const value = config[key]
    if (typeof value === "string" && value.trim()) {
      return value.trim()
    }
  }
  return ""
}

function parseConfigObject(config: unknown): Record<string, unknown> {
  if (config && typeof config === "object" && !Array.isArray(config)) {
    return config as Record<string, unknown>
  }
  if (typeof config === "string" && config.trim()) {
    try {
      const parsed = JSON.parse(config)
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>
      }
    } catch {
      return {}
    }
  }
  return {}
}

function formatUpdatedAt(dateText?: string) {
  if (!dateText) {
    return "最近更新: 无"
  }
  const date = new Date(dateText)
  if (Number.isNaN(date.getTime())) {
    return "最近更新: 无"
  }
  return `最近更新: ${date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  })}`
}
</script>

<style scoped>
.channel-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  color: var(--color-text-primary);
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.channel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.header-copy h2 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-strong);
}

.header-copy p {
  margin: 8px 0 0;
  color: var(--text-weak);
  font-size: 14px;
  line-height: 1.5;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.channel-error {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fecaca;
  background: #fff1f2;
  color: #9f1239;
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 14px;
}

.channel-card {
  border-radius: 14px;
  border: 2px solid #d8dfef;
  background: #ffffff;
  padding: 18px;
  cursor: pointer;
  transition: border-color var(--transition-normal), box-shadow var(--transition-normal), transform var(--transition-normal);
}

.channel-card:hover {
  border-color: #3769cc;
  box-shadow: 0 14px 26px -24px rgba(15, 24, 39, 0.58);
  transform: translateY(-1px);
}

.channel-card.active {
  border-color: #1d4ed8;
  box-shadow: 0 18px 28px -22px rgba(29, 78, 216, 0.45);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.card-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #5b677d;
  font-size: 14px;
  font-weight: 500;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #9ca3af;
}

.status-dot.on {
  background: #22c55e;
}

.channel-type-chip {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  background: #e6edf9;
}

.channel-card h3 {
  margin: 0 0 10px;
  font-size: 42px;
  line-height: 1.12;
  letter-spacing: -0.02em;
  color: #273248;
}

.card-meta {
  margin: 0;
  color: #6b7280;
  font-size: 20px;
}

.card-hint {
  margin: 6px 0 0;
  color: #9ca3af;
  font-size: 18px;
}

.card-footer {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.updated-at {
  color: #8a94a9;
  font-size: 12px;
}

.state-card {
  border: 1px dashed #cdd6e6;
  border-radius: 14px;
  background: #f9fbff;
  padding: 30px;
}

.state-card h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.state-card p {
  margin: 8px 0 16px;
  color: #64748b;
}

.skeleton-card {
  pointer-events: none;
}

.skeleton-line {
  height: 12px;
  border-radius: 999px;
  margin-bottom: 12px;
  background: linear-gradient(90deg, #edf2fa 0%, #dce6f4 50%, #edf2fa 100%);
  background-size: 220% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

.w-20 {
  width: 80px;
}

.w-24 {
  width: 96px;
}

.w-28 {
  width: 112px;
}

.w-full {
  width: 100%;
}

.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(1px);
  z-index: 120;
}

.drawer-panel {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: min(560px, 100vw);
  background: #ffffff;
  border-left: 1px solid #d7deef;
  z-index: 121;
  display: flex;
  flex-direction: column;
}

.drawer-header {
  padding: 22px 24px 18px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.drawer-title h3 {
  margin: 0;
  color: #24324a;
  font-size: 22px;
}

.doc-link {
  text-decoration: none;
  color: #1d4ed8;
  font-size: 14px;
  font-weight: 600;
}

.drawer-body {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field > span {
  font-size: 14px;
  color: #334155;
  font-weight: 600;
}

.field input {
  width: 100%;
  border: 1px solid #d2d9e8;
  border-radius: 12px;
  padding: 11px 14px;
  font-size: 14px;
  color: #1f2937;
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), background-color var(--transition-fast);
}

.field input::placeholder {
  color: #a4adc0;
}

.field input:focus {
  border-color: #1d4ed8;
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.2);
}

.field input:disabled {
  background: #f5f7fb;
  color: #6b7280;
}

.switch-field {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.switch-btn {
  width: 56px;
  height: 34px;
  border: none;
  border-radius: 999px;
  padding: 4px;
  background: #d2dae8;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.switch-btn.on {
  background: #5a67d8;
}

.switch-dot {
  display: block;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #ffffff;
  transition: transform var(--transition-fast);
}

.switch-btn.on .switch-dot {
  transform: translateX(22px);
}

.secret-input-wrap {
  position: relative;
}

.icon-inline {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
}

.field-tip {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.drawer-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  border: 1px solid transparent;
  border-radius: 10px;
  height: 40px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-xs {
  height: 32px;
  padding: 0 11px;
  font-size: 12px;
}

.btn-primary {
  background: linear-gradient(120deg, #2153d1, #0f766e);
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  filter: brightness(1.03);
}

.btn-ghost {
  background: #ffffff;
  border-color: #d2dae8;
  color: #334155;
}

.btn-ghost:hover:not(:disabled) {
  background: #f8fafc;
}

.icon-btn {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  border-radius: 8px;
  font-size: 14px;
  padding: 6px 8px;
}

.icon-btn:hover {
  background: #f1f5f9;
}

.close-btn {
  font-size: 20px;
  line-height: 1;
}

.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity var(--transition-fast);
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform var(--transition-normal);
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(100%);
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

@media (max-width: 900px) {
  .channel-page {
    padding: 16px;
  }

  .channel-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 640px) {
  .channel-grid {
    grid-template-columns: 1fr;
  }

  .drawer-body {
    padding: 16px;
  }

  .drawer-header {
    padding: 16px;
  }

  .drawer-title h3 {
    font-size: 18px;
  }

  .field > span {
    font-size: 13px;
  }
}
</style>
