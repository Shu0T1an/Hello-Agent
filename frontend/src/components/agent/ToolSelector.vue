<template>
  <div class="tool-selector">
    <input
      v-model="search"
      type="text"
      placeholder="搜索工具..."
      class="search-input"
    />

    <div class="tool-sections">
      <!-- 本地工具 -->
      <div class="tool-section" v-if="filteredLocalTools.length > 0">
        <h3>本地工具</h3>
        <div class="tool-list">
          <label
            v-for="tool in filteredLocalTools"
            :key="tool.id"
            :class="['tool-item', { selected: selected.has(tool.id) }]"
          >
            <input
              type="checkbox"
              :checked="selected.has(tool.id)"
              @change="toggleTool(tool.id)"
            />
            <div class="tool-content">
              <span class="tool-name">{{ tool.displayName }}</span>
              <p class="tool-desc">{{ tool.description }}</p>
            </div>
          </label>
        </div>
      </div>

      <!-- MCP 工具 -->
      <div class="tool-section" v-if="filteredMcpTools.length > 0">
        <h3>MCP 工具</h3>
        <div class="tool-list">
          <label
            v-for="tool in filteredMcpTools"
            :key="tool.id"
            :class="['tool-item', { selected: selected.has(tool.id) }]"
          >
            <input
              type="checkbox"
              :checked="selected.has(tool.id)"
              @change="toggleTool(tool.id)"
            />
            <div class="tool-content">
              <div class="tool-header">
                <span class="tool-name">{{ tool.displayName }}</span>
                <span class="mcp-badge">MCP</span>
              </div>
              <p class="tool-desc">{{ tool.description }}</p>
              <span v-if="tool.mcpConnectionName" class="mcp-connection">
                {{ tool.mcpConnectionName }}
              </span>
            </div>
          </label>
        </div>
      </div>

      <!-- 无结果 -->
      <div v-if="filteredLocalTools.length === 0 && filteredMcpTools.length === 0" class="no-results">
        未找到匹配的工具
      </div>
    </div>

    <div class="selection-summary">
      已选择 {{ selected.size }} 个工具
      <button v-if="selected.size > 0" @click="clearAll" type="button" class="btn-clear">
        清空
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useToolDefinitionStore } from '@/stores/toolDefinition'

const props = defineProps<{
  modelValue: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const toolStore = useToolDefinitionStore()
const search = ref('')

const selected = ref<Set<number>>(new Set(props.modelValue))

// 过滤后的本地工具
const filteredLocalTools = computed(() =>
  toolStore.localTools.filter(t =>
    t.isActive && (
      t.displayName.toLowerCase().includes(search.value.toLowerCase()) ||
      t.description?.toLowerCase().includes(search.value.toLowerCase()) ||
      t.toolName.toLowerCase().includes(search.value.toLowerCase())
    )
  )
)

// 过滤后的 MCP 工具
const filteredMcpTools = computed(() =>
  toolStore.mcpTools.filter(t =>
    t.isActive && (
      t.displayName.toLowerCase().includes(search.value.toLowerCase()) ||
      t.description?.toLowerCase().includes(search.value.toLowerCase()) ||
      t.toolName.toLowerCase().includes(search.value.toLowerCase())
    )
  )
)

// 监听外部变化
watch(() => props.modelValue, (newVal) => {
  selected.value = new Set(newVal)
}, { deep: true })

function toggleTool(id: number) {
  if (selected.value.has(id)) {
    selected.value.delete(id)
  } else {
    selected.value.add(id)
  }
  emit('update:modelValue', Array.from(selected.value))
}

function clearAll() {
  selected.value.clear()
  emit('update:modelValue', [])
}

// 初始化时加载工具
toolStore.fetchTools()
</script>

<style scoped>
.tool-selector {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px;
  background: #f9fafb;
}

.search-input {
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 16px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
}

.search-input:focus {
  outline: none;
  border-color: #6366f1;
}

.tool-sections {
  max-height: 400px;
  overflow-y: auto;
}

.tool-section {
  margin-bottom: 20px;
}

.tool-section:last-child {
  margin-bottom: 0;
}

.tool-section h3 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.tool-item:hover {
  border-color: #d1d5db;
  background: #f9fafb;
}

.tool-item.selected {
  border-color: #6366f1;
  background: #eef2ff;
}

.tool-item input[type="checkbox"] {
  margin-top: 2px;
  margin-right: 12px;
  width: 18px;
  height: 18px;
  cursor: pointer;
}

.tool-content {
  flex: 1;
  min-width: 0;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.tool-name {
  font-weight: 500;
  color: #111827;
  font-size: 14px;
}

.tool-desc {
  font-size: 12px;
  color: #6b7280;
  margin: 4px 0 0 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.mcp-badge {
  font-size: 10px;
  padding: 2px 8px;
  background: #f3e8ff;
  color: #7c3aed;
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
}

.mcp-connection {
  font-size: 11px;
  color: #8b5cf6;
  margin-top: 4px;
  display: inline-block;
}

.no-results {
  text-align: center;
  padding: 40px 20px;
  color: #9ca3af;
}

.selection-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
  font-size: 13px;
  color: #6b7280;
}

.btn-clear {
  padding: 6px 12px;
  background: none;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-clear:hover {
  background: #f3f4f6;
  color: #374151;
}
</style>
