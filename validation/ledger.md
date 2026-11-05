# Evidence ledger

This is an **uncommitted development candidate**, not an accepted release. Source baseline:
`28f5d5c6ee52d62eb55ad2d1b98c64fc1e1d6acb`, branch `001-modular-synth-mvp`. Existing Spec Kit
changes predate this implementation and were preserved. No commit, push, or public
publication was performed. Qualification occurred on November 3–4 local / November 4 UTC.
The exact final artifacts and hashes are in `build/libs/SHA256SUMS`; the project ZIP
contains the rebuildable source tree, tests, editable artwork, and this evidence.

## Results by evidence class

| Class | Status | Observed evidence and limit |
|---|---|---|
| Build and code checks | PASS | Java 21 compilation, google-java-format, Checkstyle, PMD, `check build`; warnings treated as errors except host deprecation/annotation-processing noise |
| Automated DSP/domain/transport | PASS | 35 JUnit tests, zero failures/errors/skips; named cases and measurements in [results.json](results.json) |
| Host integration | PASS | Seven NeoForge GameTests: identities/permissions, persistence/replacement, gate-release queue behavior, unknown block data preservation, malformed packets, copied item NBT, packaged origin |
| Packaged headless loading | PASS | Actual installable JAR loaded by a headless GameTest server; all seven tests pass; no client/audio class resolution failure |
| Packaged singleplayer | PASS | Actual JAR loaded on macOS, RP-001 streams finite PCM, 20 local audio resynchronization cycles return owned channel/timer counters to zero |
| Dependency integrity | PASS | All 478 verification entries match official origins; distribution and wrapper JAR checksums pinned and verified |
| Dependency security qualification | BLOCKED | 35 OSV advisories across 143 binary coordinates; host-matrix exposure review remains open, including high/critical matches; [assessment](security-review.md) |
| Artifact/source checks | PASS | Test fixtures excluded from runtime/source JAR executable contents; original assets complete; Java major version 65; full project archive separate |
| Reproducibility | PASS | Extracted project archive rebuilt offline with `--no-build-cache --rerun-tasks`; runtime JAR, source JAR, and project ZIP matched byte-for-byte; final hashes and comparison are in `build/libs/verification.json` |
| Human listening observation | PASS | User reported: “I didn't notice any problems with the sound during play testing.” This concerns the earlier development client, not formal final-artifact RP-001/RP-002 acceptance |
| Formal listening / SC-002 | PENDING | Both complete reference patches, all variations, independent final-JAR listening checklist |
| Device latency / SC-003 | PENDING | No aligned 100-action acoustic capture, ten-minute gap measurement, or measured mute latency |
| Two-client timing / SC-004 | PENDING | No two-macOS-client dedicated-server session or aligned capture clocks; arithmetic clock tests do not prove physical output skew |
| Load qualification / SC-005 | PENDING | One-minute single-client 64-module probe observed; thirty-minute two-client active/inactive comparison and frame-time thresholds outstanding |
| Server work ceiling | FAIL | Native startup crossed the 2 ms/tick target (about 2.47–10.60 ms across probes); ordinary reported ticks were tens of microseconds. Full-load qualification is still required |
| Persistence / SC-006 | PENDING | Twenty codec roundtrips and twenty native audio recovery cycles pass; twenty full world restarts and unload/reconnect cycles have not been performed |
| Device switching / pause / spatial controls | PENDING | Implementation and partial numeric/lifecycle coverage exist; final native device-loss, pause, category-volume, spatial, and eligibility-change checklist remains outstanding |
| First-time guide / SC-001 | PENDING | Five first-time users have not completed the timed guide test |
| Hosted CI | PENDING | Pinned macOS workflow is present; no remote run was requested or observed |
| Other loaders / Windows / Linux playback | N/A | Explicitly deferred by the approved plan; no support claim |

## Commands and retained records

```sh
./gradlew formatJava check build runGameTestServer --dependency-verification strict
./gradlew runGameTestServer -Ppackaged -PgameTestRunDir=run/packaged-final-server --dependency-verification strict
./gradlew runClient -Ppackaged -Pqa -PqaCycles=true -PclientRunDir=run/packaged-final --dependency-verification strict
python3 scripts/check-delivery.py
python3 scripts/verify-upstream.py
python3 scripts/audit-dependencies.py
```

Native fixtures use separate disposable run directories, never a personal save. The
user-created `run/client/saves/New World` was preserved. Fixture code is test-only and
excluded from the runtime artifact. See [acceptance.md](../docs/acceptance.md) for setup
and the remaining procedures. Raw logs remain under `validation/*.log` locally; the
source archive includes structured summaries rather than verbose host/user logs.

The red results were observed before their corresponding implementations. [results.json](results.json)
records expected failures and SHA-256 hashes for `core-red`, `integration-core-red`,
`host-red`, `audio-runtime-red`, `alias-red`, `host-regression-red`, `inbox-red`,
`placement-red`, `scene-red`, and `traffic-red` logs. Final green evidence is
`final-automated.log`, `packaged-final-server.log`, and `packaged-final-client.log`.
One early drone test sampled before its 10 ms smoothing ramp settled; the fixture was
corrected to allow ten blocks. This was a test setup correction, not a musical exception.

## Numeric and native observations

[environment.json](environment.json) records a MacBook Pro Mac14,9, M2 Pro (12 cores),
16 GiB RAM, macOS 26.6.2 (25G83), Temurin 21.0.12.1+1 arm64, and Scarlett 18i8 USB.
Development probes set render distance 12, simulation distance 8, VSync off and no shaders.
The actual framebuffer resolution was not independently captured; therefore the required
1080p benchmark baseline is not claimed. Audio generation is 48 kHz / 128 frames.

