# MIDI reconnect remediation — 2026-11-04

Source: `b4f7377` plus the uncommitted timeout and reconnect changes. Java 21.0.12.1+1
(Eclipse Adoptium aarch64), macOS, Minecraft 1.21.1, NeoForge 21.1.218. Runtime JAR
SHA-256: `3ed718d239657a83becd1d02e72ad0d25da12088600ed025815a6fef65f28059`.
No pins, dependencies, protocol or world-save formats changed.

## Trigger and remedy

The player confirmed MIDI works initially, then becomes silent after leaving and returning
to the keyboard panel despite “MIDI connected”. The earlier [diagnostic record](rk006-diagnostics.md)
showed retained native Java Sound reader threads and a blocked native close. The installed
JDK source (`MidiInDevice.implOpen`) creates a reader only when its cached reader reference
is null. An open handle alone therefore does not establish a working reopened reader.

Each native connection now runs in a fresh, bounded headless Java helper. The same single
client worker manages discovery, helper lifetime and input polling between game ticks.
Closing explicitly closes native handles and reaps the helper; a blocked close is forcibly
terminated within the operation budget. No next helper opens until its predecessor exits.
The original provider defect is contained, not patched inside the JDK.

## Red–green

- `reconnectDeliversFreshNotesEvenWhenProviderCachesABrokenReader`: a cached fake provider
  reports open on its second connection but delivers no notes. The original direct backend
  failed the second-cycle note-count assertion (1 test, 1 failure). The isolated adapter
  receives fresh events and leaves no live child over 20 cycles.
- `idleWorkerReceivesInputWithoutWaitingForAnotherClientTick`: the former job-only worker
  failed the polling assertion (2 worker tests, 1 failure). The single bounded worker now
  pumps input between jobs. A latch-controlled companion test verifies four pending jobs,
  rejection beyond capacity, and shutdown draining. A preceding compilation-warning failure
  was corrected and is not counted as red behavior evidence.
- Extended channel selection regression: before forwarding the filter to the backend,
  selected channel 3 was not received by it (1 test, 1 failure). The helper now rejects
  unselected channels before its queue; the parent also validates channel/ranges.
- Additional tests cover bounded 64-frame polling, pitch endpoints, malformed frames,
  explicit helper overflow, failed handshake cleanup, forced close, ownership retained
  when termination has not completed, per-tick delivery, and once-per-second discovery.
- **PASS:** `make verify`: 81 JUnit, 12 packaged GameTests, 12 launcher tests, formatting,
  Checkstyle, PMD, compilation, archive/resource inspection.

## Native evidence

- **PASS — RK006 lifecycle:** [20-cycle output](reconnect-lifecycle.txt). IN_ALL, channel 1.
  Each cycle reaches ready, remains active through a health scan, disables input, and waits
  1.8 seconds (within the two-second operation budget) before checking resources. Both live
  helper processes and parent native input threads return to zero on all 20 cycles. An
  initial probe sampled at 1.2 seconds, before the 1.25-second graceful-close deadline, and
  correctly saw cleanup still pending; that was not treated as a successful cycle.
- **PASS — parent death:** an isolated test parent opened IN_ALL, reported its helper PID,
  then used `Runtime.halt` without normal cleanup. The helper disappeared within the
  two-second observation window, confirming the parent-liveness watchdog. Minecraft was
  not terminated by this test.
- **PASS — packaged native fixture:** `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`,
  fresh `run/make/smoke-ldmkoto2`. The packaged runtime opened a helper for Scarlett 18i8 USB;
  [inspected screenshot](reconnect.png) shows MIDI connected and A4/gate 1 under sustain.
  Synthetic notes, pedal release, GUI overlap, octave changes, settings return and range
  loss assertions all passed. This fixture injects events at the screen, not physical MIDI.
- **Retained performance limitations:** fixture startup underruns=1, maximum audio block
  7.752417 ms, maximum server work 6.092083 ms. This short run is not SC-003/004/005 timing
  or load acceptance; the previous performance/security gates remain in force.
- **PASS — player-assisted physical reconnect capture:** IN_ALL, UI channel 1, two
  35-second listening windows through the production MidiInput/process adapter. Received
  **327 events before close and 317 after reopen**; both phases ended `midi_ready` and
  both closes reported zero live helper children. Samples included Note On (144) and
  Note Off (128), wire channel 0 (UI channel 1). The process exited successfully.
  The player requested a restart after accidentally closing Minecraft; the restarted
  standalone capture did not require Minecraft running. The earlier interrupted attempt
  is not used for the before/after comparison (no playing during its first window).
  [Capture source](MidiReconnectCapture.java) uses the same production sources as the
  recorded runtime candidate and the lifecycle probe's bounded readiness check.
- **PENDING:** physical in-game note delivery/listening after panel reopen, physical
  sustain/unplug testing, acoustic timing and two-client timing. This capture proves the
  hardware/adapter reconnect path; it does not exercise Minecraft's screen or audio output.

The hardware lifecycle probe is retained as [Java source](MidiLifecycleProbe.java). Compile
it with the same Java 21 toolchain as Minecraft against `build/libs/modularcraft-0.1.0.jar`
into a disposable directory, then run `com.malevolentgods.modularcraft.client.MidiLifecycleProbe`
with that directory and the runtime JAR on the classpath. It selects IN_ALL and requires the
RK006 connected; it opens/closes real input and exits afterward. Do not treat it as a note
or listening test.

## Explicit self-review

Reviewed single-worker ownership, bounded work/queues, early channel filtering, frame/range
validation, generation revocation, late result cleanup, child readiness/exit, bounded forced
termination, refusal to overlap helpers, parent death, temporary-class extraction, exception
cleanup and dedicated-server loading. One private helper directory is reused even if
extraction fails, with normal JVM-exit cleanup. Only fixed mod classes and the running Java
executable are used; no shell, downloaded executable, network service or dependency is added.
The helper's 32 MiB heap and 64 MiB metaspace ceilings do not bound total native process RSS.
Two 256-event queues now bound the separate helper/client stages; this addition is explicit
in the feature contract and architecture. All three stream-close attempts run even if one
fails. PMD resource suppressions are narrowly documented ownership/cleanup cases.

The player's running game and manual worlds were preserved. No commit, push or publication.
