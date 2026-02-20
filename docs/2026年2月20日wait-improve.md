结合你当前代码看，DeepSearch 的“后端能力”其实已经比前端展示丰富很多，核心问题是消费不充分和事件模型不统一。

后端已具备的可视化能力

DeepSearch 是“主代理 + 子代理委派”模型，且子代理固定有 research-agent / critique-agent / general-purpose，并且子代理是非流式执行：DeepSearchAgentBuilder.java (line 80) DeepSearchAgentBuilder.java (line 94) DeepSearchAgentBuilder.java (line 109) DeepSearchAgentBuilder.java (line 83)
SSE 事件里已经有 stateData、nodeStatus、startTime/endTime、nodeErrorMessage、usage、outputType：AgentResponseBuilder.java (line 215) AgentResponseBuilder.java (line 317) AgentResponseBuilder.java (line 220) AgentResponseBuilder.java (line 245)
你还有会话级统计接口（token、tool、LLM调用明细），可作为 DeepSearch 结果页的“执行报告”：SessionSummaryController.java (line 38) SessionSummaryService.java (line 60)
当前前端与后端不匹配点

时间线采集与面板显示绑定，面板关闭就不收集事件，导致用户再打开时上下文丢失：agentTimeline.ts (line 54) ChatContainer.vue (line 304)
前端事件类型定义过窄，只包含 starting/running/completed/failed/GRAPH_COMPLETED，但后端会发 ERROR/RATE_LIMIT/SERVICE_UNAVAILABLE/AUTH_FAILED/API_ERROR/INTERRUPTION：agent.ts (line 31) AgentResponseBuilder.java (line 100)
聊天主消息只在 _AGENT_MODEL_ + completed 时全量覆盖，导致多阶段信息可见性弱：chat.ts (line 684)
引用(citations)目前仅在“上传文件场景”提取并注入，DeepSearch 网络检索来源没有结构化展示：StreamController.java (line 166) StreamController.java (line 184)
TaskTool 已有 subagent_type，但前端没有显式展示“哪个子代理在执行哪条任务”：TaskTool.java (line 24) TaskTool.java (line 62)
针对你后端的前端优化优先级（可落地）

先做事件归一化层（必做）
把后端所有 eventType 映射到统一 UI 状态机（含错误态），避免遗漏。
时间线改为“始终采集，按需展示”（高价值）
isCollecting 不再由面板开关控制，只控制可见性。
增加 DeepSearch 过程卡片（高价值）
直接用 task 工具参数里的 subagent_type + description 展示“研究子任务队列/进行中/完成”。
增加执行指标条（中高价值）
实时显示本轮 usage、节点耗时、失败节点；结束后自动拉 /summary 做报告汇总。
引用体系分层（中价值）
先保留文件引用；再给网络检索加结构化来源（需后端补充 source 列表字段，不建议只靠正文正则）。
如果你愿意，我可以下一步直接给你一版“最小改动实现方案”（只改前端，不动后端接口）和“增强版方案”（补 1-2 个后端字段）两套对比清单。