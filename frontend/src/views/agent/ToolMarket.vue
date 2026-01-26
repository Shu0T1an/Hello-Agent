<template>
  <div class="tool-market">
    <div class="page-header">
      <h1>工具市场</h1>
      <div class="header-actions">
        <button class="btn-secondary" @click="handleScanLocal">
          <span>🔄</span> 扫描本地工具
        </button>
        <button class="btn-primary" @click="handleSyncAllMcp" :disabled="syncing || mcpConnections.length === 0">
          <span>⚡</span> {{ syncing ? '同步中...' : '同步 MCP 工具' }}
        </button>
      </div>
    </div>

    <!-- MCP 连接状态 -->
    <section class="mcp-connections" v-if="mcpConnections.length > 0">
      <h2>MCP 连接</h2>
      <div class="connection-list">
        <div v-for="conn in mcpConnections" :key="conn.name" class="connection-item">
          <div class="connection-info">
            <h3>{{ conn.name }}</h3>
            <p v-if="conn.description">{{ conn.description }}</p>
            <span :class="['status-badge', conn.status.toLowerCase()]">
              {{ getStatusText(conn.status) }}
            </span>
          </div>
          <div class="connection-actions">
            <button
              v-if="conn.status === 'CONNECTED'"
              @click="handleSyncConnection(conn.name)"
              class="btn-secondary btn-sm"
              :disabled="syncing"
            >
              同步工具
            </button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="toolStore.loading && !mcpLoading" class="loading">加载中...</div>
    <div v-else-if="toolStore.error" class="error">{{ toolStore.error }}</div>
    <div v-else>
      <!-- 本地工具 -->
      <section class="tool-section">
        <h2>本地工具</h2>
        <div class="tool-grid">
          <div v-for="tool in toolStore.localTools" :key="tool.id" class="tool-card">
            <div class="tool-header">
              <h3>{{ tool.displayName }}</h3>
              <span :class="['status-badge', tool.isActive ? 'active' : 'inactive']">
                {{ tool.isActive ? '可用' : '禁用' }}
              </span>
            </div>
            <p class="tool-name">{{ tool.toolName }}</p>
            <p class="tool-description">{{ tool.description }}</p>
            <div class="tool-meta">
              <span class="tool-type">LOCAL</span>
              <span v-if="tool.className" class="tool-class">{{ formatClassName(tool.className) }}</span>
            </div>
          </div>
        </div>
        <div v-if="toolStore.localTools.length === 0" class="empty-state">
          暂无本地工具，点击右上角扫描
        </div>
      </section>

      <!-- MCP 工具 -->
      <section class="tool-section">
        <h2>MCP 工具</h2>
        <div class="tool-grid">
          <div v-for="tool in toolStore.mcpTools" :key="tool.id" class="tool-card">
            <div class="tool-header">
              <h3>{{ tool.displayName }}</h3>
              <span :class="['status-badge', tool.isActive ? 'active' : 'inactive']">
                {{ tool.isActive ? '可用' : '禁用' }}
              </span>
            </div>
            <p class="tool-name">{{ tool.toolName }}</p>
            <p class="tool-description">{{ tool.description }}</p>
            <div class="tool-meta">
              <span class="tool-type mcp">MCP</span>
              <span v-if="tool.mcpConnectionName" class="mcp-connection">
                {{ tool.mcpConnectionName }}
              </span>
            </div>
          </div>
        </div>
        <div v-if="toolStore.mcpTools.length === 0 && mcpConnections.length === 0" class="empty-state">
          暂无 MCP 工具，请先在配置文件中添加 MCP 连接
        </div>
        <div v-else-if="toolStore.mcpTools.length === 0" class="empty-state">
          暂无 MCP 工具，点击上方"同步 MCP 工具"按钮
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToolDefinitionStore } from '@/stores/toolDefinition'
import * as mcpApi from '@/api/mcp'

const toolStore = useToolDefinitionStore()