- MU-009 filter comparison: 2–8 kHz energy attenuation **−30.430 dB**.
- Alias residuals at coherent pitches near 55/220/880/3,520 Hz: **−70.779, −65.616,
  −61.597, −49.686 dB** relative to the oversampled reference. Mean DC passes −60 dBFS.
  The initial 48 kHz PolyBLEP failed at −32.769 dB near 880 Hz; four-times internal
  oversampling and a 95-tap decimator corrected it. See `alias-red.xml` and measurement code.
- Exact 55/220/880/3,520 Hz pitch tests render sixty seconds each. ADSR linearity,
  retriggers, one-sample stage edges, control ramps, finite extremes, and full-scale
  output protection pass. One-hour arithmetic transport has sub-millisecond error;
  this is not a device-clock drift measurement.
- Final offline 64-module / 96-cable / eight-speaker fixture: **0.180125 ms p95** per
  block, **zero measured steady application allocation bytes** on this JVM. The test
  uses a warmed virtual workload and does not measure Minecraft frame timing.
- Earlier one-minute native maximum-load probe: **about 0.214 ms rolling p95**, maximum
  observed processing 4.779 ms, one startup underrun, peak 1.0 protected output, at most
  twelve queued buffers (**32 ms**). The final packaged RP-001 recovery probe is recorded
  separately in [native-results.json](native-results.json). No acoustic dropout duration
  was recorded; an underrun counter is not proof of a >100 ms gap or its absence.
- Twenty final packaged local audio recovery cycles each reported **zero owned channels
  and zero wakeup timers** immediately after teardown. These are resynchronizations,
  not twenty world restarts, device changes, or chunk unloads. Post-recovery counters
  describe the last AudioOutput instance, not aggregate underruns across all instances.

The earlier packaged probe used runtime SHA-256
`2ed3ef78417f5e8975dc08cd5bb03b56da8bd5940e0b5dec5638f849ca0dcff4`.
The final packaged probe uses
`83057484e55b534de4870164957fbc11652e1e30d1ee63de9bef7affd7a9b567`.
The subsequent archive/reproducibility steps do not alter runtime Java/resources.

## Self-review and release boundary

Codex self-reviewed client/server class separation, server authority, thread ownership,
codec bounds, graph/owner/revision rejection, opaque-save preservation, resource cleanup,
asset provenance, and distributable contents. The review caught and fixed copied-item
identity injection, aggregate client-scene quotas, and unbudgeted recovery messages.
Host source inspection confirmed payload registrar thread selection returns a copy:
edit admission runs on the network thread; other handlers retain main-thread ownership.

The only added native access is to Minecraft's existing OpenAL channel/executor. No
second audio device, external media service, runtime download, or additional bundled
native binary was added. Five client-only mixins are pinned to this host version.
See [architecture.md](../docs/architecture.md) for the necessity and ownership model.

The server's 1.5 ms edit-drain slice is an admission budget, not proof that total work
stays below 2 ms. The audio sample cursor follows the device after its initial synchronized
anchor and resets on recovery; physical device-clock drift over long sessions remains
unmeasured. Opaque corrupt records remain bounded and preserved but cannot all be assigned
trusted owners for per-owner quota accounting. These limits and all pending rows above
must be resolved or explicitly reviewed before claiming release acceptance. A clean,
identified source revision and hosted CI are also required for a public release.

## Makefile follow-up — 2026-11-04

Added the default-help Makefile, version-independent Gradle runtime artifact descriptor,
managed packaged launcher, and [development command guide](../docs/development.md).
The runtime JAR remains SHA-256
`83057484e55b534de4870164957fbc11652e1e30d1ee63de9bef7affd7a9b567`;
this follow-up changes tooling/documentation, not musical runtime behavior.

- **PASS:** Eight launcher contract tests first failed for the expected missing behavior
  (`makefile-red.log`), then passed. Three additional characterization cases cover
  exclusive instance locks, operator-managed EULA state, and invalid artifacts/symlinked
  seeds. A further red/green regression (`makefile-ci-red.log`) ensures `make ci` retains
  `--no-daemon` even when `GRADLE_ARGS` is overridden. All **12 Python launcher/tooling
  tests** pass in `makefile-green.log`.
- **PASS:** `make verify`, including existing Java quality gates and all seven headless
  GameTests using `run/make/gametest/mods/modularcraft-0.1.0.jar`; origin is printed in
  `makefile-verify.log`. Java runtime sources were unchanged, so cached/up-to-date Java
  checks were retained rather than described as newly executed tests.
- **PASS:** `make smoke-recovery TICKS=400` launched a fresh managed native fixture from
  the actual JAR and exited automatically. Twenty recovery cycles each reported zero
  owned channels/timers; final output instance reported zero underruns. The native log
  is `makefile-native.log`. This is a launcher/recovery smoke check, not new listening or
  multiplayer acceptance.
- **PASS:** GNU Make 3.81 help and dry runs cover client/server, test selection, lint,
  no-daemon CI, and clean. `make clean` invokes only Gradle's build-output cleanup.
  The launcher tests cover exact JAR staging, managed version replacement, save retention,
  paths with spaces, unmanaged-directory/symlink refusal, seed bootstrap, and subprocess
  failure propagation. No live normal-server EULA was accepted for this check.
- **PASS:** Local `make ci GRADLE_ARGS=--offline` retained no-daemon mode in both Gradle
  invocations and passed the launcher tests, build/checks, packaged GameTests, and archive
  inspection (`makefile-ci.log`). This does not represent a hosted workflow run.

