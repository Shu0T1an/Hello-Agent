---
name: project-skill-demo
description: Demonstrates skill metadata, trigger matching, and progressive disclosure for Hello-Agent.
triggers:
  - onboarding
  - setup guide
  - hello-agent quick start
version: 1.0.0
owner: backend-team
---

# Purpose
Use when the user asks for onboarding help, setup steps, or a quick-start walkthrough.

# Trigger Phrases
- onboarding
- setup guide
- quick start
- skill demo

# Workflow
1. Start with a short summary of the goal.
2. Provide only the first actionable step.
3. Ask whether to reveal the next level of detail.
4. If the user asks for more, load [Onboarding Checklist](references/checklist.md).

# Progressive Disclosure
Default response should include:
- Goal
- First step
- One optional follow-up hint

Detailed response may include:
- Full checklist from [references/checklist.md](references/checklist.md)
- Command samples from [scripts/commands.txt](scripts/commands.txt)

# Safety
- Never include secrets or private tokens.
- Confirm OS before sharing shell commands.
