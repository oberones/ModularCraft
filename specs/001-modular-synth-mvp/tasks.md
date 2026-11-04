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
