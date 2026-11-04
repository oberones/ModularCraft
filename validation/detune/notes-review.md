# Oscillator note controls — evidence and self-review

Base `b58caec` plus this uncommitted change, 2026-11-04. No commit/push/publication.
Minecraft 1.21.1, NeoForge 21.1.218, Java 21; Apple M2 Pro, macOS 26.6.2.

## Results

- PASS: six pure Java note tests cover A4/A3/C4, enharmonics, all 108 selector
  combinations, invalid bounds, nearest-note initialization, explicit apply, stale views,
  rejection retention, accepted edits and a newer draft during an earlier acknowledgement.
- PASS: 115 JUnit, 16 packaged GameTests, 12 launcher tests, formatting, Checkstyle, PMD,
  and artifact inspection via `make verify`. Exact C4 doubles survive packet/block NBT
  roundtrips. Invalid, stale and foreign parameter edits preserve accepted state/revision.
- PASS: spectral regression confirms a keyboard still overrides saved C4 and disconnecting
  restores it through graph adoption. No DSP or musical smoothing changes were made.
- PASS: `make smoke GRADLE_ARGS=-PqaDetune TICKS=600` using the packaged JAR in fresh
  `run/make/smoke-mbhotspr/`. Mouse and keyboard dropdown interaction, no edit on selection,
  C#/Db4 and C4 exact server acceptance, arbitrary Hz entry, invalid C0, permission loss,
  Escape/Tab, draft retention and resizing all passed. Existing detune assertions still pass.
- PASS: final [normal](notes-normal.png), [dropdown](notes-menu.png),
  [invalid range](notes-range.png), [compact](notes-compact.png), and
  [narrow](notes-narrow.png) screenshots inspected. Normal/compact use actual GUI scales
  1/2 at 854×480; the narrow check resizes the screen to 320×240 within the existing
  framebuffer. Widget bounds are asserted. An initial status overflow at narrow width
  was corrected with wrapping and the native probe rerun successfully.
- PASS: all seven note translations match the asset generator; `git diff --check` clean.
- PENDING: human listening, spoken narrator audition and two-client acceptance. Native
  automation checks widget behavior, not human usability or acoustic timing.
- FAIL / prior gate retained: final native smoke observed one underrun and maximum server
  work of 3.293 ms against the <2 ms target. This feature adds no DSP processing and does
  not claim to resolve prior native performance/security/platform/release gates.

## Self-review

Reviewed the complete change for authority, compatibility, control lifecycle and bounds.
The UI submits an unrounded Frequency double through the existing parameter path. Note
and octave never enter world or network schemas. Selection/preview does not mutate the
accepted ModuleState; acknowledgement requires a newer revision and matching frequency,
and preserves a subsequently changed draft. Permission revocation disables editing,
closes menus and discards any uncommitted slider movement. Existing server validation,
CV precedence, dependency pins and world saves remain intact. No new dependencies.
Dropdowns have bounded 12/9-item lists, keyboard navigation and narrated labels. Localization,
player guide, feature spec/plan and the native probe instructions are synchronized.

## Red–green excerpts

The initial JUnit run failed because the new draft helper did not exist; the native
probe then failed on the absent note dropdown. Final results below are from the completed
implementation. Full native log/screenshots remain in the disposable instance; Gradle
JUnit reports are under `build/test-results/test/`.

## JUnit red

```text
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:3: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:10: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:10: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:20: error: cannot find symbol
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:21: error: cannot find symbol
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:26: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:26: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:40: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:40: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:51: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:58: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:58: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:75: error: cannot find symbol
  symbol:   class OscillatorNote
/Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:75: error: cannot find symbol
  symbol:   class OscillatorNote
  /Users/oberon/Projects/coding/mods/minecraft/ModularCraft/src/test/java/com/malevolentgods/modularcraft/OscillatorNoteTest.java:3: error: cannot find symbol
    symbol:   class OscillatorNote
BUILD FAILED in 1s
```

## Native red

```text
[18:28:47] [Render thread/ERROR] [ne.ne.bu.EventBus/EVENTBUS]: Exception caught during firing event: Oscillator note dropdown is missing
java.lang.IllegalStateException: Oscillator note dropdown is missing
java.lang.IllegalStateException: Oscillator note dropdown is missing
java.lang.IllegalStateException: Oscillator note dropdown is missing
BUILD FAILED in 21s
```

## Final verify

```text
BUILD SUCCESSFUL in 19s
[18:34:39] [Server thread/INFO] [minecraft/GameTestServer]: All 16 required tests passed :)
BUILD SUCCESSFUL in 7s
PASS: Java 21 artifacts, six complete asset sets, no test fixtures or bundled host/runtime libraries.
```

## Final native

```text
BUILD SUCCESSFUL in 434ms
MODULARCRAFT_QA client p95BlockNs=80416 blocks=1804 underruns=1 maxBlockNs=839458 peak=0.025075530633330345 maxQueued=12 failed=false
MODULARCRAFT_QA detune tick=120 accepted=25.125
MODULARCRAFT_QA detune tick=160 accepted=100.0
MODULARCRAFT_QA server lastNs=57375 maxNs=3292625
MODULARCRAFT_QA detune tick=200 accepted=100.0
MODULARCRAFT_QA client p95BlockNs=66791 blocks=3676 underruns=1 maxBlockNs=839458 peak=0.025075530633330345 maxQueued=12 failed=false
MODULARCRAFT_QA detune tick=240 accepted=0.0
MODULARCRAFT_QA note frequency=220.0
MODULARCRAFT_QA client p95BlockNs=52583 blocks=5552 underruns=1 maxBlockNs=839458 peak=0.025075530633330345 maxQueued=12 failed=false
MODULARCRAFT_QA note frequency=277.1826309768721
MODULARCRAFT_QA note frequency=432.123
MODULARCRAFT_QA note frequency=432.123
MODULARCRAFT_QA server lastNs=48083 maxNs=3292625
MODULARCRAFT_QA client p95BlockNs=47708 blocks=7429 underruns=1 maxBlockNs=839458 peak=0.025075530633330345 maxQueued=12 failed=false
MODULARCRAFT_QA note frequency=432.123
MODULARCRAFT_QA note frequency=261.6255653005986
MODULARCRAFT_QA note frequency=330.123
MODULARCRAFT_QA client p95BlockNs=27625 blocks=9301 underruns=1 maxBlockNs=839458 peak=0.025075530633330345 maxQueued=12 failed=false
MODULARCRAFT_QA note frequency=587.3295358348151
MODULARCRAFT_QA server lastNs=50541 maxNs=3292625
MODULARCRAFT_QA client p95BlockNs=27375 blocks=11177 underruns=1 maxBlockNs=3358042 peak=0.025075530633330345 maxQueued=12 failed=false
BUILD SUCCESSFUL in 43s
```
