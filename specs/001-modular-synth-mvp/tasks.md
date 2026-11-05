# Keyboard extension tasks

Approved extension to the existing MVP, 2026-11-04. Existing module implementation predates
this checklist; acceptance evidence is recorded in ../../validation/ledger.md.

- [x] K01 Add failing behavioral tests for piano input, pitch/ports, lease, snapshots and retriggering.
- [x] K02 Add keyboard domain contracts, octave persistence, typed pitch/gate routing and snapshot v2.
- [x] K03 Integrate server sessions, authority, FIFO note release, exclusive leases and cleanup.
- [x] K04 Add exact concert-pitch oscillator input, phase-preserving smoothing and external envelope gate.
- [x] K05 Add the non-pausing piano panel, mouse/computer input, octave controls and localized state.
- [x] K06 Generate original keyboard assets and updated oscillator/envelope ports; extend delivery checks.
- [x] K07 Update specification, plan, architecture, guides and RP-003.
- [x] K08 Add DSP/compatibility/queue tests, packaged GameTests and disposable native keyboard probe.
- [x] K09 Complete final make verify and record self-review/native screenshot evidence.
- [ ] K10 Record human RP-003 listening acceptance and two-client timing/late-join acceptance.

Existing security, performance, other platforms and release-acceptance gates remain unchanged.
No commit, push or publication is authorized.


## MIDI extension

- [x] M01 Observe failing priority/sustain/full-range tests before behavior implementation.
- [x] M02 Add bounded Java Sound discovery, selection, callbacks, lifecycle and fake-provider tests.
- [x] M03 Add relative/absolute packet mode and v3 snapshots; preserve v1 world data.
- [x] M04 Add local preferences, device/channel/refresh/release controls and compact layout.
- [x] M05 Cover server ordering, malformed packets, DSP endpoints, failures and 20 fake-device cycles.
- [x] M06 Pass `make verify`, native MIDI/octave/range fixture and visual review; record evidence.
- [x] M07 Update contracts, architecture, guides, localization, changelog and explicit self-review.
- [ ] M08 Qualify physical controller/pedal/unplug behavior and real-device resource cycles.
- [ ] M09 Record human listening/acoustic timing and two-client MIDI SC-004 acceptance.

## Euclidean sequencer extension

- [x] EU01 Add exhaustive pattern, clock, control and checkpoint contract tests before implementation.
- [x] EU02 Add the sixteenth-step four-channel module, validated settings, seven ports and schema-v1 saves.
- [x] EU03 Implement the shared sample timeline, integer clock phase, exact pulses and bounded clock chains.
- [x] EU04 Route digital signals by module and port; preserve envelope and keyboard/manual/redstone semantics.
- [x] EU05 Add authoritative transport edits, v5 checkpoints, next-edge edits, overload and unload suspension.
- [x] EU06 Add the localized responsive editor and client-only animated block rings with cached geometry.
- [x] EU07 Generate original editable assets and extend packaging checks to all six modules.
- [x] EU08 Cover malformed records, protocol/authority/stale revisions, persistence and server lifecycle.
- [x] EU09 Pass `make verify`, sequencer DSP/allocation measurements and the disposable native probe.
- [x] EU10 Inspect editor/compact layout and all four block orientations; record screenshots and self-review.
- [x] EU11 Update RP-004, feature/architecture/acceptance documentation, changelog and validation ledger.
- [ ] EU12 Complete human RP-004 listening and two-client timing/late-join acceptance.
- [ ] EU13 Qualify sustained native sequencer load: resolve server-work spikes and observed underrun.

Evidence: [sequencer review](../../validation/sequencer/review.md) and
[machine-readable results](../../validation/sequencer/results.json). Automated and native
functional passes do not close existing security, performance or release gates.
No commit, push or publication is included.


## Square oscillator extension

- [x] SQ01 Observe failing square defaults/authority, pitch/DSP, persistence and protocol tests.
- [x] SQ02 Append square kind/PWM; retain fractional widths and explicit integer validation.
- [x] SQ03 Add bounded pulse DSP, anti-alias/DC measurement and phase-continuous control ramps.
- [x] SQ04 Share oscillator note/pitch behavior and authoritative edits; retain world v1, require protocol v6.
- [x] SQ05 Generate SQR assets/localization and extend delivery checks to seven modules.
- [x] SQ06 Cover malformed records, edits, packaged placement/cabling/reload/removal and load budgets.
- [x] SQ07 Pass `make verify` and disposable native PWM/note/compact-layout probe; inspect screenshots.
- [x] SQ08 Update contracts, RP-005, architecture, acceptance, changelog and explicit self-review.
- [ ] SQ09 Record human RP-005 listening and two-client timing/late-join acceptance.
- [ ] SQ10 Qualify native sustained performance; retain the observed underrun/server-work failure.

Evidence: [square review](../../validation/square/review.md) and
[results](../../validation/square/results.json). Earlier release gates remain applicable.

## LFO and parameter CV extension

- [x] LF01 Observe failing module/port, signed-cutoff, LFO waveform/clock, ADSR CV, codec and audio contracts.
- [x] LF02 Implement analytic LFO phase, frequency/shape transitions, pulse-measured synchronization and loss recovery.
- [x] LF03 Add signed resonance/ADSR CV depths, exact stage-entry capture and shared sustain control frames.
- [x] LF04 Integrate server/client timelines, protocol 7 checkpoints, exact legacy world-v1 migrations and lifecycle handling.
- [x] LF05 Add original assets, labeled ports, selectors, grouped/scrollable controls and native widget assertions.
- [x] LF06 Preserve legacy short-note behavior and LFO phase across topology changes; fix delayed previous-panel close.
- [x] LF07 Pass full automated checks and 29 packaged GameTests; measure maximum DSP/native workloads.
- [x] LF08 Inspect native screenshots and ten cleanup cycles; document RP-006, compatibility and explicit self-review.
- [ ] LF09 Complete human listening/acoustic and two-client timing/late-join acceptance.
- [ ] LF10 Qualify 30-minute native load and resolve the observed underrun; retain prior release gates.

Evidence: [LFO review](../../validation/lfo/review.md) and [results](../../validation/lfo/results.json).
