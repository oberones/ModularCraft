# PR #1 review follow-up — 2026-11-07

Reviewed all five unresolved Copilot comments on
[PR #1](https://github.com/oberones/ModularCraft/pull/1), against head
`38e150a20b805ec4b17c56602dd0998b1f3546fd`. All five were actionable.

| Review comment | Change and regression |
| --- | --- |
| [Quarantine reactivation](https://github.com/oberones/ModularCraft/pull/1#discussion_r4500158931) | Mark opaque records in the unused high bit of their schema-2 length prefix. Do not decode marked payloads. Preserve exact raw bytes, identity blocking, record counts and byte limits. Regression reproduces a schema-1 rejection becoming a live Speaker; repeated saves, empty/maximum/unknown records and an exactly 2 MiB world are covered. |
| [Native cable count](https://github.com/oberones/ModularCraft/pull/1#discussion_r4500158958) | Expect four RP-001 cables, including Amp → Speaker. Native remove/reconnect/remove assertions retain exact cable-set comparisons. |
| [Admission broadcast](https://github.com/oberones/ModularCraft/pull/1#discussion_r4500158989) | `WorldState.enable` returns all affected components; control handling includes admitted waiting patches in its immediate snapshot broadcast. |
| [Open panel freshness](https://github.com/oberones/ModularCraft/pull/1#discussion_r4500159016) | Shared change notifications refresh eligible inspectors in affected components after syncing entity state. Automatic admission and overload suspension deliver current status and edit revisions. |
| [RP-001 wiring](https://github.com/oberones/ModularCraft/pull/1#discussion_r4500159053) | Route Envelope Out to **Amp Gain Control In** in the player guide. |

## Red–green evidence

Observed before fixes:

- `make test TEST='*AmpMigrationTest'`: failed
  `quarantinedLegacyRecordsCannotBecomeCurrentSpeakersAfterResave` because the
  quarantined record became live on reload.
- `make gametest GAMETEST_DIR=run/make/pr1-red`: the added packet-capture test failed
  **Known waiting patch gets immediate admission snapshot**.
- After the admission fix, `make gametest GAMETEST_DIR=run/make/pr1-panel-red` failed
  **No inspector View delivered** during automatic load admission.
- `make smoke GRADLE_ARGS=-PqaCableRemoval TICKS=280 QA_DIR=run/make/pr1-cable-red`:
  failed at the obsolete **Expected three RP-001 cables** assertion.

The packaged notification test captures real server `Patch` and `View` payloads at the
send boundary using an in-process recording player. It seeds the listener's known roster,
checks the immediate edit broadcast, and suppresses periodic full broadcasts while testing
automatic admission. It injects an overload suspension into the runtime's existing pending
notification path and verifies that re-enable accepts the revision sent to the open panel.
This is packet/server integration evidence, not native two-client or overload-trigger stress
acceptance. Test-only reflection is confined to the GameTest source set.

## Self-review

- Kept quarantine framing within existing 4,096-byte payload, 1,024-record and 2 MiB
  limits. A first wrapper-byte implementation was replaced before final validation to
  avoid growing a world already at its storage limit. No recursive decoding or format
  reinterpretation occurs for opaque records; `WorldData.blocked` sees original identities.
- Playback admission retains already-active patches and UUID-stable waiting order.
  The original component is used for voice cleanup before expanding the broadcast set.
- Inspector updates retain range/eligibility and outbound byte-budget enforcement;
  unrelated panels no longer receive every control acknowledgment. The existing special
  Mixer acknowledgment remains intact.
- No dependency pins, manual worlds, DSP algorithms, quotas, or network version changed.
  Disposable validation worlds are under `run/make/pr1-*`.
- An initial verification attempt flagged the test's borrowed Minecraft server as a
  resource owner; documented its ownership and applied the existing PMD suppression
  convention. An accidentally overlapping rerun collided on test-report output; the final
  verification runs in isolation and supersedes those attempts.
- Changes remain local. No commit, push, publication, GitHub reply, or remote thread
  resolution is included. Existing listening, multiplayer, sustained-load, security and
  release qualification gates remain pending.

## Final validation

- **PASS:** `make verify GAMETEST_DIR=run/make/pr1-final`: 175 JUnit tests, 36 packaged
  GameTests; strict dependency verification, launcher tests, formatting, Checkstyle, PMD,
  build and artifact inspection. Full Amp load measurement: DSP p95 0.628125 ms,
  measured steady allocation 0 bytes.
- **PASS:** `make smoke GRADLE_ARGS=-PqaCableRemoval TICKS=280 QA_DIR=run/make/pr1-cable-green`:
  native dispatch and server acknowledgment at ticks 140/200/240, exact cable counts
  3/4/3. Screenshot at tick 200 inspected; all three captures remain in the disposable
  instance's `screenshots/` directory.
- **PASS:** final diff whitespace check and documentation link checks.
- **LIMITATION:** the short native probe recorded one underrun (maximum measured block
  2.015709 ms); this is functional cable validation, not acoustic or sustained qualification.
- Runtime JAR SHA-256: `6921b1c472e0d88966bb376a17b932bc7661497119eaf5a3b488a5b59e613a3c`.
- [Machine-readable results](pr1-review-results.json).