Structured evidence is in [makefile-results.json](makefile-results.json). Source archives
and checksums include the Makefile and launcher tests. The workflow now invokes `make ci`;
its hosted execution remains PENDING. Earlier failed/pending release gates remain open.

## Patch cable appearance — 2026-11-04

Source: uncommitted changes based on `760e492`. Runtime JAR SHA-256:
`1e8cbedd14364ebfadc8cf0fc9ed2cb9881870822d32ce4d06951289fb6c4867`.
Environment: macOS 26.6.2 arm64, Eclipse Adoptium Java 21.0.12.1+1-LTS,
Minecraft 1.21.1 / NeoForge 21.1.218; default renderer, no shaders, VSync off,
render distance 12, simulation distance 8. No dependency pins or save/network schemas changed.

- **PASS — regression reproduction:** extracted the original port/curve calculations
  without changing their behavior, then observed both jack-height and ground-clearance
  tests fail under `make test TEST='*CableGeometryTest'` (`cables-red.log`).
- **PASS — geometry:** six JUnit tests cover jack positions on all four facings and all
  port counts, 24 segments/eight-sided constant-diameter tube rings, attached collar
  taper direction, full-mesh floor clearance across facing/elevation combinations,
  vertical/near-vertical/short/coincident/16-block connections, and independent fan-out.
  The targeted run passed in `cables-green.log`; `make check` passed all **41 JUnit**
  and **12 Python** tests plus formatting, Checkstyle, and PMD (`cables-check.log`).
- **PASS — packaged verification:** `make verify` passed build/checks, all **seven
  packaged headless GameTests**, and archive inspection (`cables-verify.log`).
- **PASS — native visual inspection:** `make smoke GRADLE_ARGS=-PqaCables TICKS=600`
  loaded the above packaged JAR in fresh `run/make/smoke-duw693p2`, built the adjacent
  RP-001 row on solid ground, captured front/oblique/rear views, and exited normally.
  Reviewed captures show three solid cables with jack-centered endpoints, attached
  input collars, no arrowhead branches, clearance above the floor, and correct hiding
  behind opaque modules. Cyan/amber cables may still cross, as the contract permits.
  See [front](cables/front.png), [oblique](cables/oblique.png), and
  [occlusion](cables/occlusion.png); native log: `cables-native-final.log`.
- **PASS — self-review:** rendering remains client-only; geometry is cached by endpoints
  and rebuilt on facing/position changes, pruned when cables disappear, and cleared on
  client teardown. Mesh construction is bounded per cable. Camera-relative local
  vertices avoid losing cable thickness at large world coordinates. Lighting checks
  chunk availability before reading it. Existing manual worlds were not used as fixtures.
- **PENDING — broader acceptance:** multiplayer, other graphics platforms/shader packs,
  maximum-load frame-time qualification, and prior acoustic/listening/security gates
  are not established by this visual fix. Native smoke output is diagnostic only and
  is not a new audio-performance acceptance result. Arbitrary-terrain cable routing
  remains outside scope; floor clearance refers to the lower connected block's base.

No commit, push, or publication was performed. Screenshots are retained in the checkout;
native worlds/logs remain under their fresh managed run directories.


## Keyboard extension — 2026-11-04

The extension adds the fifth block, typed CV/gate connections, octave persistence, protocol
v2 performance snapshots, monophonic piano UI and exclusive server performance sessions.
World-save schema remains v1; dependency pins and original worlds are preserved.

- **PASS — automated checks:** `make verify`: 53 JUnit tests, 10 packaged GameTests,
  launcher tests, Java formatting, Checkstyle, PMD, Java 21 compilation, archive and
  five-module resource inspection. Final hashes are in `build/libs/SHA256SUMS`.
- **PASS — native GUI and network state:** `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`
  loads the actual packaged JAR in a new RP-003 world. It exercises overlap/fallback,
  octave transposition, retained release pitch, black/white mouse dragging, outside
  release, audio-settings return, rapid note/octave acknowledgement ordering and close.
  See [native observations](keyboard/native-results.json) and [red–green evidence](keyboard/red-green.md).
- **PASS — visual inspection:** [idle](keyboard/idle.png), [held octave](keyboard/octave-held.png),
  [released](keyboard/released.png), [mouse drag](keyboard/mouse-drag.png),
  [block appearance](keyboard/blocks.png). Labels, shortcuts and piano bounds are readable;
  the keyboard face uses the existing chassis and original editable SVG artwork.
- **PASS — self-review:** checked typed cable compatibility, saved-data preservation,
  protocol mismatch rejection, session/sequence validation, release ordering, bounded
  queues/leases, phase-preserving pitch transitions and client-only UI isolation. Native
  inspection found and fixed text blurring and a rapid octave acknowledgement race.
- **PENDING — RP-003 human/acoustic qualification:** listening for transitions/tails,
  physical input timing, two-client SC-004/late join, device/focus-loss and extended
  lifecycle/load acceptance. Synthetic GUI input and native PCM counters do not replace these.
- **Retained limitations:** the native run recorded startup underruns and maximum server
  work above the 2 ms target (exact counters in the JSON). Existing dependency-security
  and broader release-qualification gates remain BLOCKED/PENDING; no release claim.

No commit, push or publication was performed.


## MIDI keyboard input — 2026-11-04

Implementation uses Java Sound with no added dependencies, exact MIDI notes 0–127,
channel-scoped sustain/reset, combined GUI/MIDI priority, remembered local preferences,
and bounded asynchronous device lifecycle. Network/snapshot v3 requires matching peers;
world v1, dependency pins and manual worlds remain unchanged. Source is commit `0450bc0`
plus the working tree; the assistant did not create that commit or push/publish anything.

