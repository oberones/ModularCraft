# Euclidean redstone control evidence — 2026-11-07

Implemented EU-009: persistent opt-in redstone transport, immediate level reconciliation,
next-step resume, gray Start/Stop and server-side rejection, unaffected Reset/external clock,
complete-patch reload behavior, exact legacy migration, and protocol 10.

## Automated checks

- `make verify GAMETEST_DIR=run/make/redstone-verify`: **PASS**, 180 JUnit tests,
  41 packaged GameTests, 25 launcher tests, strict dependencies, formatting, Checkstyle,
  PMD, artifact inspection. [Full output](logs/verify.txt).
- [Parameter/persistence/codec tests](logs/SequencerRedstoneTest.xml) cover strict boolean
  values, defaults, world/block/snapshot round trips, schema-1/schema-2 migration, malformed
  quarantine preservation, old protocol refusal and missing network parameter refusal.
- Five new packaged tests cover power/mode switching, sustained/nonzero strengths,
  foreign/stale edits, rejected manual transport, NBT, complete-patch load reconciliation,
  external-clock resume and Reset. Existing tests retain dedicated-server/package isolation.
- [Timeline tests](logs/SequencerTimelineTest.xml) cover exact downstream gate release,
  next-step resume and Reset; existing sequencer tests retain pulse/timing/clock bounds.
- Local Markdown links, generated localization/source agreement and `git diff --check`: PASS.

## Red–green and corrections

[Three focused JUnit failures](logs/junit-red.txt) and [packaged failures](logs/gametest-red.txt)
were observed before implementation: REDSTONE_MODE did not exist. The native probe first
caught playback-status text overlapping the new toggle; moving the status to the title row
resolved this in both ordinary and compact screenshots.

The dust fixture needed its placement update to finish before assigning a fixed signal
strength. Its progress check now waits for the monotonic musical clock, rather than assuming
a fixed count of accelerated GameTest ticks advances musical time. One intermediate full
run also failed the existing sneak-removal test; subsequent packaged and final full runs
passed it without changing cable implementation or tests. No required check was disabled.

## Native checks

`make smoke GRADLE_ARGS=-PqaSequencer TICKS=780 QA_DIR=run/make/redstone-native-final`:
**PASS**, packaged JAR, disposable world, keyboard Tab/Enter toggle, powered/unpowered
playback snapshots, disabled Start/Stop with active Reset, mouse mode exit, compact layout,
and 20 teardown/recovery cycles. [Native output](logs/native.txt).

Reviewed native screenshots:

- [Mode enabled, stopped](screenshots/sequencer-redstone-235.png)
- [Mode enabled, running; Start/Stop gray](screenshots/sequencer-redstone-265.png)
- [Manual mode restored](screenshots/sequencer-redstone-340.png)
- [Compact layout](screenshots/sequencer-354.png)

The 780-tick probe recorded **one underrun**, maximum audio block **2.404792 ms**, and
maximum server work **5.678042 ms**. Largest reported audio p95 was **0.139375 ms**;
server p95 was not measured. These are short native UI diagnostics, not sustained-load,
acoustic or two-client qualification. See [machine-readable results](results.json).
Human listening, multiplayer/timing, sustained/tail/underrun qualification and earlier
security/platform/release gates remain **PENDING**.

## Self-review

PASS: common/server code remains independent of client classes; only accepted server edits
persist mode; manual transport is refused while controlled; Reset is retained; signal
reconciliation is bounded and publishes affected snapshots. A two-block neighborhood load
check prevents vanilla conductor queries from implicitly fetching neighboring chunks.
Parameter ordinals are append-only, DSP checkpoints are unchanged, legacy migration is
stored-only/exact, and all peers must use protocol 10. UI state follows acknowledgements
and preserves edit permission/pending restrictions. Generated localization agrees with
its source. Existing AGENTS.md, dependencies and player worlds are preserved.

No commit, push, installation into a play instance, or publication was performed.
Runtime JAR SHA-256: `9576146785910cba3bea8313fc817818939f5d5c3e3737ab4014c1d4309a2667`.
