# Step Sequencer documentation captures — 2026-11-07

Added the eleventh block to the README gallery, expanded RP-009 with a four-connection
wiring table and numbered recording steps, and corrected the guide and repository
CurseForge draft to distinguish melodic note recording from unsupported audio export.
The original showcase anchor and ten-module/eleven-cable instrument are preserved.

Three authentic native Minecraft screenshots were copied byte-for-byte to `docs/images/`.
No generated imagery, compositing, cropping, or retouching was used. See
[images.json](images.json) for original paths, dimensions, SHA-256 hashes, and runtime
artifact identity. All three PNGs are 1920×1052; the requested window was 1920×1080.

## Reproduction and observed results

Run each capture flag separately and omit `QA_DIR` to obtain a fresh disposable instance:

```sh
make smoke GRADLE_ARGS=-PqaShowcase TICKS=1200
make smoke GRADLE_ARGS=-PqaStepSequencer TICKS=500
make format
make lint
make checksums
```

These captures ran on Apple M2 Pro / macOS arm64 / Java 21, with Minecraft 1.21.1,
NeoForge 21.1.218 and packaged ModularCraft 0.5.0. The runtime JAR hash remains
`dec3110a7109045a24b52c1a05c1acd593799c1728883b537d2381f4910cb5df`.
Only opt-in test fixtures, documentation, and the QA window-size option changed.

- **PASS:** [showcase run](showcase.txt), `run/make/smoke-0ia9wjrn`: eleven registered
  block portraits plus the connected-instrument image, twelve files total. The existing
  ten-module/eleven-cable instrument retains its running Euclidean Sequencer and enabled
  Speaker. Its construction and cables were not changed.
- **PASS:** [melody run](melody.txt), `run/make/smoke-0252czri`: real editor synthetic
  typing/mouse entry, held-key suppression, rest entry, completion at step 16, compact
  380×240 logical bounds, four screenshot files, and the accepted four-module/four-cable
  patch with playback running and Speaker enabled.
- **PASS:** [formatting](format.txt) and [static checks](lint.txt), including
  Checkstyle and PMD for the test fixtures. Full gameplay suites were not rerun for
  this documentation/capture-only change.
- **PASS:** [documentation and archive checks](checks.txt): local links/anchors/images,
  retained guide anchors, eleven-block gallery/list, and all three new PNGs present
  byte-for-byte in both the sources JAR and project ZIP. `make checksums` passed artifact
  inspection, including eleven asset sets and exclusion of runtime test fixtures.

The final instances retain original screenshots, worlds, Minecraft logs, and complete
`capture-command.log` files. Do not reuse these instance directories. The compact and
recording-complete captures remain in the melody instance as additional evidence.

## Red–green and visual review

The [original showcase run](showcase-before.txt) failed with
`Screenshot write did not complete: AMP`. Its fixed end tick omitted the eleventh
portrait. The schedule now follows the module count, and the capture assertion requires
the complete enum set and all twelve PNGs. The fresh run passed.

The [first melody capture](melody-tooltip.txt), retained in `run/make/smoke-xgbascnu`,
passed its state checks but failed visual review: a step tooltip covered the controls.
A host cursor warp alone did not update Minecraft's pointer in the unfocused window.
The test fixture now dispatches a cursor movement through Minecraft's registered GLFW
callback and asserts the accepted position, without changing production UI behavior.
The fresh capture passed visual review.

Self-review **PASS**:

- Portrait: same showcase pedestal, daylight and camera framing; all sixteen step cells
  and five labeled ports visible, no HUD. Default rests appear as hollow cells.
- Editor: all controls and twelve piano keys readable; notes and Rest visible; orange
  Edit 5 selection and cyan Play 12 marker are distinct and labeled. No tooltip or HUD.
  The caption identifies the seeded 16-step, 120 BPM, 80 ms demonstration and explains
  that gate time defaults to 100 ms and new patterns contain only rests.
- Patch: Step Sequencer → Saw Oscillator → Amp → Speaker all in frame, pitch/gate and
  both audio cables visible, lit step indicators, daylight, no HUD or hand. The accepted
  wiring corresponds to the RP-009 table. The simple four-step walkthrough is explicitly
  separate from the longer screenshot demonstration.
- Compact capture: all controls fit the 380×240 logical editor viewport. This is a
  synthetic resize of the native screen, not physical-device or accessibility acceptance.
- Production source, public APIs, protocol 12, schema 2, dependency pins, AGENTS.md,
  and existing player worlds are unchanged. No commit, push, installation, or publication.

## Qualification limits

These are short screenshot and scripted-input probes. The accepted showcase and melody
runs each reported one audio underrun. Showcase maximum audio-block time was 28.318417 ms;
melody maximum was 1.292541 ms. The melody's sampled server p95 was 0.324417 ms, with
8.550125 ms reported as the full-run server maximum. These diagnostics do not establish
sustained performance or renew the earlier feature's load qualification.

Human listening/acoustic, physical-input, multiplayer/timing, zero-underrun, 30-minute
load, and earlier security/platform/release gates remain **PENDING**. Previous feature
evidence and caveats in the [ledger](../../ledger.md) are preserved.
