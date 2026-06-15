# Yass Reloaded Agent Notes

## Project Identity

This repository is **Yass Reloaded**, a fork of Yass. User-facing documentation,
release notes, UI copy, and new specs should use the name "Yass Reloaded" when
the project identity matters.

## Documentation Layout

- `specs/` contains current-state specs for implemented or accepted features.
- `docs/specs/` contains open feature specs and implementation ideas that are
  not done yet.
- When a feature from `docs/specs/` is implemented, move its spec into `specs/`
  and rewrite it as a current-state spec.
- Completed feature specs must follow the template in `specs/README.md`.
- Public GitHub Pages documentation lives under `docs/`; do not treat
  `specs/` as end-user documentation.

## Feature And Bugfix Workflow

- When working on an existing feature or bugfix, first check whether a matching
  current-state spec already exists in `specs/`.
- If no matching spec exists, point that out before making broad changes and
  suggest a Deep Explore pass to document the feature or bug area first.
- A Deep Explore pass should identify current behavior, code entry points,
  relevant tests, edge cases, and missing regression coverage, then create or
  update the appropriate spec.
- Small, obvious fixes may still proceed without a full Deep Explore, but note
  the missing documentation in the final response so it can be backfilled.

## Development Notes

- This is a Java/Swing application with Groovy/Spock tests.
- Prefer focused changes over broad rewrites, especially in large classes such
  as `YassActions`, `YassTable`, `YassSheet`, and `SongHeader`.
- Keep existing user work intact. Do not revert unrelated dirty files.
- Use `rg` for repository search.
- Use `apply_patch` for manual file edits.

## Verification

- Compile without tests: `mvn -q -DskipTests compile`
- Package without tests: `mvn -q -DskipTests package`
- Run all tests: `mvn -q test`
- Run one Spock spec: `mvn -q "-Dtest=SpecName" test`

For docs-only changes, a directory/link sanity check is usually enough. Mention
explicitly when no code tests were run.
