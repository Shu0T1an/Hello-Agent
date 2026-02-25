<template>
  <div class="rounded-xl border border-zinc-200 bg-white overflow-hidden">
    <AgentGraphToolbar
      :layout-mode="graphStore.layoutMode"
      @layout-change="handleLayoutChange"
      @fit-view="handleFitView"
    />

    <div v-if="graphStore.loading" class="h-[64vh] flex items-center justify-center text-sm text-zinc-500">
      图谱加载中...
    </div>

    <div v-else-if="graphStore.error" class="h-[64vh] flex items-center justify-center text-sm text-rose-600 px-4 text-center">
      {{ graphStore.error }}
    </div>

    <div v-else-if="!graphStore.graph" class="h-[64vh] flex items-center justify-center text-sm text-zinc-500">
      暂无图谱数据
    </div>

    <div v-else class="h-[64vh] flex">
      <div class="flex-1 min-w-0">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-edge-options="defaultEdgeOptions"
          :nodes-connectable="false"
          :nodes-draggable="false"
          :elements-selectable="true"
          :fit-view-on-init="true"
          :min-zoom="0.2"
          :max-zoom="2"
          @node-click="onNodeClick"
        >
          <Background />
          <MiniMap />
          <Controls />
        </VueFlow>
      </div>

      <AgentNodeDetailDrawer :node="graphStore.selectedNode" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import dagre from 'dagre'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import {
  ConnectionLineType,
  MarkerType,
  type NodeMouseEvent,
  Position,
  VueFlow,
  type Edge,
  type Node,
  useVueFlow
} from '@vue-flow/core'
import type { AgentGraphEdge, AgentGraphNode, GraphLayoutMode } from '@/types/agent-graph'
import { useAgentGraphStore } from '@/stores/agentGraph'
import AgentGraphToolbar from './AgentGraphToolbar.vue'
import AgentNodeDetailDrawer from './AgentNodeDetailDrawer.vue'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

const props = defineProps<{
  agentId: number | null
  agentName?: string | null
}>()

const graphStore = useAgentGraphStore()
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])

const { fitView } = useVueFlow()

const defaultEdgeOptions = computed(() => ({
  type: 'default',
  markerEnd: MarkerType.ArrowClosed,
  animated: false,
  interactionWidth: 18,
  style: { stroke: '#64748b', strokeWidth: 1.5 },
  labelStyle: { fill: '#334155', fontSize: 11 },
  labelBgStyle: { fill: '#f8fafc', fillOpacity: 0.9 },
  labelBgPadding: [4, 2] as [number, number],
  labelBgBorderRadius: 4,
  connectionLineType: ConnectionLineType.SmoothStep
}))

watch(
  () => [props.agentId, props.agentName] as const,
  async ([agentId, agentName]) => {
    if (agentId == null && (!agentName || !agentName.trim())) {
      graphStore.reset()
      nodes.value = []
      edges.value = []
      return
    }
    try {
      if (agentId != null) {
        await graphStore.loadGraph(agentId)
      } else {
        await graphStore.loadGraphByName((agentName ?? '').trim())
      }
      rebuildGraphElements()
    } catch {
      nodes.value = []
      edges.value = []
    }
  },
  { immediate: true }
)

watch(
  () => graphStore.layoutMode,
  () => {
    rebuildGraphElements()
  }
)

function handleLayoutChange(mode: GraphLayoutMode) {
  graphStore.setLayout(mode)
}

function handleFitView() {
  fitView({ padding: 0.2, duration: 200 })
}

function rebuildGraphElements() {
  const graph = graphStore.graph
  if (!graph) {
    nodes.value = []
    edges.value = []
    return
  }

  const baseNodes = graph.nodes.map(toFlowNode)
  const mergedEdges = mergeParallelEdges(graph.edges)
  const baseEdges = mergedEdges.map(toFlowEdge)
  const layouted = layoutWithDagre(baseNodes, baseEdges, graphStore.layoutMode)
  nodes.value = layouted.nodes
  edges.value = layouted.edges

  nextTick(() => {
    fitView({ padding: 0.2, duration: 250 })
  })
}