const mcpConnections = ref<mcpApi.McpConnection[]>([])
const mcpLoading = ref(false)
const syncing = ref(false)

onMounted(async () => {
  await Promise.all([
    toolStore.fetchTools(),
    loadMcpConnections()
  ])
})

async function loadMcpConnections() {
  mcpLoading.value = true
  try {
    mcpConnections.value = await mcpApi.fetchConnections()
  } catch (error) {
    console.error('Failed to load MCP connections:', error)
  } finally {
    mcpLoading.value = false
  }
}

async function handleScanLocal() {
  try {
    const result = await toolStore.scanLocalTools()
    alert(`扫描完成，发现 ${result.count} 个工具`)
  } catch (error) {
    console.error('Failed to scan local tools:', error)
    alert('扫描失败，请重试')
  }
}

async function handleSyncConnection(connectionName: string) {
  syncing.value = true
  try {
    const result = await mcpApi.syncMcpTools(connectionName)
    alert(`${connectionName}: 同步完成，发现 ${result.count} 个工具`)
    await toolStore.fetchTools()
  } catch (error) {
    console.error('Failed to sync MCP tools:', error)
    alert('同步失败，请重试')
  } finally {
    syncing.value = false
  }
}

async function handleSyncAllMcp() {
  syncing.value = true
  try {
    const result = await mcpApi.syncAllMcpTools()
    alert(`同步完成！从 ${result.connections} 个连接同步了 ${result.total} 个工具`)
    await toolStore.fetchTools()
  } catch (error) {
    console.error('Failed to sync all MCP tools:', error)
    alert('同步失败，请重试')
  } finally {
    syncing.value = false
  }
}

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'CONNECTED': '已连接',
    'DISCONNECTED': '未连接',
    'ERROR': '错误'
  }
  return statusMap[status] || status
}

function formatClassName(className?: string): string {
  if (!className) return ''
  return className.split('.').pop() || className
}
</script>

<style scoped>
.tool-market {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.btn-secondary {
  padding: 10px 20px;
  background: #f3f4f6;
  color: #374151;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-secondary:hover {
  background: #e5e7eb;
}

.btn-primary {
  padding: 10px 20px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-primary:hover:not(:disabled) {
  background: #4f46e5;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 13px;
}

.mcp-connections {
  margin-bottom: 32px;
  padding: 20px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.mcp-connections h2 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #111827;
}

.connection-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.connection-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.connection-info h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #111827;
}

.connection-info p {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 8px 0;
}

.status-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  display: inline-block;
}

.status-badge.connected {
  background: #dcfce7;
  color: #166534;
}

.status-badge.disconnected {
  background: #f3f4f6;
  color: #6b7280;
}

.status-badge.error {
  background: #fee2e2;
  color: #991b1b;
}

.loading, .error {
  text-align: center;
  padding: 40px;
  color: #6b7280;
}

.error {
  color: #ef4444;
}

.tool-section {
  margin-bottom: 40px;
}

.tool-section h2 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #111827;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.tool-card {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  transition: box-shadow 0.2s;
}

.tool-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.tool-header h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  flex: 1;
}

.status-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.status-badge.active {
  background: #dcfce7;
  color: #166534;
}

.status-badge.inactive {
  background: #f3f4f6;
  color: #6b7280;
}

.tool-name {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 8px 0;
  font-family: monospace;
}

.tool-description {
  font-size: 14px;
  color: #374151;
  margin: 0 0 16px 0;
  line-height: 1.5;
  min-height: 42px;
}

.tool-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tool-type {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: #dbeafe;
  color: #1d4ed8;
}

.tool-type.mcp {
  background: #f3e8ff;
  color: #7c3aed;
}

.tool-class, .mcp-connection {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  background: #f3f4f6;
  color: #4b5563;
  font-family: monospace;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #9ca3af;
  border: 2px dashed #e5e7eb;
  border-radius: 12px;
}
</style>
