# Audio Mixer self-review — 2026-11-05

Reviewed the domain, DSP, protocol/persistence, panel/edit queue, metering, block resources,
packaged integration tests and native evidence. No commit, push, publication, dependency
change or manual-world alteration. AGENTS.md remains unchanged.

- **Authority/compatibility:** Four separate audio inputs preserve one-cable inputs, fan-out,
  ownership/range/cycle/capacity rules. Fourteen validated controls use existing server
  authorization/revisions. Stable enum identifiers appended; named world-v1 records stay
  unchanged; older network/snapshot versions explicitly refused by v8.
- **Audio:** Fixed four-input sum with post-input/post-master gains; no auto normalization or
  internal clipping. Effective gains ramp 480 samples and adopt continuously. Sources render
  once per sample; existing final limiter/DC protection, topology crossfade and local mute apply.
- **Meters:** Per-mixer preallocated state, audio-thread writer, bounded sequence-stamped UI
  copies. No network traffic or blocking/audio allocation. Five post-control peak envelopes,
  300 ms decay, 1 s overload hold. Current graph identity, wall-time staleness and client pause/
  mute prevent retired readings. Parameter replacement preserves meter envelope history.
- **Controls:** One outstanding edit; at most 14 coalesced pending values; 100 ms minimum send
  spacing. Exact numeric validation, multiple solos, mute precedence, release retains queued
  final value, close drops unsent work. Timeout/rejection stops pending work. Acknowledgments
  still reach mixer edits after returning from local settings. Compact strips keep their fader,
  entry, mute and solo together; Page navigation and Tab expose controls, footer stays fixed.
- **Resources:** Generator owns original MIX SVG/PNG, matching chassis, five jack labels,
  models, loot and localization. Package inspection now checks all nine asset sets and mixer
  parameters/ports. All four facings' cable anchors match actual patch hitboxes.
- **Lifecycle:** Packaged tests verify all controls through NBT, authority/invalid/stale
  rejection and incident-cable removal. Native probe exercises widgets, meters, settings
  return, NBT roundtrip, block removal and five zero-resource recovery cycles.

Initial regressions failed on absent MIXER; meter/edit tests initially failed compilation
because support types were absent (recorded separately, not claimed as behavioral red).
A later clock-origin regression covers signed monotonic time. The first integration run
found a test assertion calling WorldState.get after deletion; get deliberately throws for
missing modules. The corrected assertion checks registry absence. No runtime defect was
hidden by that fixture correction.

## Evidence limits

The short load run used 64 modules / 96 cables / 8 speakers on Apple M2 Pro (Mac14,9),
16 GiB, macOS 26.6.2 (25G83), Java 21.0.12.1. Audio p95 at most 0.624834 ms; final reported
p95 0.531375 ms, max 5.185708 ms. Server p95 0.440625 ms over 1,080 measured ticks; measured
window max 1.377709 ms; startup-inclusive max 4.523834 ms. **One underrun occurred**.
Load artifact SHA-256 `31bd7a084711929ea2c4be8fa2cc4f32ecae9afd21e6d0ac64aa213b77c26a89`.
That build has the same DSP/meter/server implementation as the completed mixer; later changes
refine screen layout and signed-clock edit throttling. Do not claim identical binary qualification.

Full/compact screenshots are real native captures. The compact probe uses a 380×240 logical
screen in the larger native window. All five jack/facing hitboxes also have automated tests.
NBT roundtrip is not a disk restart; recovery/removal does not establish real chunk reload,
multiplayer or audio-device acceptance. Human RP-007 listening, narrator, two-client timing,
real restart and 30-minute qualification remain PENDING, together with earlier release gates.

Final verification: **157 JUnit**, **32 packaged GameTests**, **12 launcher tests**, formatting,
Checkstyle, PMD and nine-set artifact inspection pass. Final synthetic mixer benchmark p95
**0.467042 ms**, **0 allocation bytes** over 10,000 measured blocks after 5,000 warmup blocks.
Final native widget probe confirms changes before fader release, the final queued value,
compact controls, settings return, NBT/removal and five cleanup cycles. Native artifact:
`de2bedad0f061d50e9649f56995d5618c028844f1ca35c85e8386e67481afcc5`.
A byte comparison against the load artifact found only MixerScreen, its Fader class and
MixerEdits changed; DSP, meter and server class bytes match. Detailed commands/results and
artifact scope are in results.json. All PENDING/FAIL gates above remain unchanged.
