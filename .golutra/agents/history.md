# History

## Description
- Record each Git change or local file change summary.
- Each entry includes reason, impact scope, and related links (put links in Notes when applicable).
- If owner is not specified, default to `<project-name>-agent-1`.
- Use datetime format `YYYY-MM-DD HH:MM` (24h).

## Mandatory Action
- MUST: When this table reaches 50 entries, compress the records into shorter and more general summaries, keeping stable and reusable change points.

## Record Template
| Date Time | Type | Summary | Reason | Impact Scope | Owner Id | Notes |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| 2026-02-27 11:48 | fix | 娣囶喖顦?`McpManagerImpl` 娑?MCP 缁狅紕鎮婇崳銊ф祲閸忚櫕妫╄箛妞剧瑢濞夈劑鍣存稊杈╃垳 | 缁惧じ绗?閺堫剙婀撮弮銉ョ箶閸戣櫣骞?`MCP 缂佺媴绱曢幃濠囧闯...` 缁涘绗夐崣顖濐嚢閺傚洦婀伴敍灞藉閸濆秵甯撻梾?| `agent-core` 濡€虫健 `cn.ts.agent.mcp.McpManagerImpl` | 01KJEJZ7D9HCZ8JQ6KJEY3J1DA | 瀹稿弶澧界悰?`mvn -pl agent-core -DskipTests compile`閿涘本鐎鐑樺灇閸?|
| 2026-02-27 12:22 | fix | 淇 MCP HTTP 绌哄鎴风 NPE銆佹仮澶?stop/start 鍚庤繛鎺ヨ兘鍔涳紝骞惰ˉ榻?tools 鎵弿/鍚屾 count 杩斿洖 | 绾夸笂瀛樺湪 P0锛欻TTP 绫诲瀷瑙﹀彂绌烘寚閽堬紱MCP 绠＄悊鍣?stop/start 鍚庢棤娉曞啀娉ㄥ唽杩炴帴锛?api/tools/scan-local 涓?/api/tools/sync-mcp/{connectionName} 缂哄皯 data.count | agent-core 鐨?McpManagerImpl銆?gent-studio 鐨?ToolManagementController 鍙婂搴旀祴璇?| 01KJEME96NQRFDEGYKX2RAK68K | 宸叉墽琛?mvn -pl agent-core -Dtest=McpManagerImplLifecycleTest test銆乵vn -pl agent-studio -Dtest=ToolManagementControllerTest test锛涘叏閲?mvn -pl agent-core,agent-studio test 澶辫触涓虹幇鏈?MyBatis/PostgreSQL 闆嗘垚娴嬭瘯鐜闂锛堥潪鏈鏀瑰姩寮曞叆锛?|
