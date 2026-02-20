# SubAgentInterceptor Design

## Scope
- Enhance model calls only via `ModelInterceptor`.
- Inject `task` tool and subagent usage prompt.
- Per-agent switch.
- Subagent instances always disable `SubAgentInterceptor` (recursion guard).

## Main Components
- `agent-core/src/main/java/cn/ts/agent/extension/tools/TaskTool.java`
- `agent-core/src/main/java/cn/ts/agent/extension/interceptor/SubAgentInterceptor.java`
- `Agent-Studio/src/main/java/cn/ts/web/factory/AgentFactory.java`

## Task Protocol
`task` input:
- `description` (required)
- `subagent_type` (required)

`task` output:
- plain string (success output or readable error text)

## Wiring
1. `AgentFactory` checks `enableSubAgentInterceptor`.
2. Build subagent map from configured mappings.
3. Optionally add `general-purpose` subagent when `includeGeneralPurpose=true`.
4. Inject `SubAgentInterceptor` into `ReactAgent.Builder.modelInterceptors(...)`.

## Tool Policy
- `INHERIT`: use target agent tools.
- `CUSTOM`: use `customToolIds` from mapping.

## Recursion Guard
When building a subagent instance, `AgentFactory` calls internal build path with `includeSubAgentInterceptor=false`.
Even if target agent itself has subagent enabled, that runtime subagent instance will not install the interceptor.
