# Feature Spec Format

`specs/` contains current-state documentation for implemented or accepted
features. These files are written for future agents and maintainers, not as
polished user documentation.

Open, planned, or speculative work belongs in `docs/specs/`. Once implemented,
move the file here and convert it to the structure below.

## Required Structure

Each completed feature spec should use these sections in this order:

```markdown
# Feature Name

## Status

Implemented/current. Mention important version or scope notes if useful.

## Purpose

Explain why the feature exists and what problem it solves.

## User-Facing Behavior

Describe menus, shortcuts, dialogs, visible workflow, and important disabled or
fallback states.

## Core Rules

Document the domain rules and heuristics that must stay true when the feature is
extended.

## Data And Configuration

List relevant UltraStar tags, properties, cache files, generated filenames, and
external tool assumptions.

## Code Entry Points

List the classes and methods future agents should inspect first.

## Regression Coverage

List the test specs or specific scenarios currently covered.

## Extension Notes

Call out known limitations, intentional non-goals, and rules of thumb for safe
future changes.
```

## Style

- Prefer current behavior over historical planning notes.
- If a historical decision still matters, rewrite it as a current rule.
- Keep future ideas in `Extension Notes`, not mixed into the main behavior.
- Use concrete class, method, property, and file names.
- Keep specs concise enough to scan, but complete enough that an agent can make
  a safe first change without rediscovering the whole feature from scratch.