- **PASS — TDD and automated verification:** `make verify`: 71 JUnit tests, 12 packaged
  GameTests, 12 launcher tests, formatting, Checkstyle, PMD, Java 21 compilation and
  artifact/resource inspection. [Red–green record](midi/red-green.md).
- **PASS — native synthetic MIDI/UI:** `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`
  exercises device/Refresh/channel controls, MIDI note/pedal delivery, octave independence,
  release during an octave acknowledgement, GUI overlap/dragging, settings return, range
  loss and panel close. [Artifact hash, environment and observations](midi/native-results.json).
- **PASS — visual inspection:** [controls](midi/controls.png), [sustain](midi/sustain.png),
  [release](midi/released.png). All controls/status text fit the default 854×480 window
  at GUI scale 2. The native process discovered and opened **Scarlett 18i8 USB**; the
  earlier sandboxed discovery scan exposed only internal devices.
- **PASS — explicit self-review:** inspected server authority, bounds and generation
  revocation, MIDI channel/hold/pedal identity, provider timeout/cancellation/partial-open
  ownership, non-blocking shutdown, dedicated-server loading, full-range DSP finiteness,
  old-protocol refusal and world roundtrip. Fixed null selection on immutable port lists,
  obsolete connection opens, late/persistent close failures, and range-loss local-input cleanup.
  PMD exceptions are limited to unopened provider capability queries, explicit worker-owned
  connection cleanup, and asynchronous executor shutdown; lifecycle tests cover ownership.
- **PENDING — physical/human qualification:** actual controller note/pedal messages,
  unplug reporting, real-device 20-cycle resource accounting, listening/acoustic SC-003,
  two-client SC-004, other operating systems, and existing release gates. Synthetic input
  and an opened hardware port do not establish these results.
- **Retained performance limitations:** the short native run recorded one startup underrun,
  maximum audio-block processing about 4.43 ms and maximum server work about 10.75 ms,
  above the existing 2 ms server-work target. This fixture is not SC-005/load qualification.
  Existing security/performance BLOCKED/PENDING results remain in force.

No assistant commit, push or publication was performed. The final runtime JAR hash is
recorded in the linked JSON; archive hashes are refreshed in `build/libs/SHA256SUMS`.

## RK006 MIDI connection diagnosis — 2026-11-04

Source `b4f7377` plus this uncommitted fix; [detailed evidence](midi/rk006-diagnostics.md).

- **PASS — physical Java Sound ingress:** player-assisted 90-second IN_ALL capture
  received 191 Note On/Off messages on channel 1. Device/channel selection is confirmed.
- **FAIL — live native lifecycle:** two thread dumps show the game's MIDI worker
  blocked in `MidiInDevice.nStop` during health-failure close. An isolated 20-cycle
  RK006 probe completed operations but retained one input thread after every close
  against a zero-thread baseline. This supersedes any assumption of hardware lifecycle
  qualification; the native hang itself remains unresolved.
- **PASS — red–green/status fix:** a new test first reproduced Refresh/focus/channel
  changes hiding the timeout as Connecting. It now retains timeout status and restart
  guidance while the native call is outstanding. Late callbacks remain invalidated;
  no replacement worker is created. Explicit self-review is in the linked evidence.
- **PASS — `make verify`:** 72 JUnit, 12 packaged GameTests, 12 launcher tests, formatting,
  static analysis and artifact inspection. Runtime JAR SHA-256:
  `4acf4138ad4cf2ab9ee538ad20412fb78a65738757a4e9b1389d468e847106e2`.
- **PENDING — in-game physical MIDI listening/recovery**, sustain, resource-lifecycle
  remediation, two-client timing and prior acceptance/security/performance gates.
  No second Minecraft smoke client was launched during the player's active session.

The running client and manual worlds were left untouched. No commit, push or publication.

## MIDI silent-reopen remediation — 2026-11-04

Source `b4f7377` plus uncommitted work; [evidence and self-review](midi/reconnect-fix.md).

- **PASS — reconnect fix:** native Java Sound handles/readers now live in one bounded Java
  helper per connection. The single client worker polls input between game ticks and owns
  all lifecycle work; it reaps the old helper before a new open. Blocked native close is
  contained without blocking Minecraft or creating replacement client workers.
- **PASS — red–green/full verification:** cached-reader reconnect, idle input polling and
  pre-queue channel selection each failed before their fix. `make verify` passes 81 JUnit,
  12 packaged GameTests, 12 launcher tests, formatting, static analysis and artifact checks.
- **PASS — real RK006 cleanup:** 20 IN_ALL/channel-1 open/health/close cycles returned live
  helper and parent input-thread counts to zero after each bounded close. Abrupt test-parent
  exit also left no orphan helper. This addresses the earlier failed lifecycle baseline.
- **PASS — packaged native smoke/UI:** fresh keyboard fixture passes note/sustain/GUI/
  settings-return/range-loss assertions; inspected screenshot shows a connected helper.
- **PENDING — physical post-reopen notes/listening**, sustain/unplug and SC-003/004 timing.
  The synthetic fixture and hardware resource checks do not establish these. Existing
  security/performance gates remain: startup underruns 1, max audio block 7.75 ms and server
  work 6.09 ms in this short fixture.

Runtime JAR SHA-256: `3ed718d239657a83becd1d02e72ad0d25da12088600ed025815a6fef65f28059`.
No changes to user worlds, running client, dependency pins or save/network formats.
No commit, push or publication.


## Player-assisted RK006 reconnect capture — 2026-11-04

- **PASS — physical adapter input before/after close:** restarted capture received 327
  events during the first 35-second window and 317 during the second window after fully
  closing and reopening IN_ALL on UI channel 1. Both phases ended `midi_ready`; after each
  close, live helper count was zero. The capture process exited with code 0.
