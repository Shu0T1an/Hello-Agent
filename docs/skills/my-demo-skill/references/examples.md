# Skill Examples

This document provides various examples of skill configurations and use cases.

## Example 1: Simple Greeting Skill

```yaml
---
name: greeting-skill
description: A simple skill for handling greetings
triggers:
  - hello
  - hi
  - greeting
version: 1.0.0
---

# Purpose
Use when the user greets the AI or says hello.

# Trigger Phrases
- hello
- hi
- hey
- good morning
- good afternoon

# Workflow
1. Respond with a friendly greeting
2. Ask how you can help
3. Wait for user input
```

## Example 2: Code Assistant Skill

```yaml
---
name: code-assistant
description: Help with coding questions and debugging
triggers:
  - code help
  - debug
  - programming
version: 1.0.0
---

# Purpose
Use when users ask for programming help or debugging assistance.

# Trigger Phrases
- code help
- debug my code
- how to program
- programming question

# Workflow
1. Understand the programming problem
2. Ask for relevant code snippets
3. Provide solutions and explanations
4. Offer follow-up help
```

## Example 3: Data Analysis Skill

```yaml
---
name: data-analysis
description: Analyze and interpret data
type: analysis
triggers:
  - analyze data
  - data visualization
  - statistics
version: 1.0.0
---

# Purpose
Use when users need help analyzing data or creating visualizations.

# Trigger Phrases
- analyze my data
- create a chart
- statistical analysis
- data interpretation

# Workflow
1. Understand the data type and analysis goal
2. Ask for data format and sample
3. Suggest appropriate analysis methods
4. Provide results and visualizations
```

## Best Practices

1. **Clear Descriptions**: Always provide clear, concise descriptions
2. **Specific Triggers**: Use specific, unambiguous trigger phrases
3. **Progressive Disclosure**: Start simple, offer more details
4. **Safety First**: Always include safety considerations
5. **Version Control**: Use semantic versioning for skills