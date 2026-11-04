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
