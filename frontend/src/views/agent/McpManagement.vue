<template>
  <div class="mcp-management">
    <div class="page-header">
      <h1>MCP 连接管理</h1>
      <button class="btn-primary" @click="showAddForm = true">
        <span>+</span> 添加连接
      </button>
    </div>

    <!-- MCP 连接列表 -->
    <div class="connection-list">
      <div v-for="conn in connections" :key="conn.name" class="connection-card">
        <div class="connection-header">
          <div class="connection-info">
            <h3>{{ conn.name }}</h3>
            <p v-if="conn.description">{{ conn.description }}</p>
          </div>
          <span :class="['status-badge', conn.status.toLowerCase()]">
            {{ getStatusText(conn.status) }}
          </span>
        </div>

        <div class="connection-details">
          <div class="detail-item">
            <label>类型:</label>
            <span>{{ conn.config.type }}</span>
          </div>
          <div v-if="conn.config.command" class="detail-item">
            <label>命令:</label>
            <code>{{ conn.config.command }} {{ (conn.config.args || []).join(' ') }}</code>
          </div>
          <div v-if="conn.config.url" class="detail-item">
            <label>URL:</label>
            <span>{{ conn.config.url }}</span>
          </div>
          <div v-if="conn.toolCount !== undefined" class="detail-item">
            <label>工具数量:</label>
            <span>{{ conn.toolCount }}</span>
          </div>
        </div>

        <div class="connection-actions">
          <button
            v-if="conn.status === 'CONNECTED'"
            @click="handleSync(conn.name)"
            class="btn-secondary"
            :disabled="syncing"
          >
            同步工具
          </button>
          <button
            v-if="conn.status === 'DISCONNECTED' || conn.status === 'ERROR'"
            @click="handleConnect(conn.name)"
            class="btn-success"
          >
            连接
          </button>
          <button
            v-if="conn.status === 'CONNECTED'"
            @click="handleDisconnect(conn.name)"
            class="btn-warning"
          >
            断开
          </button>
          <button @click="handleDelete(conn.name)" class="btn-danger">
            删除
          </button>
        </div>
      </div>

      <div v-if="connections.length === 0" class="empty-state">
        <p>暂无 MCP 连接，点击右上角添加</p>
      </div>
    </div>

    <!-- 添加连接表单对话框 -->
    <div v-if="showAddForm" class="modal-overlay" @click.self="showAddForm = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>添加 MCP 连接</h2>
          <button @click="showAddForm = false" class="btn-close">×</button>
        </div>
        <form @submit.prevent="handleAddConnection" class="connection-form">
          <div class="form-group">
            <label>连接名称 *</label>
            <input
              v-model="formData.name"
              type="text"
              required
              placeholder="my-mcp-server"
              pattern="[a-z0-9-]+"
              title="只能使用小写字母、数字和连字符"
            />
            <small>唯一标识，只能使用小写字母、数字和连字符</small>
          </div>

          <div class="form-group">
            <label>描述</label>
            <input
              v-model="formData.description"
              type="text"
              placeholder="描述这个 MCP 连接的用途"
            />
          </div>

          <div class="form-group">
            <label>连接类型 *</label>
            <select v-model="formData.type" required>
              <option value="">请选择类型</option>
              <option value="STDIO">STDIO (标准输入输出)</option>
              <option value="SSE">SSE (Server-Sent Events)</option>
            </select>
          </div>

          <!-- STDIO 类型配置 -->
          <template v-if="formData.type === 'STDIO'">
            <div class="form-group">
              <label>命令 *</label>
              <input
                v-model="formData.command"
                type="text"
                required
                placeholder="npx"
              />
              <small>例如: npx, node, python 等</small>
            </div>

            <div class="form-group">
              <label>参数</label>
              <input
                v-model="formData.args"
                type="text"
                placeholder="-y @amap/amap-maps-mcp-server"
              />
              <small>多个参数用空格分隔</small>
            </div>

            <div class="form-group">
              <label>环境变量</label>
              <textarea
                v-model="formData.env"
                rows="3"
                placeholder="API_KEY=your_key&#10;ANOTHER_VAR=value"
              ></textarea>
              <small>每行一个变量，格式: KEY=VALUE</small>
            </div>
          </template>

          <!-- SSE 类型配置 -->
          <template v-if="formData.type === 'SSE'">
            <div class="form-group">
              <label>服务 URL *</label>
              <input
                v-model="formData.url"
                type="url"
                required
                placeholder="https://example.com/mcp/sse"
              />
            </div>
          </template>

          <div class="form-actions">
            <button type="button" @click="showAddForm = false" class="btn-secondary">
              取消
            </button>
            <button type="submit" class="btn-primary" :disabled="submitting">
              {{ submitting ? '添加中...' : '添加连接' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as mcpApi from '@/api/mcp'

const connections = ref<mcpApi.McpConnection[]>([])
const loading = ref(false)
const syncing = ref(false)
const showAddForm = ref(false)
const submitting = ref(false)

const formData = ref({
  name: '',
  description: '',
  type: '',
  command: '',
  args: '',
  env: '',
  url: ''
})

onMounted(() => {
  loadConnections()
})

async function loadConnections() {
  loading.value = true
  try {
    connections.value = await mcpApi.fetchConnections()
  } catch (error) {
    console.error('Failed to load connections:', error)
    alert('加载连接失败')
  } finally {
    loading.value = false
  }
}

async function handleAddConnection() {
  submitting.value = true
  try {
    // 构建后端期望的请求格式
    const requestData: mcpApi.CreateMcpConnectionRequest = {
      name: formData.value.name,
      description: formData.value.description || undefined,
      type: formData.value.type as mcpApi.McpConnectionType
    }

    // 根据类型添加对应配置
    if (formData.value.type === 'STDIO') {
      requestData.command = formData.value.command
      requestData.args = formData.value.args ? formData.value.args.split(' ') : []
      if (formData.value.env) {
        requestData.env = parseEnvVars(formData.value.env)
      }
    } else if (formData.value.type === 'SSE') {
      requestData.sseUrl = formData.value.url
    }

    await mcpApi.createConnection(requestData)

    showAddForm.value = false
    resetForm()
    await loadConnections()
    alert('连接添加成功')
  } catch (error: any) {
    console.error('Failed to add connection:', error)
    alert(error.message || '添加失败')
  } finally {
    submitting.value = false
  }
}

async function handleConnect(name: string) {
  try {
    await mcpApi.connectConnection(name)
    await loadConnections()
    alert('连接成功')
  } catch (error) {
    console.error('Failed to connect:', error)
    alert('连接失败')
  }
}

async function handleDisconnect(name: string) {
  try {
    await mcpApi.disconnectConnection(name)
    await loadConnections()
    alert('已断开连接')
  } catch (error) {
    console.error('Failed to disconnect:', error)
    alert('断开失败')
  }
}

async function handleDelete(name: string) {
  if (!confirm(`确定删除连接 "${name}"？`)) return

  try {
    await mcpApi.deleteConnection(name)
    await loadConnections()
    alert('删除成功')
  } catch (error) {
    console.error('Failed to delete:', error)
    alert('删除失败')
  }
}

async function handleSync(name: string) {
  syncing.value = true
  try {
    const result = await mcpApi.syncMcpTools(name)
    alert(`${name}: 同步完成，发现 ${result.count} 个工具`)
  } catch (error) {
    console.error('Failed to sync:', error)
    alert('同步失败')
  } finally {
    syncing.value = false
  }
}

function parseEnvVars(envStr: string): Record<string, string> {
  const env: Record<string, string> = {}
  envStr.split('\n').forEach(line => {
    const [key, ...valueParts] = line.split('=')
    if (key && valueParts.length > 0) {
      env[key.trim()] = valueParts.join('=').trim()
    }
  })
  return env
}

function resetForm() {
  formData.value = {
    name: '',
    description: '',
    type: '',
    command: '',
    args: '',
    env: '',
    url: ''
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
</script>

<style scoped>
.mcp-management {
  padding: 20px;
  max-width: 1000px;
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

.connection-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.connection-card {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
}

.connection-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.connection-info h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #111827;
}

.connection-info p {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.status-badge {
  padding: 6px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
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

.connection-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item label {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}

.detail-item span,
.detail-item code {
  font-size: 13px;
  color: #111827;
}

.detail-item code {
  font-family: monospace;
  background: #e5e7eb;
  padding: 2px 6px;
  border-radius: 4px;
}

.connection-actions {
  display: flex;
  gap: 8px;
}

.btn-secondary,
.btn-success,
.btn-warning,
.btn-danger {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-success {
  background: #dcfce7;
  color: #166534;
}

.btn-warning {
  background: #fef9c3;
  color: #854d0e;
}

.btn-danger {
  background: #fee2e2;
  color: #991b1b;
}

.btn-secondary:hover,
.btn-success:hover,
.btn-warning:hover,
.btn-danger:hover {
  opacity: 0.8;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #9ca3af;
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 16px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.btn-close {
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  color: #6b7280;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.connection-form {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.form-group input[type="text"],
.form-group input[type="url"],
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #6366f1;
}

.form-group small {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}
</style>
