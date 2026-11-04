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