- The player accidentally closed Minecraft and requested the capture restart. This test
  runs the production adapter independently of Minecraft; closing the game did not prevent
  it from receiving physical notes. An earlier interrupted capture is excluded from the
  before/after comparison because its first window contained no playing.
- **PENDING — in-game physical panel-reopen/listening acceptance**, sustain/unplug and
  SC-003/004 timing. Physical native-adapter reconnect is now established separately from
  the earlier synthetic in-game fixture. [Updated evidence](midi/reconnect-fix.md).
- **Self-review:** distinguish standalone hardware delivery from screen/network/DSP/audio
  acceptance; retain the recorded runtime candidate hash and existing release gates.
  No production code changed, no game was launched, and no commit/push/publication occurred.

## Module label blur correction — 2026-11-04

Source `b4f7377` plus existing uncommitted work and this two-screen correction.

- **FAIL — visual baseline:** the [player-supplied envelope screenshot](ui-labels/before.png)
  shows blurred title, parameter labels/ranges and status text alongside sharp widgets.
  Inspection of the pinned NeoForge/Minecraft `Screen` source confirms that the previous
  trailing `super.render(...)` applied background blur after these custom elements.
- **PASS — correction and self-review:** `ModuleScreen` and `AudioSettingsScreen` now
  override `renderBackground`, call `super.renderBackground(...)` first, then draw their
  existing panel/text. Inherited `Screen.render` renders widgets afterward, once. This
  matches `KeyboardScreen`; wording, geometry, parameter/edit behavior, server authority,
  save/network schemas and dependency pins are unchanged. Changes remain client-only.
- **PASS — `make check`:** 81 JUnit tests (zero failures/errors/skips), 12 launcher tests,
  formatting, Checkstyle and PMD. Initial sandbox execution could not create the Gradle
  cache lock; the rerun with cache access passed.
- **PASS — `make build`:** checks and runtime/source/project archives completed. Runtime
  JAR SHA-256: `3d815a9f16fea6862d25046e9e774273eb73e36673ba80f45801240d8bee66d2`.
- **PASS — packaged startup only:** `make smoke TICKS=18000 QA_DIR=run/make/labels-20261104`
  loaded that staged JAR, joined the fresh world and created the four-module/three-cable
  fixture on macOS 26.6.2, Apple M2 Pro, Java 21.0.12.1, NeoForge 21.1.218. The console
  identified the packaged artifact and reported `failed=false` audio output. This does
  not establish visual, acoustic, performance or listening acceptance.
- **BLOCKED — native UI access:** the computer-control inventory did not expose the Java
  game window and `getApp("java")` returned `Invalid app: java`. Only the new QA process
  was stopped, after verifying its working directory; the server saved all dimensions.
  The deliberate SIGTERM produced exit 143, so the smoke command is not recorded as a
  completed pass. Its world/logs remain in `run/make/labels-20261104/`.
- **PENDING — native visual/interaction acceptance:** inspect oscillator, filter,
  envelope, speaker and local audio panels with blur enabled at multiple GUI scales;
  save after screenshots and verify slider dragging, numeric Apply and keyboard navigation.
  The supplied before screenshot is retained; no after screenshot was captured.

Existing manual worlds and unrelated changes were preserved. Earlier release/security/
performance/listening gates remain unchanged. No commit, push or publication.

## Saw oscillator unison detune — 2026-11-04

Source `b438683` plus this uncommitted feature. [Evidence and self-review](detune/red-green.md),
[machine-readable results](detune/results.json), [native panel](detune/fractional.png).

- **PASS — behavior:** Detune (unison spread), 0–100 cents per side, fractional entry,
  three normalized mono saws at nonzero spread, original center saw at zero. Keyboard/MIDI
  sets the center; pitch/mix transitions take 10 ms and retain phase/history.
- **PASS — compatibility:** old Frequency/Level-only world/block records gain Detune 0;
  complete current records roundtrip and malformed/unknown records remain preserved.
  World/block schema stays v1; network/snapshot v4 requires matching clients/server.
- **PASS — red–green and full verification:** 91 JUnit, 13 packaged GameTests, 12 launcher
  tests, formatting/static analysis and artifact inspection. Dedicated-server integration
  covers accepted/rejected edits, packet roundtrip and block NBT reload.
- **PASS — packaged native UI:** disposable 300-tick fixture checks exact entry, slider,
  invalid input and zero reset; inspected screenshots show readable controls at 854×480.
- **PASS — bounded DSP measurement:** existing 64-module/96-cable/eight-speaker fixture
  p95 0.185 ms at zero spread, 0.275 ms at maximum spread, zero steady allocated bytes.
- **PENDING — listening/physical MIDI audition/two-client acceptance**, all earlier
  security/release/performance gates. Smoke observed one underrun and a 3.326 ms maximum
  server-work sample; it does not establish long-duration or multiplayer qualification.

Runtime JAR SHA-256: `8661df364c777d7f9b972e43312ff2fc016c187162707f83a42457952f376d2e`.
Manual worlds preserved; no commit, push or publication.

## Four-channel Euclidean sequencer — 2026-11-04

Source `c2ac2f0` plus this uncommitted feature. [Evidence and self-review](sequencer/review.md),
[machine-readable results](sequencer/results.json), [native editor](sequencer/editor.png),
[block and cables](sequencer/north.png).

- **PASS — implementation:** four independent steps/hits/rotation/mute channels; 10 ms
  triggers, internal/external clock and reset, exact sample-timed envelope integration,
  transport, next-edge edits, checkpoint recovery and bounded overload suspension.
