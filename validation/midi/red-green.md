# MIDI red–green evidence — 2026-11-04

Commands used the pinned wrapper through Make, strict dependency verification, and the
existing Java 21 toolchain. Gradle needed sandbox escalation for its existing cache;
the initial lock-file permission failure was not counted as behavioral red evidence.

| Stage | Observed red | Final green |
|---|---|---|
| Input/performance | `make test TEST='*MidiKeyboardTest'`: 5 failing assertions/contract failures against scaffolds (mixed priority, sustain, repeated notes, absolute pitch, malformed input) | All five pass |
| Device adapter | `make test TEST='*Midi*Test'`: five adapter failures against the lifecycle scaffold, while the five input tests passed | Nine fake-provider tests pass, including later regressions |
| Snapshot | `make test TEST='*MidiProtocolTest'`: expected version 3, actual version 2 | v3 full-range roundtrip and v2 refusal pass; original world bytes retained |
| Packet | `make gametest-dev`: `midipacketscarryabsolutepitchesandrejectmalformedmodes` failed with `Bounded v3 keyboard packet` | 52-byte packets, absolute notes and malformed-mode rejection pass |
| Preferences | `make test TEST='*Midi*Test'`: remembered device expected `controller-key`, restored empty | Both preference tests pass, including malformed/oversized input |
| Provider cancellation | `make test TEST='*MidiInputTest'`: rapid selection unnecessarily opened obsolete requests; late close threw `IllegalStateException` | Cancellation coalesces before opening and late close always completes the mailbox |
| Persistent close failure | `make test TEST='*MidiInputTest'`: a failed close allowed replacement handles to accumulate | Ownership remains with the failed handle; repeated Refresh cannot open a replacement until close succeeds |
| Native MIDI path | `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`: tick 230 retained note 72/gate=false instead of MIDI 69/gate=true | MIDI 69 stays active under sustain through octave change, then releases |
| Empty selection UI | Same native fixture found `ImmutableCollections$ListN.indexOf` null-selection failure | Panel opens with MIDI Off; device/Refresh/channel controls exercised |
| Range cleanup | Native fixture failed `Out-of-range keyboard retained active input controls` at tick 358 | Local holds/device callbacks and controls are revoked on range loss |

Native failures used fresh disposable instances; no manual world was used. The full
final `make verify` passes 71 JUnit tests, 12 packaged GameTests, 12 launcher tests,
formatting, Checkstyle, PMD, Java 21 compilation and artifact/resource inspection.
The native fixture passes independently; see [observations](native-results.json).

The headless fake player is not registered in Minecraft's PlayerList, so its lease
is correctly released in the processing tick. Its integration test asserts accepted
and retained pitches plus session cleanup. Held-note octave independence is established
by the pure performance test and the actual singleplayer native fixture, not by pretending
that the fake player has a live lease.

Screenshots: [controls](controls.png), [sustained A4 at panel octave 6](sustain.png),
[released A4 at panel octave 5](released.png). The native process discovered/opened the
Scarlett 18i8 USB input; the note messages were injected by the fixture. This is not
physical-controller, unplug, listening or multiplayer acceptance.