function toFlowNode(node: AgentGraphNode): Node {
  const isHorizontal = graphStore.layoutMode === 'LR'
  return {
    id: node.id,
    type: 'default',
    position: { x: 0, y: 0 },
    draggable: false,
    selectable: true,
    sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
    targetPosition: isHorizontal ? Position.Left : Position.Top,
    class: `node-${node.nodeType}`,
    data: {
      label: `${node.label}\n${node.id}`
    }
  }
}

function toFlowEdge(edge: AgentGraphEdge & { isBidirectional?: boolean }): Edge {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.label ?? undefined,
    type: 'smoothstep',
    style: edge.isBidirectional
      ? { stroke: '#64748b', strokeWidth: 1.5, strokeDasharray: '6 4' }
      : undefined
  }
}

function layoutWithDagre(flowNodes: Node[], flowEdges: Edge[], mode: GraphLayoutMode) {
  const g = new dagre.graphlib.Graph()
  g.setGraph({
    rankdir: mode,
    ranksep: 150,
    nodesep: 90,
    edgesep: 40,
    marginx: 24,
    marginy: 24,
    acyclicer: 'greedy'
  })
  g.setDefaultEdgeLabel(() => ({}))

  for (const node of flowNodes) {
    g.setNode(node.id, { width: 220, height: 72 })
  }
  for (const edge of flowEdges) {
    g.setEdge(edge.source, edge.target)
  }
  dagre.layout(g)

  const nextNodes = flowNodes.map((node) => {
    const point = g.node(node.id)
    return {
      ...node,
      position: {
        x: (point?.x ?? 0) - 110,
        y: (point?.y ?? 0) - 36
      }
    }
  })

  return { nodes: nextNodes, edges: flowEdges }
}

function mergeParallelEdges(edges: AgentGraphEdge[]): Array<AgentGraphEdge & { isBidirectional?: boolean }> {
  const grouped = new Map<string, AgentGraphEdge[]>()
  for (const edge of edges) {
    const key = `${edge.source}__${edge.target}`
    const list = grouped.get(key)
    if (list) {
      list.push(edge)
    } else {
      grouped.set(key, [edge])
    }
  }

  const result: Array<AgentGraphEdge & { isBidirectional?: boolean }> = []
  let seq = 1
  for (const [, list] of grouped.entries()) {
    const head = list[0]
    if (!head) {
      continue
    }
    const reverseKey = `${head.target}__${head.source}`
    const labels = Array.from(
      new Set(
        list
          .map(item => item.label)
          .filter((label): label is string => Boolean(label && label.trim()))
      )
    )

    result.push({
      ...head,
      id: `merged-e${seq++}`,
      label: labels.length > 0 ? labels.join(' | ') : head.label ?? null,
      isBidirectional: grouped.has(reverseKey)
    })
  }

  return result
}

function onNodeClick(event: NodeMouseEvent) {
  graphStore.selectNode(String(event.node.id))
}
</script>

<style scoped>
:deep(.vue-flow__node) {
  min-width: 220px;
  border-radius: 12px;
  border: 1px solid #d4d4d8;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  font-size: 12px;
  white-space: pre-wrap;
  line-height: 1.35;
  color: #18181b;
}

:deep(.vue-flow__node.selected) {
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.35);
  border-color: #2563eb;
}

:deep(.vue-flow__node.node-llm) {
  border-left: 4px solid #2563eb;
}

:deep(.vue-flow__node.node-tool) {
  border-left: 4px solid #f59e0b;
}

:deep(.vue-flow__node.node-hook) {
  border-left: 4px solid #8b5cf6;
}

:deep(.vue-flow__node.node-end) {
  border-left: 4px solid #10b981;
}

:deep(.vue-flow__node.node-custom) {
  border-left: 4px solid #64748b;
}
</style>
