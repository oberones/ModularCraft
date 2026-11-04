# Acceptance workbook

Use the exact artifact hashes from `build/libs/SHA256SUMS`. Record the source revision
and dirty state, both machines, Java, NeoForge, OS, display settings, output devices,
network conditions, capture devices/rates, clock alignment procedure, uncertainty,
operator, date, workload, and raw evidence paths. PASS/FAIL/BLOCKED/PENDING are separate;
an offline render or native counter is not an acoustic capture or a listening pass.

## Automated commands

```sh
make verify
make integrity
```

The [Makefile guide](development.md) covers packaged launches and isolated smoke worlds.
`make verify` includes packaged headless GameTests; `make ci` is its no-daemon counterpart.

`MeasurementTest` measures four 60-second pitch cases in the main suite, one-second ADSR
linearity and intermediate retriggers in `CoreTest`, MU-009 filter behavior, finite
extremes, DC, aliasing, and a fixed-load DSP benchmark. Alias measurement uses coherent
frequencies nearest 55/220/880/3,520 Hz, a 65,536-frame FFT, and a 16x naive-saw reference
with ideal spectral downsampling. Non-harmonic residual bins measure aliasing; harmonic
pass-band coloration is not misreported as aliasing. A 128-sample warmup excludes the
initial filter fill. Pitch tests use the exact specified frequencies for 60 seconds each.
ADSR stage edges are checked to one sample, independently of game ticks. Arithmetic
transport is simulated over one hour including pause. Seeded malformed snapshot tests
exercise 1,000 inputs, and world roundtrips repeat 20 times.

The fixed DSP fixture has four patches, each with one oscillator, ten series filters,
three envelopes, and two speakers; 12 audio and 12 control cables. Each envelope fans
out to four distinct control inputs, covering all ten filters and both speakers.
Grid positions and controls match SC-005; the offline timing run is not the 30-minute
native/network workload.

## Native fixture, isolated development use

For an automatically prepared disposable fixture, use `make smoke`, `make smoke-load`, or
`make smoke-recovery TICKS=500`. Each builds/stages the current JAR, creates a fresh world,
bootstraps a GameTest world seed if needed, and exits after the configured game ticks.
The manual procedures below remain available for custom acceptance environments.

The test source set includes an opt-in native fixture and probe; neither ships in the
mod or sources JAR's executable sources. Use a **new disposable run directory**, copy a
fresh GameTest `level.dat` into `saves/ModularCraft-QA/`, and prepare `options.txt` before
launch. Never point the fixture at a personal save: it places a platform and instruments
near spawn. Example setup is recorded in `validation/native-load.log` and this workspace's
`run/native-load` directory. Render settings: 1080p, render distance 12, simulation 8,
shaders off, VSync off, `pauseOnLostFocus:false`, `onboardAccessibility:false`.

```sh
./gradlew runClient -Pqa -PclientRunDir=run/native-qa
./gradlew runClient -Pqa -PqaLoad=true -PclientRunDir=run/native-load
```

For packaged verification, copy `build/libs/modularcraft-0.1.0.jar` into the disposable
run directory's `mods/` folder and add `-Ppackaged`. The probe prints the loaded code
origin so an IDE class directory cannot be mistaken for the installable artifact.
Add `-PqaCycles=true` for twenty local audio resynchronizations with native channel/timer
teardown assertions. This does not replace the world-restart/device lifecycle checklist.

The default probe runs 1,200 ticks (about one minute), prints queue/peak/processing/underrun
counters, and exits. `-PqaTicks=36000` runs 30 minutes at normal tick rate. Gate schedule
is two seconds on/two seconds off. Fixture code is
`src/gametest/java/com/malevolentgods/modularcraft/gametest/NativeFixture.java`.
A one-client development probe does **not** satisfy two-client packaged SC-005.

## Packaged play and timing

1. Install only the candidate JAR plus the exact loader on a fresh Creative client and
   a normal headless dedicated server. Confirm five blocks/tool, all textures/models,
   labeled ports, cable arrows, and every panel control. Confirm no client/audio class
   loads on the server. Obtain/accept any host licensing terms as the server operator.
