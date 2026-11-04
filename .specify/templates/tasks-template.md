---
description: "TDD task list for secure, reliable Minecraft modular synthesis"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md and spec.md; research.md, data-model.md, contracts/ as applicable
**Constitution**: `.specify/memory/constitution.md` (record reviewed version)

**Tests**: TDD is REQUIRED for every new or changed executable behavior. Each production
change depends on an observed, relevant failing test. Include regression tests for fixes,
then green and refactor validation. Documentation-only changes use consistency/link checks;
build/configuration changes use reproducible validation appropriate to their effect.

**Organization**: Group tasks by independently testable user story. Security, lifecycle,
persistence and musical acceptance belong with the affected behavior, not final polish.
All generated tasks MUST trace to requirements and Constitution Check gates C1–C10.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files and no dependency on incomplete work; a dependent implementation
  MUST NOT run in parallel with the test that establishes its red result.
- **[Story]**: Story identifier (US1, US2, US3); omit for shared setup and final checks.
- Include exact file paths, dependencies, commands/procedures and evidence destinations.
- Record PASS / FAIL / BLOCKED / PENDING honestly; N/A requires a scope rationale.

## Path Conventions

- Java production/tests: use the exact packages/source sets from plan.md.
- Pure Java tests MUST NOT require a running Minecraft client.
- GameTests/equivalent: use the selected loader's integration source set.
- Fixtures/evidence: use planned paths for reference patches, save fixtures and results.
- Paths below are examples, not a prescribed package or test-framework choice.

<!-- Replace all SAMPLE TASKS below with feature-specific tasks and actual paths.
Do not build unused infrastructure. Foundation behavior also requires red–green–refactor.
The examples demonstrate dependency ordering, not a mandatory module catalog. -->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish reproducible tools before changing behavior.

- [ ] T001 Record exact compatibility/toolchain versions and budgets in specs/[feature]/plan.md
- [ ] T002 Configure pinned build/test tools, dependency locks and verification in [build files]
- [ ] T003 [P] Configure formatting/static analysis and least-privilege CI in [config/CI paths]
- [ ] T004 Verify baseline build/test commands and record results in [evidence path]

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Only shared behavior required by the selected stories; all relevant design
and compatibility questions MUST be resolved before implementation starts.

- [ ] T005 Write domain/codec boundary tests and record expected failures in [test/evidence paths]
- [ ] T006 Implement minimal validated domain contracts in [Java paths] (depends on T005 red)
- [ ] T007 Write owning-thread/dedicated-server/lifecycle tests; record red in [test/evidence paths]
- [ ] T008 Implement required host adapters and bounded ownership in [Java paths] (depends on T007 red)
- [ ] T009 Refactor and run foundation suites; record green and review in [evidence path]

**Checkpoint**: Shared contracts and tests pass; no unsupported assumptions or open safety gates.

## Phase 3: User Story 1 - [Title] (Priority: P1) — MVP

**Goal**: [Player/musical outcome]
**Independent Test**: [Reproducible scenario and measurable outcome]

### Tests for User Story 1 (REQUIRED for behavior changes)

- [ ] T010 [P] [US1] Write signal/transport/graph behavior tests in [Java test path]
- [ ] T011 [P] [US1] Write hostile-input/permission/lifecycle integration tests in [host test path]
- [ ] T012 [US1] Run T010–T011, confirm expected failures and record red in [evidence path]

### Implementation for User Story 1

- [ ] T013 [US1] Implement minimal pure synthesis/domain behavior in [Java path] (depends on T012)
- [ ] T014 [US1] Integrate validated host/network/save/UI behavior in [Java paths] (depends on T012)
- [ ] T015 [US1] Run green suites and refactor; record results in [evidence path]
- [ ] T016 [US1] Add reference patch/docs in [fixture/doc paths]; execute applicable native/listening acceptance and record status in [evidence path]

**Checkpoint**: Story behavior, abuse/failure handling, and applicable musical acceptance
have evidence. Pending native/listening work remains explicitly pending.

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Outcome]
**Independent Test**: [Scenario and thresholds]

### Tests for User Story 2 (REQUIRED for behavior changes)

- [ ] T017 [P] [US2] Write behavior/regression tests in [Java test path]
- [ ] T018 [P] [US2] Write affected integration/migration/boundary tests in [host test path]
- [ ] T019 [US2] Run T017–T018 and record expected red in [evidence path]

