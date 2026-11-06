# Repository Guidelines

## Project Structure & Module Organization

ModularCraft targets Minecraft 1.21.1, NeoForge 21.1.218, and Java 21.

- `src/main/java/com/malevolentgods/modularcraft/`: pure Java `domain`, `dsp`, and `transport`; Minecraft adapters in `game`, `network`, and `client`.
- `src/main/resources/`: mod metadata, localization, models, textures, and loot tables. Editable SVGs and provenance live in `art/`.
- `src/test/`: JUnit tests; `src/gametest/`: NeoForge integration tests and native fixtures; `scripts/tests/`: Python launcher tests.
- `docs/`: development, architecture, and acceptance procedures; `specs/001-modular-synth-mvp/`: feature contract; `validation/`: evidence and outstanding gates.

## Build, Test, and Development Commands

Install Java 21, Python 3.9+, and GNU Make 3.81+. Use the pinned Gradle wrapper; Make targets enforce strict dependency verification.

- `make build`: run checks and create JARs/project ZIP in `build/libs/`.
- `make check`: run launcher tests, JUnit, formatting, Checkstyle, and PMD.
- `make test TEST='*SceneTest'`: run selected JUnit tests.
- `make gametest`: run headless tests against the packaged JAR.
- `make verify`: run full automated checks, packaged GameTests, and artifact inspection.
- `make client`: launch the built JAR; `make client-dev` launches development classes.
- `make format`: apply google-java-format; `make lint` checks formatting/static analysis.

See [development commands](docs/development.md) for smoke probes and overrides.

## Coding Style & Naming Conventions

Use google-java-format's two-space Java indentation, explicit imports, `UpperCamelCase` types, `lowerCamelCase` methods/fields, and `UPPER_SNAKE_CASE` constants. Compiler warnings are errors. Keep DSP/domain/transport independent of Minecraft; client audio and rendering must remain unavailable to dedicated-server loading. Preserve server authority and bounded audio processing.

## Testing Guidelines

Follow red–green–refactor for executable changes; reproduce bugs with regression tests first. Use JUnit Jupiter (`*Test.java`), NeoForge GameTests, and Python `unittest` (`test_*.py`). Cover changed behavior, malformed inputs, persistence, and lifecycle boundaries; no numeric coverage threshold is configured. Documentation-only changes need consistency/link checks.

Record evidence in `validation/ledger.md`. Automated checks do not establish native multiplayer, acoustic, or listening acceptance; retain explicit PENDING/BLOCKED results.

## Commit & Pull Request Guidelines

History is short; the implementation commit uses `feat: <description>`. Prefer concise, imperative, type-prefixed subjects. PRs should describe behavior, link applicable issues/specs, report commands/results and remaining gates, and include screenshots for UI changes. Record reviewer sign-off or explicit self-review.

## Safety & Governance

Follow the [constitution](.specify/memory/constitution.md). Preserve worlds under `run/` and dependency pins; verify new hashes against upstream. Do not commit, push, or publish without explicit authorization.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