2. Build both README reference patches using only their instructions. Test every listed
   variation, redstone/manual OR gating, retriggers, short gates, disabled/missing inputs,
   and accepted/rejected numeric edits. Record actual listening, including starts/stops,
   maximum controls, disconnection to a drone, and multiple speakers.
3. SC-003: ten minutes, one RP-001 patch, 100 gate/parameter actions. Capture an input
   marker and acoustic onset on aligned clocks; subtract deliberately configured ADSR
   duration when classifying responsiveness. p95 input-to-response ≤100 ms, no stuck notes
   or unintended interruptions >100 ms. Measure mute separately at ≤100 ms.
4. SC-004: two macOS clients on a dedicated server. RTT ≤50 ms, variation ≤10 ms, no
   induced loss. Capture 100 shared edits/gates on both outputs. p95 response ≤150 ms;
   p95 inter-client onset difference ≤50 ms. Report synchronization uncertainty rather
   than assuming packet timestamps equal physical output time. Join during attack,
   decay, sustain, and release: current-state recovery ≤1 second, no replay/retrigger.
5. Spatial: sample distances 2, 8, 16, 24, 32, and >32 blocks from speaker center; expected
   gain is 1, .8, .5333, .2667, 0, 0. Turn the listener, use two speaker positions, move
   dimensions, and verify local controls never affect another listener. Compare Master,
   Records, local volume, mute, and speaker disable independently.
6. SC-005: both clients, 30 minutes, four exact maximum-load patches. Each is a 4×4 grid
   at two-block spacing; origins form a 2×2 grid eight blocks apart. Pitches 110/220/330/
   440 Hz, levels 50%, cutoff 5,000 Hz, resonance 25%, amounts 50%, default ADSR, speakers
   10%. Hold all gates two seconds/release two seconds. Compare identical inactive and
   active worlds on M1-class Apple Silicon with ≥16 GB, macOS 26.6.2, Java 21, and the
   display settings above. p95 frame duration increase ≤20%, world update p95 <50 ms,
   DSP+mix p95 <1 ms/block, mod server work target ≤2 ms/tick. Record tail spikes,
   allocations, native queue depth, underruns, and acoustic gaps, not just averages.
7. SC-006: 20 save/restart cycles and 20 unload/reload/reconnect cycles. Compare exact
   UUIDs, owners, controls, and cable endpoints. Speakers must require re-enabling after
   restart/patch unload. Gate release must survive screen close, range/permission loss,
   death, disconnect, and redstone changes. Resource counts return to idle; old voices
   stop within 250 ms of locally observed lifecycle events. Change devices and reload
   resources during held notes, including recovery while muted.
8. Exercise permissions, operator edits, stale races, malformed packets, all capacity
   limits/split/merge paths, over-distance/dimension cables, replacement identities,
   corrupt/unsupported records, unloaded quotas, and request/traffic overload. Each
   rejection must preserve accepted state and identify a recovery action.
9. SC-001: five first-time users, fresh installs, only the README. At least four build,
   wire, enable, play, release, and locate local mute within ten minutes without help.

Hosted CI is a separate record: a local workflow-shaped command is not a hosted run.
Do not call the MVP fully accepted until every applicable release gate passes. Preserve
failed captures and provenance alongside the final pass; do not overwrite the original
world or recordings during recovery.


## RP-003 keyboard acceptance

Run `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500` for a fresh packaged-JAR fixture.
It opens the actual keyboard through server inspection, plays overlapping computer keys,
transposes a held note, drags over black/white keys and outside, reopens through audio settings, checks accepted note/gate state, and captures six native
screenshots. Inspect idle/held/octave/released panels and the world block appearance.

Separately audition all twelve notes, mouse drag over black keys and outside, mouse plus
computer holds, octave limits, same-key repeats, close/reopen through audio settings, focus
loss, CV/gate cable removal, manual/redstone overlap and release tails. Verify A4 = 440 Hz,
C1/B7 bounds, CV disconnection restoring Frequency, and no unintended clicks/stuck notes.
Repeat unload, removal, disconnect, permission/range loss, and overload recovery. Run
SC-003 and SC-004 with RP-003 (including two listeners and late join); automated native
state assertions do not establish listening or acoustic timing acceptance.
