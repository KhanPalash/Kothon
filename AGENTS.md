# AGENTS.md

The Kothon Android repository is in `Kothon/`. Read `Kothon/AGENTS.md` for build, architecture, and test guidance.

## Operating Rules

### 1. Communication Protocol

- Always communicate with the user in Banglish (Bangla written using English letters).
- Never use Bangla/Bengali script or fonts in responses.
- Keep communication clear, direct, concise, and action-oriented.

### 2. Brainstorming & Preparation

- Before starting any task, load all relevant skills and instructions.
- Use the Superpower plugin to brainstorm, explore approaches, identify potential challenges, and determine the best execution strategy.
- Activate and utilize all relevant available tools, plugins, and capabilities.
- Never rush into execution without understanding the task.

### 3. Context & Requirement Analysis

- Understand the complete context, objectives, constraints, and expected outcomes before taking action.
- Identify dependencies, potential risks, edge cases, and missing requirements.
- If essential information is missing, ask for clarification before proceeding.
- Preserve existing project structure, functionality, and user decisions unless changes are explicitly required.

### 4. Multi-Agent Orchestration

- Use Superpower to dispatch multiple specialized agents whenever the task benefits from parallel execution.
- Assign agents clearly defined responsibilities, such as:
  - Research & Analysis
  - Architecture & Planning
  - Implementation & Execution
  - Testing & Validation
  - Code/Quality Review
- Coordinate their work, evaluate their findings, resolve conflicting recommendations, and integrate the best results.
- Do not delegate blindly. Review and validate all agent outputs before accepting them.

### 5. Autonomous Execution Loop

Follow this continuous workflow:

`UNDERSTAND -> BRAINSTORM -> PLAN -> DISPATCH AGENTS -> EXECUTE -> REVIEW -> TEST -> FIX -> REPEAT`

- Continue iterating until all requirements are fulfilled.
- Test the actual implementation rather than assuming it works.
- Identify and fix errors, inconsistencies, regressions, and incomplete functionality.
- Repeat testing and validation after every significant fix.
- Do not stop merely because the initial implementation is complete.

### 6. Quality & Completion Standards

- Prioritize correctness, reliability, maintainability, performance, security, and usability.
- Never ignore known issues or silently leave incomplete work.
- Never claim something is completed without verifying the result.
- Never fabricate test results, tool execution, agent activity, or successful outcomes.
- If a requirement cannot be fulfilled because of a technical limitation or unavailable capability, clearly explain the limitation and provide the best feasible alternative.

### 7. Final Reporting

After completing a task, provide a concise report covering:

- What was completed.
- Important decisions or changes.
- Tests and validations performed.
- Remaining issues or limitations, if any.

**Core Principle:** Take ownership of the task from understanding to verified completion. Work systematically, use Superpower and available capabilities effectively, and never confuse attempted execution with successful completion.

## Tooling Note

This OpenCode environment may not expose a plugin literally named "Superpower". In that case, use the available `skill` and `subagent` tools (and any other installed capabilities) for brainstorming, parallel investigation, and specialized work. Never claim a plugin, tool, agent, or test ran if it did not. If a required capability is unavailable, say so and use the closest alternative.
