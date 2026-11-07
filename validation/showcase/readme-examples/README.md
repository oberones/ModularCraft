# README patch examples — 2026-11-07

Three authentic native Minecraft captures illustrate the drone → keyboard lead →
four-voice rhythm progression. Recipes and captions are in the README and user guide.
Images are copied byte-for-byte from their disposable QA instances, with no generated
content, compositing, cropping, or retouching. See [results.json](results.json) for image
hashes, original locations, accepted-state assertions, and diagnostic limits.

## Reproduction and results

Each command passed against the packaged ModularCraft 0.3.1 runtime on macOS / Java 21.
These explicit instance directories now contain evidence and must not be reused; omit
`QA_DIR` or choose a fresh path for subsequent runs.

```sh
make smoke GRADLE_ARGS=-PqaReadmeExample=drone TICKS=600 QA_DIR=run/make/readme-drone-final
make smoke GRADLE_ARGS=-PqaReadmeExample=lead TICKS=600 QA_DIR=run/make/readme-lead-01
make smoke GRADLE_ARGS=-PqaReadmeExample=rhythms TICKS=600 QA_DIR=run/make/readme-rhythms-final-02
make check
make assemble
python3 scripts/check-delivery.py
```

- **PASS:** exact 4/3, 5/5, and 15/17 module/cable counts, complete connection sets,
  oscillator kinds/frequencies, all parameter settings, and enabled Speakers. The
  rhythmic sequencer was running at both pre-capture checks.
- **PASS:** real KeyboardScreen synthetic key presses produced C4, D4, C4 fallback,
  and final release in authoritative keyboard and Amp envelope snapshots.
- **PASS:** hidden HUD, closed panels, supported camera FOV, completed PNG writes.
  Requested window size was 1920×1080; actual PNG/framebuffer size is **1920×1052**.
- **PASS:** `make check`: 25 launcher tests, formatting, Checkstyle, and PMD. Gradle
  reused unchanged JUnit results: 180 tests, zero failures/errors/skips.
- **PASS:** artifact inspection and separate runtime/sources JAR inspection exclude
  both capture classes. The sources JAR includes byte-identical example PNGs.
- **PASS:** local README/guide/development links, image paths, section anchors, and
  `git diff --check`.

## Red–green and visual review

Added the native assertions before implementing the scene builder. The first packaged
run failed as expected: `README drone expected 4 modules / 3 cables, got 5 / 4`.
Implemented the scenes and observed all three pass. Tightened camera framing after
reviewing the initial images. A trial FOV of 25 was rejected by Minecraft and visibly
fell back to its default; discarded that capture, retained FOV 35, moved the rhythmic
camera closer, and added a capture-time FOV assertion. Final checks and recapture passed.

Visually reviewed all three full-resolution images: readable module faces, visible
audio/control cables, all blocks inside the frame, consistent daylight/platform, and
no HUD or open panels. Four stepped voice rows make the complex patch easier to follow.
The README hero and block gallery are retained. The user guide uses the same new
rhythmic image beside its updated RP-008 recipe.

Self-review **PASS**: all executable changes are opt-in test-mod infrastructure;
production source, dependency pins, AGENTS.md, and existing worlds remain unchanged.
Full local command logs are retained under `run/make/readme-capture-logs/`; each native
instance retains its original screenshot, world, and game logs. No commit, push,
installation into the user's play instance, or publication occurred.

## Remaining gates

These short runs verify documentation and scripted interaction, not listening or
multiplayer quality. Each accepted run reported one audio underrun; maximum audio-block
times are recorded in the results file. No sustained performance acceptance is claimed.
Human listening/acoustic, physical-input, multiplayer/timing, sustained/underrun, and
earlier security/platform/release gates remain **PENDING**.
