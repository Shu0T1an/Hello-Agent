<template>
  <div class="flex h-screen bg-zinc-50">
    <!-- 侧边栏：知识库选择 -->
    <aside class="w-64 bg-white border-r border-zinc-200 flex flex-col p-4">
      <h2 class="text-lg font-semibold text-zinc-900 mb-4">知识库</h2>

      <!-- 知识库列表 -->
      <div class="flex-1 space-y-2 overflow-y-auto custom-scrollbar">
        <div
          v-for="kb in knowledgeBases"
          :key="kb.kbId"
          :class="[
            'px-4 py-3 rounded-xl cursor-pointer transition-all duration-200',
            selectedKbId === kb.kbId
              ? 'bg-indigo-500 text-white shadow-medium'
              : 'hover:bg-zinc-100 text-zinc-700'
          ]"
          @click="selectedKbId = kb.kbId"
        >
          <div class="font-medium">{{ kb.kbName }}</div>
        </div>
      </div>

      <!-- 上传按钮 -->
      <BaseButton
        variant="primary"
        class="w-full btn-hover-lift"
        @click="showUploadModal = true"
      >
        <Upload :size="18" />
        上传文档
      </BaseButton>
    </aside>

    <!-- 主内容区：对话界面 -->
    <main class="flex-1 flex flex-col p-6">
      <div class="flex-1 bg-white rounded-2xl shadow-soft flex flex-col overflow-hidden">
        <!-- 消息列表 -->
        <div ref="messagesContainer" class="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['flex', msg.role === 'user' ? 'justify-end' : 'justify-start']"
          >
            <div :class="['max-w-[70%]', msg.role === 'user' ? 'order-2' : 'order-1']">
              <!-- 消息内容 -->
              <div
                :class="[
                  'px-5 py-3 rounded-2xl',
                  msg.role === 'user'
                    ? 'bg-indigo-500 text-white rounded-br-md'
                    : 'bg-zinc-100 text-zinc-900 rounded-bl-md'
                ]"
              >
                <div
                  v-if="msg.role === 'assistant'"
                  v-html="renderMarkdownSync(msg.content)"
                  class="prose prose-sm max-w-none"
                />
                <div v-else>{{ msg.content }}</div>
              </div>

              <!-- 来源文档信息 -->
              <div
                v-if="msg.sources && msg.sources.length > 0"
                class="mt-3 p-4 bg-zinc-50 rounded-xl border border-zinc-200"
              >
                <div class="flex items-center gap-2 mb-2">
                  <FileText :size="16" class="text-zinc-400" />
                  <span class="text-sm font-medium text-zinc-700">参考来源</span>
                </div>
                <ul class="space-y-1">
                  <li
                    v-for="(source, idx) in msg.sources"
                    :key="idx"
                    class="text-sm text-zinc-600 flex items-center justify-between gap-4"
                  >
                    <span class="truncate">{{ source.fileName }}</span>
                    <span
                      v-if="source.score"
                      :class="[
                        'text-xs px-2 py-0.5 rounded-full flex-shrink-0',
                        source.score >= 0.8
                          ? 'bg-success-100 text-success-700'
                          : source.score >= 0.6
                          ? 'bg-warning-100 text-warning-700'
                          : 'bg-zinc-100 text-zinc-600'
                      ]"
                    >
                      {{ (source.score * 100).toFixed(0) }}%
                    </span>
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <!-- 加载指示器 -->
          <div v-if="isLoading" class="flex justify-start">
            <div class="bg-zinc-100 px-5 py-3 rounded-2xl rounded-bl-md">
              <div class="flex gap-1">
                <span class="w-2 h-2 bg-zinc-400 rounded-full animate-bounce" style="animation-delay: 0ms" />
                <span class="w-2 h-2 bg-zinc-400 rounded-full animate-bounce" style="animation-delay: 150ms" />
                <span class="w-2 h-2 bg-zinc-400 rounded-full animate-bounce" style="animation-delay: 300ms" />
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <BaseEmpty
            v-if="messages.length === 0 && !isLoading"
            type="search"
            title="开始提问"
            description="选择知识库并输入您的问题，AI 将基于文档内容为您解答"
            :show-action="false"
          />
        </div>

        <!-- 输入区 -->
        <div class="p-4 border-t border-zinc-200">
          <div class="flex gap-3">
            <BaseTextarea
              v-model="query"
              placeholder="输入问题... (Ctrl+Enter 发送)"
              :disabled="isLoading"
              class="flex-1"
              @keydown.enter.ctrl="handleSubmit"
            />
            <BaseButton
              variant="primary"
              :disabled="isLoading || !query.trim()"
              @click="handleSubmit"
              class="btn-hover-lift"
            >
              <Send :size="18" />
              发送
            </BaseButton>
          </div>
        </div>
      </div>
    </main>

    <!-- 上传模态框 -->
    <BaseModal
      v-model:visible="showUploadModal"
      title="上传文档"
      width="md"
    >
      <div class="space-y-4">
        <p class="text-sm text-zinc-600">
          支持上传 PDF、TXT、Markdown 格式的文档，AI 将自动处理并建立索引。
        </p>

        <!-- 上传区域 -->
        <div
          class="border-2 border-dashed border-zinc-300 rounded-xl p-8 text-center hover:border-indigo-400 transition-colors cursor-pointer"
          @click="$refs.fileInput?.click()"
        >
          <UploadCloud :size="48" class="mx-auto text-zinc-400 mb-3" />
          <p class="text-sm text-zinc-600 mb-1">点击选择文件或拖拽文件到此处</p>
          <p class="text-xs text-zinc-400">支持 .pdf、.txt、.md、.markdown 格式</p>
          <input
            ref="fileInput"
            type="file"
            multiple
            class="hidden"
            @change="handleFileSelect"
            accept=".pdf,.txt,.md,.markdown"
          >
        </div>

        <!-- 文件列表 -->
        <div v-if="selectedFiles.length > 0" class="space-y-2 max-h-48 overflow-y-auto custom-scrollbar">
          <div
            v-for="(file, idx) in selectedFiles"
            :key="idx"
            class="flex items-center justify-between p-3 bg-zinc-50 rounded-lg group"
          >
            <div class="flex items-center gap-3 min-w-0">
              <File :size="20" class="text-zinc-400 flex-shrink-0" />
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium text-zinc-700 truncate">{{ file.name }}</p>
                <p class="text-xs text-zinc-500">{{ formatFileSize(file.size) }}</p>
              </div>
            </div>
            <button
              @click="selectedFiles.splice(idx, 1)"
              class="p-1 text-zinc-400 hover:text-error-500 transition-colors opacity-0 group-hover:opacity-100"
            >
              <X :size="18" />
            </button>
          </div>
        </div>
      </div>

      <template #footer>
        <BaseButton
          variant="secondary"
          @click="showUploadModal = false"
          :disabled="isUploading"
        >
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          @click="handleUpload"
          :disabled="selectedFiles.length === 0 || isUploading"
          :loading="isUploading"
        >
          {{ isUploading ? '上传中...' : '上传' }}
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import {
  ragQueryStream,
  batchUploadDocuments,
  type RagQueryRequest,
  type SourceDocument
} from '@/api/rag'
import { renderMarkdownSync } from '@/utils/markdown'
import { useToast } from '@/composables/useToast'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import BaseModal from '@/components/base/BaseModal.vue'
import BaseEmpty from '@/components/base/BaseEmpty.vue'
import {
  Upload,
  Send,
  UploadCloud,
  File,
  FileText,
  X,
} from 'lucide-vue-next'

