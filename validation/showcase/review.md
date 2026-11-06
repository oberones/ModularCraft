# README showcase review — 2026-11-06

Source baseline: `205290b1e1a169854951c6e8a38b29ef838ecdd0`, plus uncommitted documentation
and test-fixture changes. No runtime behavior, protocol, save-format, or dependency changes.
No commit, push, publication, or changes to manual player worlds.

## Reproduce

```sh
make check
make gametest GAMETEST_DIR=run/make/gametest-showcase-20261106
make smoke GRADLE_ARGS=-PqaShowcase TICKS=1200
make assemble
python3 scripts/check-delivery.py
```

Choose a new GameTest directory when reproducing; do not overwrite an existing world.
The showcase launcher always creates a fresh instance. Use `qaShowcase` alone.

## Evidence

- **PASS — red/green:** the new native assertion first rejected the old four-module fixture
  with `Showcase needs one nine-module/ten-cable patch` ([red.txt](red.txt)). The completed
  fixture passes all-nine-kind, nine-module, ten-cable, enabled-speaker, running-sequencer,
  and ten-saved-image assertions ([native.txt](native.txt)).
- **PASS — checks:** 25 Python launcher/release tests and 157 JUnit tests; formatting,
  Checkstyle, and PMD passed ([check.txt](check.txt)).
- **PASS — packaged server:** all 32 GameTests passed in a fresh isolated directory
  ([gametest-fresh.txt](gametest-fresh.txt)). The initial default `make gametest` attempt
  failed 26 tests with quarantine/eligibility errors in the previously used test world
  ([gametest.txt](gametest.txt)); that world was retained. Fresh-world success does not
  establish recovery of that pre-existing state.
- **PASS — visual self-review:** inspected all ten original PNGs: consistent individual
  front-oblique views, complete fronts and ports, no HUD or dialogs; full synth has nine
  visible block fronts and actual connected cables. The hero was recaptured at 30° FOV
  to improve readability; individual views use 35° FOV.
- **PASS — README preview:** rendered with GFM tables using the bundled `marked` and
  headless Chromium, checked desktop (1280 px) and narrow (390 px) layouts, all ten image
  loads, and local links/anchors. Local previews remain in `build/showcase/`; this is a
  local rendering check, not a hosted GitHub publication ([docs.txt](docs.txt)).

- **PASS — archives:** `make assemble` and the delivery inspector passed. The current
  README, user guide, and ten PNGs are included in the sources JAR and project ZIP;
  test fixtures remain excluded from both JARs. The rebuildable project ZIP includes
  capture source and evidence. The runtime JAR hash still matches the photographed
  build ([artifacts.txt](artifacts.txt)).

## Capture provenance

Minecraft 1.21.1, NeoForge 21.1.218, Java 21, native macOS client, packaged
`modularcraft-0.2.1.jar` SHA-256:
`20b57e03e261abd66f0d76b10cfdcf84e9fa0bab7faed1d5559ac00df63f8036`.

Original world, logs, and screenshots:
`run/make/smoke-4x5myrwq/`. Images in `docs/images/` are byte-for-byte copies of its ten
`screenshots/*.png` files. No compositing, retouching, generated artwork, or shaders.
Requested window: 1920×1080; actual native framebuffer: **1920×1052** for all ten images.
File sizes, hashes, and dimensions are recorded in [results.json](results.json).

The photographed patch uses the [documented wiring and settings](../../docs/user-guide.md#showcase-all-nine-blocks-together).
A separate, unconnected display pedestal cycles through the block types for close-ups;
it does not alter the nine-module instrument.

## Limits

This run establishes screenshot capture and accepted patch state only. It recorded three
audio underruns and a maximum audio block time of 20,772,083 ns while other validation
work was also running; it is not a performance or acoustic qualification run. Human
listening, physical MIDI, two-client timing, sustained load, and all earlier release/security
gates remain PENDING/BLOCKED as recorded in the main ledger. No acceptance gate is closed
by these screenshots.