- **PASS — compatibility/authority:** world/block v1, validated module-specific parameter
  counts, existing migration/quarantine and graph/permission/revision bounds. Network and
  snapshots now require v5; saves contain settings/cables, never running state/playheads.
- **PASS — full verification:** `make verify`; 108 JUnit, 15 packaged GameTests, 12 launcher
  tests, formatting/static analysis and six-module artifact inspection. Red–green logs and
  an explicit self-review are recorded in the linked evidence.
- **PASS — native functional/visual probe:** `make smoke GRADLE_ARGS=-PqaSequencer TICKS=650`;
  exact entry, mute/stop/resume, compact scrolling, all four orientations, cables and 20
  native audio cleanup cycles. Final screenshots were inspected. Manual worlds preserved.
- **PASS — synthetic DSP/allocation:** four maximum patches, 64 modules/96 cables/eight
  speakers, 300 BPM and maximum detune: p95 0.240 ms/block (four channels), 0.216 ms/block
  (clock/reset chains), zero measured steady allocation. Retained target is <1 ms/block.
- **FAIL — native performance acceptance:** final probe observed one audio underrun and
  13.640 ms maximum server work against the <2 ms target. Sampled steady work was lower;
  this does not establish sustained-load qualification or erase the observed maximum.
- **PENDING — human RP-004 listening, two-client timing/late join and 30-minute sequencer
  load qualification**. Existing security, other-platform, hosted-CI and release gates
  remain unchanged; this feature is not a release-acceptance claim.

Runtime JAR SHA-256: `750d473afbe8a54862f4a747432fdd8ee65a2dd6d740a7df5d3572d61868cd03`.
Minecraft 1.21.1, NeoForge 21.1.218, Java 21 and dependencies remain pinned.
No commit, push or publication.


## Oscillator note and octave controls — 2026-11-04

Base `b58caec` plus this uncommitted change. [Evidence and self-review](detune/notes-review.md),
[results](detune/notes-results.json), [compact panel](detune/notes-compact.png).

- **PASS — implementation:** chromatic Note/Octave dropdowns, A4 = 440 Hz preview and explicit
  Set Note, E0–B8 limits, full-precision server edits, retained drafts, permission gating,
  keyboard/narrated controls and compact layout. Hz tuning remains available.
- **PASS — compatibility:** only Frequency is persisted; world v1, protocol/snapshot v5,
  dependency pins, CV precedence and DSP remain unchanged. No migration is needed.
- **PASS — verification:** `make verify`; 115 JUnit, 16 packaged GameTests, 12 launcher
  tests, formatting/static checks and artifact inspection. Red–green evidence linked above.
- **PASS — native functional/visual probe:** `make smoke GRADLE_ARGS=-PqaDetune TICKS=600`,
  exact accepted frequencies, arbitrary Hz, invalid range, permission loss, drafts,
  Escape/Tab navigation, normal/compact screenshots and minimum-size control bounds.
- **PENDING — listening, spoken narrator audition and two-client acceptance.** Existing
  security, platform and release gates remain open. Native performance acceptance remains
  **FAIL/unqualified**: one underrun and 3.293 ms maximum server work in this smoke run.

Runtime JAR SHA-256: `b85ddc3828d8b6c464e388beb4463098584b0d40ae68cec287305adf108e28df`.
Manual worlds preserved. No commit, push or publication.


## Square oscillator with manual PWM — 2026-11-04

[Evidence and self-review](square/review.md), [results](square/results.json),
[panel](square/panel.png), [compact layout](square/compact.png).

- **PASS — implementation:** separate Square Oscillator, 5–95% fractional PWM (50% default),
  shared Frequency/note/octave/Level and pitch precedence, matching SQR assets and ports.
  Single-voice DC-compensated pulse DSP with phase/history retention and 10 ms edits.
- **PASS — compatibility:** world v1, unchanged legacy saw migration, complete square records,
  quarantine preservation, appended enums and matching network/snapshot v6 peers.
- **PASS — verification:** `make verify`; 124 JUnit, 19 packaged GameTests, 12 launcher tests,
  formatting/static checks and seven-block artifact inspection. Red–green evidence retained.
- **PASS — DSP:** alias residual worst -86.279 dB in the measured pulse fixtures, DC below
  -60 dBFS; synthetic square/mixed load p95 0.211/0.261 ms, zero steady allocation.
- **PASS — native functional/visual:** disposable `make smoke GRADLE_ARGS=-PqaSquare TICKS=600`;
  fractional PWM, slider/bounds, rejection, note selection, keyboard navigation and compact
  screenshots. Actual packaged JAR used; manual worlds preserved.
- **FAIL/unqualified — native performance:** one underrun and 2.975 ms maximum server work
  against the <2 ms target. Final sampled audio p95 was 0.0363 ms; this is not a sustained-load pass.
- **PENDING — human RP-005 listening, physical MIDI and two-client acceptance.** Earlier
  security, platform and release gates remain open. No commit, push or publication.

Runtime JAR SHA-256: `3fea40a61bc656b764d80e6730e61fc591432ae0695671ba6e74e1f4d4fbddd9`.


## Shift-right-click cable removal — 2026-11-05

- **PASS — reproduced before implementation:** four new packaged GameTests failed at
  Minecraft game-mode dispatch while sneaking; ordinary connection setup and the 19
  existing tests passed. Failures were `Module port click must be consumed, including
  while sneaking` and `Sneak-click on non-port module area is consumed`.
- **PASS — implementation:** the tool's item-on-block fallback forwards module hits to
  the existing block handler and consumes its result on both sides. Server-authorized
  destination-input removal, port geometry and air/non-port cancellation remain intact.
  Overlay and generated localization now explicitly say Shift-right-click.