const toast = useToast()

// 状态
const knowledgeBases = ref([
  { kbId: 'default', kbName: '默认知识库' }
])
const selectedKbId = ref('default')
const query = ref('')
const messages = ref<Array<{
  id: number
  role: 'user' | 'assistant'
  content: string
  sources?: SourceDocument[]
}>>([])
const showUploadModal = ref(false)
const selectedFiles = ref<File[]>([])
const isLoading = ref(false)
const isUploading = ref(false)
const messagesContainer = ref<HTMLElement>()
const fileInput = ref<HTMLInputElement>()

// 初始化
onMounted(() => {
  // 可以在这里加载知识库列表
})

// 提交查询
const handleSubmit = () => {
  if (!query.value.trim() || isLoading.value) return

  const userMessage = {
    id: Date.now(),
    role: 'user' as const,
    content: query.value
  }
  messages.value.push(userMessage)

  const assistantMessage = {
    id: Date.now() + 1,
    role: 'assistant' as const,
    content: '',
    sources: [] as SourceDocument[]
  }
  messages.value.push(assistantMessage)

  const currentQuery = query.value
  query.value = ''
  isLoading.value = true

  scrollToBottom()

  // 流式查询
  ragQueryStream(
    {
      query: currentQuery,
      knowledgeBaseId: selectedKbId.value,
      topK: 5,
      similarityThreshold: 0.7
    },
    (chunk: string) => {
      assistantMessage.content += chunk
      scrollToBottom()
    },
    () => {
      // 完成回调
      isLoading.value = false
    },
    (error: Error) => {
      console.error('Stream error:', error)
      assistantMessage.content = '查询出错，请稍后重试。'
      isLoading.value = false
      toast.error('查询失败，请稍后重试')
    }
  )
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 处理文件选择
const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files) {
    selectedFiles.value = Array.from(target.files)
  }
}

// 处理上传
const handleUpload = async () => {
  if (selectedFiles.value.length === 0) return

  isUploading.value = true
  try {
    const result = await batchUploadDocuments(selectedFiles.value, selectedKbId.value)
    console.log('Upload result:', result.data)
    showUploadModal.value = false
    selectedFiles.value = []
    toast.success(`成功上传 ${result.data.successful} 个文档`)
  } catch (error) {
    console.error('Upload error:', error)
    toast.error('上传失败，请重试')
  } finally {
    isUploading.value = false
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.animate-bounce {
  animation: bounce 1.4s infinite ease-in-out both;
}
</style>