### Implementation for User Story 2

- [ ] T020 [US2] Implement tested behavior and bounded failure handling in [Java paths] (depends on T019)
- [ ] T021 [US2] Refactor and run green/regression suites in [test paths]; record [evidence path]
- [ ] T022 [US2] Update reference patches/docs and complete affected native/listening checks in [fixture/doc/evidence paths]

**Checkpoint**: Stories 1 and 2 remain independently testable; shared contracts still pass.

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Outcome]
**Independent Test**: [Scenario and thresholds]

### Tests for User Story 3 (REQUIRED for behavior changes)

- [ ] T023 [P] [US3] Write behavior and affected safety/integration tests in [test paths]
- [ ] T024 [US3] Run tests and record expected red in [evidence path]

### Implementation for User Story 3

- [ ] T025 [US3] Implement tested behavior and compatibility handling in [Java paths] (depends on T024)
- [ ] T026 [US3] Refactor, run green/regressions, update fixtures/docs and record applicable native/listening evidence in [paths]

**Checkpoint**: Retained stories meet their acceptance cases; list any unverified acceptance.

[Add or remove story phases as required; keep IDs sequential and dependencies explicit.]

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Consolidate evidence and verify combined behavior; do not postpone feature safety.

- [ ] T027 Run complete formatting/static/build/resource and test gates; record [evidence path]
- [ ] T028 Exercise aggregate load, long transport runs, overload and repeated unload/reload; compare plan budgets in [evidence path]
- [ ] T029 Qualify packaged dedicated server/native client and two-client shared playback where applicable; record [evidence path]
- [ ] T030 Measure and audition affected reference patches, local mute and recovery; record [audio/evidence paths]
- [ ] T031 Review dependency/license/security inventory, rebuild provenance and compatibility/migration notes in [release paths]
- [ ] T032 Recheck C1–C10, quickstart and documentation; record reviewer decision and all pending/blocked acceptance in [review path]

## Dependencies & Execution Order

### Phase Dependencies

- Setup enables repeatable commands and MUST precede behavior implementation.
- Foundational work depends on Setup and blocks only stories that actually need it.
- Story implementation depends on its relevant observed red evidence.
- Final qualification depends on the selected completed stories and packaged artifact.

### User Story Dependencies

[Record the actual story dependency graph. Do not claim independence where shared APIs,
files or contracts require sequential implementation.]

### Within Each User Story

1. Specify contracts, numeric acceptance and reference patches where relevant.
2. Write meaningful tests; observe the intended failure and save evidence.
3. Implement the minimum behavior with validation, ownership, bounds and cleanup.
4. Run green suites, refactor, then repeat affected regression checks.
5. Validate host and musical behavior; update docs and evidence status.

### Parallel Opportunities

Independent fixture/test or documentation work may run in parallel. Implementation
requires its red evidence and stable prerequisite contracts. Integration and combined
resource/performance checks MUST run after their component changes are available.

## Parallel Example: User Story 1

```text
Independent: T010 signal contract tests and T011 host-boundary tests in separate files.
Then: T012 records the intended failures.
Then: T013/T014 implement behavior in dependency order; parallelize only if contracts permit.
Then: T015 verifies/refactors; T016 records player and musical acceptance.
```

## Implementation Strategy

### MVP First (User Story 1 Only)

Complete the minimum setup/foundation, then deliver Story 1 through red–green–refactor
and applicable host/music acceptance. MVP scope reduces features; it does not waive safety.

### Incremental Delivery

Add one independently useful story at a time and retain earlier regressions. Refresh
aggregate resource and compatibility evidence when a change affects those contracts.

### Parallel Team Strategy

Assign work only when contracts, test prerequisites and file ownership permit it. A
shared graph/transport or save-schema change requires coordinated integration evidence.

## Notes

- Tests verify observable behavior; coverage percentages alone do not establish correctness.
- Performance improvements MUST preserve musical behavior or explicitly version its change.
- Pure tests, GameTests, native gameplay, listening and hosted CI are distinct evidence.
- Required FAIL/BLOCKED/PENDING checks prevent the corresponding completion/release claim.
- Commit/publish actions follow explicit project authorization; task completion is not authorization.