- **PASS — regression and verification:** `make gametest` changed from four expected
  failures to all 23 passing. `make verify` passed 124 JUnit tests (zero skipped),
  23 packaged GameTests, 12 launcher tests, formatting, Checkstyle, PMD, and delivery
  inspection. New regressions cover empty/occupied offhand, fan-out preservation,
  output/empty-input refusal, foreign-owner refusal and selection cancellation.
- **PASS — native automated functional/visual probe:**
  `make smoke GRADLE_ARGS=-PqaCableRemoval TICKS=300` loaded the packaged JAR in fresh
  `run/make/smoke-7nb1kbho`. Actual native client game-mode dispatch and server packets
  removed, reconnected and removed the filter input cable with an offhand stick.
  Synchronized cable sets matched exactly at ticks 140/200/240 (2/3/2 cables).
  All three screenshots were inspected: removal displays `Cable removed. Other
  connections are unchanged.`, reconnection displays the connection confirmation,
  and the Shift-right-click hint is visible. Captures remain in that instance's
  `screenshots/cable-removal-{140,200,240}.png`.
- **PASS — explicit self-review:** production change is confined to tool dispatch and
  localization; no duplicated graph edits or client-side world mutation, no save or
  protocol changes, no dependency changes. The fallback returns PASS for non-modules
  or absent players. Native probe code remains in the test-only source set and is
  opt-in. Existing uncommitted work and manual worlds were preserved.
- **PENDING — physical mouse/key and two-client acceptance.** Native actions above were
  dispatched programmatically. Listening and earlier security/platform/release gates
  remain unchanged. This functional run is not performance qualification: zero audio
  underruns were reported, but maximum server work was 91.242125 ms against the retained
  <2 ms target, so native performance remains **FAIL/unqualified**.

Local raw logs: `validation/cable-removal-{red,green,verify,native}.log`.
Runtime JAR SHA-256: `8e84f9edfed73f507588bbe8431668506de6e53e995a21bb07592464699675de`.
No commit, push or publication.

## LFO and parameter CV extension — 2026-11-05

Baseline `aa231cbd488ec974b28320b4d747602101f2e839`, branch `001-modular-synth-mvp`;
implemented changes remain uncommitted. [Review](lfo/review.md), [results](lfo/results.json),
[red evidence](lfo/red.txt), [full verification](lfo/verify.txt), [LFO panel](lfo/panel.png).
These results extend, rather than replace, the historical evidence above.

| Evidence | Status | Result / limit |
|---|---|---|
| Full automated checks | PASS | 144 JUnit, 12 launcher tests, formatting, Checkstyle, PMD, Java 21 build |
| Packaged headless integration | PASS | 29 GameTests, including LFO persistence/cabling/unload, authority, old-panel close and phase-preserving destination removal |
| Resource/package inspection | PASS | Eight complete block asset sets, protocol 7, exact old filter/envelope world-v1 migration |
| Synthetic maximum DSP | PASS | 64 modules / 96 cables / eight speakers; p95 0.266083 ms; 136 measured allocation bytes over 10,000 blocks |
| Native controls / visual / cleanup | PASS | Actual widget edits, compact scrolling, invalid input, inspected screenshots, ten zero-handle recovery cycles; artifact scope in review |
| Short native load p95 budgets | PASS | Audio 0.299334 ms; server 0.593292 ms over 1,080 samples, approximately one minute on Apple M2 Pro / 16 GiB |
| Native zero-underrun qualification | FAIL | One underrun observed; audio max 5.158 ms and server max 8.516292 ms retained; no sustained qualification claim |
| Human RP-006 listening / acoustic | PENDING | All waveform, polarity, sync and ADSR reference-patch variations require audition |
| Two-client / 30-minute load | PENDING | Shared timing, late join, pause/recovery and sustained performance need native qualification |
| Self-review | PASS | Boundaries, ownership, compatibility, timing, cleanup, localization and provenance reviewed; see review |

Earlier security, platform, hardware and release gates remain applicable. No commit, push,
publication, or dependency changes occurred; existing manual worlds and AGENTS.md were preserved.

## Audio Mixer extension — 2026-11-05

Implemented four mono inputs, linear summing, five live faders/meters, input mute/multi-solo
and master mute, with default input 100% / master 25%. World schema 1, matching protocol 8.
Changes remain uncommitted. [Self-review](mixer/review.md), [results](mixer/results.json),
[verification](mixer/verify.txt), [native panel](mixer/native.txt), [native load](mixer/load.txt),
[full panel](mixer/full.png), [compact panel](mixer/compact.png), [block](mixer/world.png).

| Evidence | Status | Result / limit |
|---|---|---|
| TDD | PASS | Absent-module behavioral red; support-type compilation red explicitly separated; signed-clock-origin behavioral regression and green coverage |
| Automated verification | PASS | 157 JUnit, 12 launcher tests, formatting, Checkstyle, PMD, build and archive inspection |
| Packaged integration | PASS | 32 GameTests; mixer authority/stale/invalid edits, all 14 NBT controls, five jacks in four facings, removal/cable cleanup |
| Resource/package coverage | PASS | Nine complete block asset sets; original SVG/PNG, localized mixer controls/ports, append-only identifiers |
| Synthetic mixer load | PASS | Four patches, 64 modules / 96 cables / 8 speakers; 0.467042 ms DSP p95, zero measured allocation over 10,000 blocks after 5,000 warmup blocks |
| Native panel/visual/cleanup | PASS | Real drag updates before release, final values, mute/multi-solo/master, meters, exact/invalid numeric entry, compact controls, local mute/settings return, NBT, removal, five recovery cycles; screenshots inspected |
| Short native load p95 budgets | PASS | Audio p95 at most 0.624834 ms (final window 0.531375 ms); server p95 0.440625 ms over 1,080 measured ticks |
| Native zero-underrun qualification | FAIL | One underrun; audio max 5.185708 ms; measured server-window max 1.377709 ms, startup-inclusive max 4.523834 ms retained |
| Human listening / narrator | PENDING | RP-007 audition, acoustic and accessibility acceptance require observation |
| Two-client / restart / sustained | PENDING | Shared timing/lifecycle, actual disk restart/chunk reload and 30-minute load qualification |
| Self-review | PASS | Authority, compatibility, audio ownership, bounded edits/meters, lifecycle, assets and evidence limits reviewed |

