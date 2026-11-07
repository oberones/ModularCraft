## Unreleased

### Feat

- Add a 1–16-step melodic Step Sequencer with note/rest recording, an input-only piano,
  tempo or external clock, adjustable gate time, and optional redstone transport.
- Keep saved world/block schema 2; require protocol/snapshot 12 on every client and server.
  Back up worlds before upgrading and restore that backup before downgrading.

### Fix

- Preserve pending Step Sequencer recordings across unrelated state updates and refresh
  unfocused Length, BPM, and Gate ms fields from accepted edits.
- Keep replacement Step Sequencer runtimes externally clocked after another patch block
  is removed, and retrigger melodic playback on same-sample stop/resume.

## v0.5.0 (2026-11-07)

### Feat

- increase module limit from 16 to 20

## v0.4.0 (2026-11-07)

### Feat

- add redstone toggle support to euclidean sequencer

## v0.3.1 (2026-11-07)

### Fix

- updates based on PR feedback

## v0.3.0 (2026-11-06)

### Feat

- add separate speaker block

## v0.2.2 (2026-11-06)

### Fix

- remove global k shortcut

## v0.2.1 (2026-11-05)

### Fix

- set neoforge version back to 21.1.218

## v0.2.0 (2026-11-05)

### Feat

- update neoforge support to 21.1.234

## v0.1.2 (2026-11-05)

### Fix

- address timing issue in build server test

## v0.1.1 (2026-11-05)

### Fix

- update builds to use linux runners

## v0.1.0 (2026-11-05)

### Feat

- add mixer block
- add clock-synced LFO
- add square oscillator block
- add better note control to saw oscillator block
- add euclidean sequencer module
- add detune capability to saw osc module
- add midi support to keyboard block
- add keyboard block
- initial MvP with four basic module blocks

### Fix

- address UI bug that made text difficult to read
- update wiring graphics to prevent clipping
