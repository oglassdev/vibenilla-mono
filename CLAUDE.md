# vibenilla-mono

Vanilla Minecraft features as Minestom libraries, in one Gradle build. Forked from the [vibenilla](https://github.com/vibenilla) repositories to work on them together.

## General guidelines

- Do not edit AGENTS.md or CLAUDE.md unless explicitly asked.
- You may suggest additions to the glossary. Project specific words that take time to seek out should be added to the glossary.
- Match vanilla behavior. When unsure how something behaves, check the vanilla source or game data rather than guessing.

## Tooling

- Use `just` for common tasks (`just` lists them):
  - `just build` to build everything
  - `just test [modules...]` to run tests, e.g. `just test pvp loot`
  - `just format` to apply formatting
  - `just check` to verify formatting
- Run `just ready` before publishing substantial changes.
- Tool versions are pinned in `mise.toml`; run `mise install` to get them.
- Dependency versions live in `gradle/libs.versions.toml`. Shared module configuration lives in the `vibenilla.library` convention plugin under `buildSrc`.
- Be careful with destructive actions that are not explicitly asked for by the user.

## Code Style

- Keep code simple. When implementing a feature, think of `yagni`.
- Type inference is your friend. Prefer `var` where the type is obvious.
- Java is formatted with google-java-format (AOSP style) through Spotless. Don't hand-format; run `just format`.
- Minestom is a host API: depend on it with `compileOnly`, never `implementation`.
- Writing tests are a great way to ensure proper behavior, but we should be careful with what tests and how many tests we write. Don't write an endless amount of tests that covers unthinkable regressions or run smoke tests for most changes.
- Keep package exports narrow and intentional.
- Ensure modularity of code. We don't want 1000+ line monoliths.

## Git

- Do not revert unrelated changes.
- Commit messages should follow Conventional Commits, scoped by module when it applies (e.g. `fix(pvp): ...`).
- Do not rely on pre-commit hooks; CI is the source of truth for formatting, linting, tests, and builds.

## Glossary

- **Minestom**: The from-scratch Minecraft server library these modules build on. It ships no vanilla gameplay.
- **Module**: One library in this repository (`pvp`, `worldgen`, `placement`, `crafting`, `fluids`, `loot`), published as its own artifact.
- **Upstream**: The original vibenilla repositories this was forked from. Not synced.
- **Vanilla parity**: Behaving exactly like the official Minecraft server.
- **Version scheme**: `yyyy.MM.dd-<minecraft version>`, e.g. `2026.10.24-26.3`. The Minecraft version is `minecraft` in the version catalog.
- **Inside test**: The `minestom.inside-test` system property, set for all tests, which lets Minestom run without a real server.