Machine: Apple M2 Pro (Mac14,9), 16 GiB, macOS 26.6.2 (25G83), Java 21.0.12.1.
Final runtime SHA-256: `de2bedad0f061d50e9649f56995d5618c028844f1ca35c85e8386e67481afcc5`.
The load probe preceded final UI/edit-throttle refinements; DSP/meter/server class bytes
were verified identical to the final artifact. Full binary identity is not claimed.
Earlier security, platform, hardware and release gates remain applicable. No commit, push,
publication or dependency changes; manual worlds and AGENTS.md preserved.

## Git ignore review — 2026-11-05

- **PASS — configuration checks.** Reviewed Gradle and Make launch/output paths;
  retained existing build, runtime, log and Python-cache exclusions. Added Java IDE
  metadata/compiler output, standalone Python bytecode, JVM diagnostics and OS metadata.
  `git -c core.excludesFile=/dev/null check-ignore --no-index --stdin` verified 27
  representative generated paths are ignored, 19 source/dependency/asset/evidence paths
  remain trackable, and none of the 372 tracked files match ignore rules.
- **PASS — self-review and whitespace.** `git diff --check` passed. Wrapper artifacts,
  dependency pins, shared configuration, assets and structured validation evidence remain
  trackable. No executable behavior changed; runtime suites were not rerun. Earlier
  native, listening, multiplayer and release gates remain unchanged.

## Commitizen synchronization and tagged releases — 2026-11-05

Baseline: `d8c9533`, plus this uncommitted workflow/build change. Repository version
remains `0.1.0`. Validation used macOS, Gradle 8.13 with strict dependency verification,
the Java 21 toolchain, Python 3.14.6, Commitizen 4.8.3, and actionlint 1.7.7.

| Check | Result | Evidence |
|---|---|---|
| Commitizen regression, red then green | PASS | In a disposable Git repository, the original configuration bumped its own version/tag but left Gradle unchanged. Stable and prerelease bumps now update `.cz.toml`, `mod_version`, changelog, commit and tag together; the temporary repository is clean afterward. |
| Version and release boundary tests | PASS | 25 Python tests pass, including existing launcher tests; reject malformed/mismatched tags, stale descriptors, incorrect paths, missing assets, wrong embedded versions, altered checksums, and existing-release publication failure. Stable build metadata containing a hyphen does not become a prerelease. |
| Default-version full verification | PASS | `make ci GAMETEST_DIR=run/make/release-ci-final-20261105` under Java 21: 157 JUnit tests, 32 packaged GameTests, formatting, Checkstyle, PMD, strict dependency verification, artifact/resource inspection. |
| Bumped-version full verification | PASS | A disposable source copy bumped by Commitizen to `0.1.1-rc0` passes `make ci` with the same 25 Python, 157 JUnit and 32 GameTest cases. The first run exposed the GameTest's hard-coded `modularcraft-0.1.0.jar` assertion; it now verifies the filename against loaded mod metadata, and both mod/test metadata derive from Gradle. |
| Release staging and mock publication | PASS | The actual bumped JAR staged with LICENSE, THIRD_PARTY_NOTICES.md, dependencies.json, licenses.json, README.md, CHANGELOG.md, ledger.md, and SHA256SUMS. Checksums and embedded versions verified before mocked `gh release create`; no sources JAR/project ZIP included. |
| Clean runtime rebuild | PASS | Disposable-copy `./gradlew --no-daemon --dependency-verification strict clean assemble` reproduced the exact runtime JAR hash below. This is same-environment runtime-JAR evidence, not cross-platform or all-archive reproducibility. |
| Workflow syntax and action provenance | PASS | `actionlint .github/workflows/verify.yml`; added upload-artifact v4.6.2 (`ea165f8d65b6e75b540449e92b4886f43607fa02`) and download-artifact v4.3.0 (`d3f86a106a0bac45b974a628896c90dbdf5c8093`) SHAs verified using upstream `git ls-remote`. |
| Hosted tag trigger and GitHub publication | PENDING | No repository commit/tag/push or real release was created. A future version-tag push must establish hosted execution. |
| Self-review | PASS | Build job remains read-only, publishing alone receives contents:write, credentials are not persisted, tag text passes through quoted arguments, assets are selected exactly and rechecked, existing releases are not overwritten, tag runs are not canceled. AGENTS.md, dependency/toolchain pins, and manual worlds preserved. |

Runtime SHA-256 values:

- `modularcraft-0.1.0.jar`: `c9ab680f10460fd67737f12e85907a331c4e0cabc93fa21f7ef39006c9698eaf`.
- `modularcraft-0.1.1-rc0.jar`, before and after clean rebuild:
  `aa2174f2698324762957319dec43a31aabe844313f8e6a2d2263fa3ccc97e636`.

All earlier native, acoustic/listening, multiplayer, platform, performance and security
acceptance results remain applicable. Publishing build outputs does not turn those
PENDING/FAIL/BLOCKED results into passes. No runtime synthesis or world schema changed.
