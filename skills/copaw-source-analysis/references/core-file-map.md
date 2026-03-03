# Core File Map

## 1. App and Lifecycle
- `src/copaw/app/_app.py`: FastAPI app setup, lifespan startup/shutdown, runner/channel/cron/MCP wiring.
- `src/copaw/cli/main.py`: CLI root and command registration.

## 2. Channels
- `src/copaw/app/channels/manager.py`: Channel runtime queueing/consumption and dispatch.
- `src/copaw/app/channels/registry.py`: Built-in channel registry + custom channel discovery.
- `src/copaw/cli/channels_cmd.py`: Interactive channel configuration and plugin installation.

## 3. Skills
- `src/copaw/agents/skills_manager.py`: Built-in/customized/active skill synchronization and CRUD helpers.
- `src/copaw/app/routers/skills.py`: Skills REST endpoints.
- `src/copaw/agents/skills_hub.py`: Skill hub search/install and multi-source remote import.

## 4. Memory
- `src/copaw/agents/memory/memory_manager.py`: memory compaction, summary, semantic search/get.
- `src/copaw/agents/tools/memory_search.py`: Tool wrapper for memory search.

## 5. MCP
- `src/copaw/app/mcp/manager.py`: MCP client lifecycle and hot-reload replacement.
- `src/copaw/app/mcp/watcher.py`: Config change watch and MCP reload.

## 6. Cron and Scheduling
- `src/copaw/app/crons/manager.py`: APScheduler job management and heartbeat.
- `src/copaw/app/crons/api.py`: Cron job REST API.

## 7. Session and Runner
- `src/copaw/app/runner/runner.py`: Agent runner query lifecycle.
- `src/copaw/app/runner/session.py`: Safe JSON session path handling across platforms.
- `src/copaw/app/runner/api.py`: Chat/session APIs.

## 8. Model and Provider Management
- `src/copaw/app/routers/providers.py`: provider/model configs and active model.
- `src/copaw/app/routers/local_models.py`: local model download/delete/status APIs.

## 9. Workspace Management
- `src/copaw/app/routers/workspace.py`: workspace zip upload/download.
