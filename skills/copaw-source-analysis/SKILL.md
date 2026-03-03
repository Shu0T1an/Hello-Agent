---
name: copaw-source-analysis
description: Analyze the CoPaw codebase from local source and produce architecture/feature comparisons, especially against Hello-Agent. Use when user asks to read CoPaw code, compare capabilities, extract module responsibilities, or propose migration/borrowing plans based on CoPaw implementation details.
---

# Purpose
Read CoPaw source code from local disk and output code-backed conclusions (not README-only summaries).

# Default Source Path
- Primary local path: `D:\AI\Github\CoPaw`
- If this path does not exist, search user-provided path or ask for a valid local checkout path.

# Workflow
1. Validate source path exists and contains `src/copaw` and `pyproject.toml`.
2. Capture baseline metadata:
- Branch/commit if available
- Top-level modules in `src/copaw`
- API router inventory (`app/routers`, `app/crons/api.py`, `app/runner/api.py`)
3. Read core implementation files for evidence:
- App lifecycle and startup wiring
- Channel system
- Skills lifecycle/hub
- Memory system
- MCP manager
- Cron/scheduling
- Runner/session persistence
4. When comparing with Hello-Agent:
- Build a capability matrix with at least: channels, skills lifecycle, MCP, memory, RAG, checkpoints/resume, scheduling, local models, session model.
- Reference concrete files from both repositories in conclusions.
5. Produce outputs in this order:
- Executive summary
- Capability matrix
- Detailed deltas
- Practical adoption recommendations
- Evidence file list

# Quality Rules
- Prefer statements verifiable from code over inferred product claims.
- Mark uncertain conclusions as "code not found" rather than guessing.
- Distinguish between "implemented", "partially implemented", and "not observed".

# Progressive Disclosure
Default output includes:
- 1-page summary + capability matrix

If user asks for deeper detail, load:
- [Core File Map](references/core-file-map.md)
