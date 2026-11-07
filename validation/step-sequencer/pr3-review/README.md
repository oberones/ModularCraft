# PR #3 review remediation — 2026-11-07

Base: `5c7ff956ad7c956fcc5c890f99370aff6909c9bd` (`step_sequencer`), with the
uncommitted remediation recorded here. All four findings from
[PR #3](https://github.com/oberones/ModularCraft/pull/3) were reproduced and fixed.

| Finding | Fix | Regression evidence |
| --- | --- | --- |
| [Pending note cleared by unrelated ready views](https://github.com/oberones/ModularCraft/pull/3#discussion_r4503652225) | Keep awaiting/recorder state for editable ready views at or below the submitted revision. | Native widget checks: unchanged/older views, next-cell advance, duplicate acknowledgement, explicit rejection, permission loss, conflicting newer value, final-step completion, timeout cancellation. |
| [Stale numeric fields](https://github.com/oberones/ModularCraft/pull/3#discussion_r4503652245) | Track fields by parameter; refresh unfocused fields on accepted views and rebuild the map on initialization. | Native widget checks for all three fields, focused drafts, and resize. |
| [Replacement loses external clock](https://github.com/oberones/ModularCraft/pull/3#discussion_r4503652274) | Include Step Sequencer runtime identity in the rewiring guard. | Pure timeline tests for replacement/high-source priming, retained edge history, snapshot roundtrip; packaged block-removal GameTest. |
| [Same-sample stop/resume misses melody](https://github.com/oberones/ModularCraft/pull/3#discussion_r4503652288) | Compare a local accepted-clock event counter instead of timestamps. | Multi-step and length-one resume, rests, checkpoint recovery, rejected external-clock and pending-edit tests. |

## Reproduction and results

- **Expected FAIL before fixes:** `make test TEST='*StepSequencerTest'` reproduced
  three assertions: missing external mode, missing one-step gate, and stale next-note pitch.
  See [red-unit.txt](red-unit.txt). The first attempt had two invalid different-owner
  fixtures; [red-unit-fixture-error.txt](red-unit-fixture-error.txt) retains that setup
  error, which is not defect evidence. The corrected fixtures supplied the expected red result.
- **Expected FAIL before fixes:** `make smoke GRADLE_ARGS=-PqaStepSequencer TICKS=500
  QA_DIR=run/make/pr3-review-red-ui` reproduced five editor assertions covering both UI
  findings. See [red-ui.txt](red-ui.txt).
- **Expected FAIL before fixes:** `make gametest GAMETEST_DIR=run/make/pr3-review-red-tests`
  failed with `step_checkpoint` during block removal. See [red-gametest.txt](red-gametest.txt).
- **PASS after fixes:** all 12 focused Step Sequencer tests. See
  [green-unit.txt](green-unit.txt) and [JUnit results](StepSequencerTest.xml).
- **PASS:** `make verify GAMETEST_DIR=run/make/pr3-review-verify`: 25 launcher tests,
  201 JUnit tests, all 49 packaged GameTests, formatting, Checkstyle, PMD, strict dependency
  verification, build and artifact inspection. See [verify.txt](verify.txt).
- **PASS:** final `make format` / `make lint` after explicit-import cleanup in test fixtures;
  see [final-lint.txt](final-lint.txt). No executable behavior changed after full verification.
- **PASS:** `make smoke GRADLE_ARGS=-PqaStepSequencer TICKS=500
  QA_DIR=run/make/pr3-review-green-ui`. See [green-ui.txt](green-ui.txt).
  All 22 additional editor checks passed, as did the existing real input/recording,
  compact-layout, RP-009 and screenshot checks.

The new editor checks use real initialized native widgets with synthetic accepted/rejected
views and nonexistent module identities, so their outgoing edits cannot mutate the fixture.
They do not constitute two-client networking evidence. Timeout checks exercise the existing
two-second cancellation, a synthetic late acknowledgement and a fresh inspection screen;
they do not simulate packet loss through a second client. The existing probe separately
exercises real UI input, authoritative edits and their returned state in the disposable world.

All four screenshots were visually inspected: [full editor](step-full.png),
[compact editor](step-compact.png), [completed recording](step-recorded.png),
and [reference patch](step-world.png). Fields and controls remain readable and contained;
the compact capture uses a 380×240 logical screen within the native framebuffer.

## Environment and qualification limits

macOS 26.6.2 (25G83), Apple M2 Pro / arm64, Eclipse Adoptium Java 21.0.12.1+1-LTS,
Minecraft 1.21.1, NeoForge 21.1.218. Runtime JAR SHA-256:
`641c6865da71eaa90061ad0154f7f983347f5e9c03e1184faa631ebcc0dd3d70`.

The 500-tick four-module probe recorded final rolling audio p95 0.034083 ms,
maximum audio block 10.197458 ms and **one underrun**. Its 380-sample server measurement
reported p95 0.397959 ms / maximum 1.563542 ms; the broader server maximum counter reached
43.856083 ms. These observations are retained, not classified as a zero-underrun or sustained
performance pass. The full JUnit suite's existing 80-module DSP benchmark measured
0.734792 ms p95 and zero steady-state allocated bytes.

**PENDING:** human listening/acoustic, physical-input/two-client, zero-underrun,
30-minute sustained-load, and all prior security/platform/release qualification gates.

**Self-review PASS:** client changes retain one outstanding edit and explicit rejection;
no authority or packet changes. Rewiring remains preparation-only and primes current levels
without synthetic edges. The event counter is single-owner, allocation-free, increments only
on accepted ticks, and is intentionally absent from checkpoints. Runtime recovery compares
its own before/after counter, so no migration or wire change is required. Protocol 12,
schema 2, quotas and dependency pins are unchanged. Player worlds and AGENTS.md were preserved.
No commit, push, publication, review reply, or GitHub thread resolution was performed.
