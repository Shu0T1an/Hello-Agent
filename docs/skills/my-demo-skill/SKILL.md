---
name: my-demo-skill
description: A demonstration skill showing how to create custom skills for Hello-Agent. Covers basic conversation assistance and code help.
triggers:
  - demo skill
  - my demo
  - skill creation demo
  - custom skill example
version: 1.0.0
owner: demo-user
---

# Purpose
Use when users ask about creating custom skills, learning how skills work, or want to see a skill demonstration.

# Trigger Phrases
- demo skill
- create a skill
- my demo
- skill creation demo
- custom skill example
- how to write a skill

# Workflow
1. Explain what this skill demonstrates.
2. Show the skill structure with a simple example.
3. Provide code or content examples from reference files.
4. Ask if the user wants more detailed information.

# Progressive Disclosure
Default response should include:
- Brief skill description
- Basic structure example
- One reference file hint

Detailed response may include:
- Full SKILL.md template from [templates/skill-template.md](templates/skill-template.md)
- Example references from [references/examples.md](references/examples.md)
- Code samples from [scripts/examples.txt](scripts/examples.txt)

# Sections
## What is a Skill?
Skills are markdown-based configuration files that define how the AI should respond to specific types of requests. They include:
- Metadata (name, version, triggers)
- Purpose and trigger phrases
- Workflow instructions
- Progressive disclosure rules
- Safety guidelines

## Skill Structure
A skill file consists of:
1. **Front Matter**: YAML metadata block
2. **Purpose Section**: When to use this skill
3. **Trigger Phrases**: Keywords that activate the skill
4. **Workflow**: Step-by-step instructions
5. **References**: Links to supporting files

# Safety
- Never expose sensitive information in skill files
- Validate user inputs before processing
- Follow the principle of least privilege
- Document any external dependencies