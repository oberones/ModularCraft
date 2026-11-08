# Development and quick validation

Use GNU Make 3.81+ (the macOS system `make` works), Python 3.9+, and Java 21. Gradle,
NeoForge, and tool dependencies use the pinned wrapper and strict verification metadata.
Run commands from the project directory, or use `make -C /path/to/ModularCraft`.
`make` alone prints help; it does not build or launch anything.

## Launch the built JAR

```sh
make client                         # Interactive Minecraft; build and stage current JAR first
make smoke                          # Fresh RP-001 patch, about one minute, automatic exit
make smoke-recovery TICKS=400        # Twenty recovery cycles, about twenty seconds, then exit
make smoke GRADLE_ARGS=-PqaCables TICKS=600 # Adjacent modules; three cable screenshots, then exit
make smoke GRADLE_ARGS=-PqaCableRemoval TICKS=300 # Remove/reconnect via native client dispatch
make smoke-load TICKS=1200           # 64 modules / 96 cables / eight speakers, about one minute
```

`make run` and `make play` are aliases for `make client`. The interactive client opens
Minecraft normally: create or select a world and use the [player guide](user-guide.md).
It uses `run/make/client`; worlds, options, local audio preferences, and screenshots persist
between launches. Close Minecraft normally when finished. `make client-dev` instead uses
development classes in `run/client`, including any existing worlds there.

Packaged targets run `runtimeArtifact`, a Gradle task that builds the current runtime JAR
and writes `build/runtime-artifact.json` from Gradle's actual archive name. There is no
version duplicated in the Makefile and no guess among stale JARs in `build/libs`.
The launcher copies that exact JAR into the selected instance's `mods/`, prints its SHA-256,
then passes `-Ppackaged` to exclude the main development mod from the launch configuration.
The pinned NeoForge development launcher supplies the host game and dependencies; this
is not a separately installed vanilla/CurseForge launcher profile. The native smoke probe
prints `MODULARCRAFT_QA artifact=...jar...` so the loaded artifact can be checked directly.

Automated smoke modes always create a **new** `run/make/smoke-…` directory. They use the
test-only fixture, copy only a generated GameTest `level.dat` into `saves/ModularCraft-QA`,
create the modules/cables, alternate redstone gates every two seconds, and exit after
`TICKS` game ticks. A packaged GameTest server creates the seed automatically if missing.
No manual world is used as a fixture. The first launch can take longer while game assets
and dependencies are downloaded. Smoke options skip onboarding, keep the game active
without focus, and use render distance 12, simulation 8, and VSync off.

Smoke durations are game ticks, not a wall-clock timeout: 1,200 ticks is about one minute
at 20 TPS. Recovery needs at least 400 ticks for all twenty teardown assertions. Other
smoke modes accept at least 40 ticks. Logs and generated worlds are retained under the
printed instance directory, including `logs/latest.log`; no cleanup target deletes them.

```sh
make client CLIENT_DIR=run/make/second-client
make smoke QA_DIR=run/my-fresh-qa TICKS=400
make benchmark                      # Single-client load fixture; 36,000 ticks by default
make benchmark BENCHMARK_TICKS=2400
make client GRADLE_ARGS='--offline'  # Works after dependencies/game assets are cached
```

`QA_DIR` must be new or empty. Manual instance overrides must be below this project's
`run/`, empty on first use or already managed by this launcher, and free of symlinks.
The marker `.modularcraft-validation.json` records the mode, managed JAR name, and hash.
Only that unchanged, launcher-owned JAR may be replaced on the next launch; other mods,
saves, and configuration remain intact. A changed JAR or additional ModularCraft JAR
requires you to move/preserve it before retrying. A per-instance lock prevents concurrent
launcher processes from modifying the same instance while the game is running.

The automatic fixture is a development test mod, separate from the distributed runtime
JAR. One-client smoke tests, teardown counters, and the single-client benchmark do not
satisfy two-client timing, acoustic capture, listening, device-switching, or full SC-005
acceptance. Follow [acceptance.md](acceptance.md) for those procedures.

The opt-in `-PqaCables` smoke variant places RP-001's four modules directly adjacent on
a solid floor. It requests a 1920×1080 window with 50° FOV, selects spectator mode,
hides the HUD, and moves through front, oblique, and rear views. At 160, 340, and 520
client ticks it saves `cables-front.png`,
`cables-oblique.png`, and `cables-occlusion.png` in that fresh instance's `screenshots/`.
Use at least 600 ticks and inspect the captures for a single solid cable per connection,
jack alignment, a taper toward each input, clearance above the floor, and rear-view
occlusion. This variant is for `make smoke`, not load/recovery qualification; ordinary
interactive clients and existing worlds are unaffected.

The `-PqaCableRemoval` variant equips the patching tool and an offhand stick in a fresh
RP-001 world. It dispatches Shift-right-click through the native client game mode, checks
the server's synchronized cables after removal/reconnection/repeated removal, and saves
`cable-removal-140.png`, `cable-removal-200.png`, and `cable-removal-240.png`. Use at least
300 ticks. This automated interaction probe does not substitute for physical mouse/key,
listening, or two-client acceptance.

## Build and check

| Command | Work performed |
|---|---|
| `make build` / `make all` | Launcher tests, Gradle checks, runtime/source JARs, full project ZIP |
| `make jar` | Current runtime JAR and artifact descriptor only; quick launch preparation |
| `make assemble` | All archives, without running checks |
| `make package` | Build/check, inspect distributables, write `build/libs/SHA256SUMS` |
| `make checksums` | Assemble, inspect archives, refresh checksums |
| `make check` | Launcher tests plus Java tests, formatting, Checkstyle, PMD |
| `make verify-dependencies` | Resolve all locked dependency configurations with strict checksum verification, including PMD auxiliary classpaths |
| `make test` | JUnit suite; use `TEST='*SceneTest'` to select tests |
| `make test-tools` | Filesystem isolation and launcher command/failure tests, no Minecraft |
| `make lint` / `make format` | Check Java formatting/static analysis / apply Java formatting |
| `make gametest` | Packaged JAR on a headless GameTest server in `run/make/gametest` |
| `make gametest-dev` | GameTests against development classes |
| `make verify` | Launcher tests, dependency preflight, complete Gradle build/check, packaged GameTests, delivery inspection |
| `make ci` | `verify` with `--no-daemon`; headless, no game window or external audit service |

GameTests for queued controls wait for the server inbox to drain before checking results.
The server processes requests within a 1.5 ms budget per tick, so a fixed three-tick
delay is not a completion guarantee on CI. These tests retain their 100-tick timeout
and assert accepted/rejected state once processing completes; they do not increase
the runtime budget or retry failed assertions.

## Tagged releases

The mod version is recorded in `.cz.toml` and `gradle.properties` (`mod_version`).
Commitizen's `version_files` updates the Gradle property during a bump, alongside its
own version and `CHANGELOG.md`, before creating the bump commit and `v<version>` tag.
Gradle uses that property for archive names, NeoForge mod metadata, and the JAR's
`Implementation-Version`. Java 21 and the pinned Gradle, Minecraft, and NeoForge
versions are independent of the mod version and are not bumped.

With Commitizen installed (validated with 4.8.3), prepare a release from a clean,
reviewed working tree after running `make ci`:

```sh
cz bump --check-consistency --dry-run  # Preview the next version and changelog
cz bump --check-consistency           # Update files, commit, and create the version tag
python3 scripts/release.py check --tag "v$(cz version --project)"
git push --atomic origin HEAD "refs/tags/v$(cz version --project)"
```

The bump and push commands above intentionally create a commit/tag and publish them;
run them only when ready to release. Use `cz bump --check-consistency --prerelease rc`
for a release candidate. Review any partial file updates if Commitizen reports a
consistency failure. The optional real-Commitizen regression in `make test-tools`
runs in a disposable repository; it is skipped when `cz` is not installed.

The Verify workflow keeps branch/PR checks and accepts version-tag pushes with or
without `v`, including prerelease/build suffixes. Commitizen generates `v` tags.
Malformed version-like tags fail, and the normalized tag must exactly match both
committed versions. CI does not rewrite versions or create tags. `make test-tools`
also checks committed version consistency during ordinary builds/checks.

Both verification/build and publishing use Ubuntu 24.04 x64 GitHub-hosted runners.
The JAR is platform-independent; CI runs headless GameTests and does not require
a graphical Minecraft client or audio device. Native playback qualification remains
separate from the build runner's operating system.

Gradle selects `gradle/linux.lockfile` on Linux and the existing `gradle.lockfile` on
macOS. Both pin the same dependency versions; their runtime configuration memberships
account for Minecraft's Linux transport and macOS Objective-C bridge. Dependency updates
must refresh and review the locks on both build platforms while keeping checksum
verification strict.

Run `make verify-dependencies` to catch lock/checksum failures without compiling Minecraft.
The build realizes PMD tasks before enumerating configurations because Gradle creates
their auxiliary classpaths lazily; these use runtime dependencies even though they
extend the compile classpaths. When intentionally refreshing locks, run
`./gradlew --dependency-verification strict dependencies --write-locks` on each platform,
review the coordinate/version and configuration changes, then run
`make verify-dependencies`. CI includes this preflight in `make verify`/`make ci`.

Only a successful full `make ci` run can publish. The build job stages the exact
runtime and sources JARs selected by `build/runtime-artifact.json`, checks the runtime
JAR's embedded versions, and creates SHA256SUMS covering both JARs. Only
`modularcraft-<version>.jar` and `modularcraft-<version>-sources.jar` are uploaded
as release assets; project ZIPs remain local build outputs. SHA256SUMS stays in the
intermediate Actions artifact, and its contents appear in the release notes.
The publishing job verifies these checksums and uses GitHub's repository token;
no personal token is required. The intermediate Actions artifact lasts seven days.

Releases are titled `ModularCraft <version>` and include generated notes plus links
to compatibility, migration, license/notices, dependency/license inventories, and
acceptance records at the tag. Prerelease suffixes
produce GitHub prereleases. An existing release causes publication to fail without
replacing assets; use a new version for changed content. In-progress tag releases
are not canceled by subsequent pushes.

For local inspection after `make ci`, run
`python3 scripts/release.py stage --tag "v$(cz version --project)"`. Staging requires
an absent `build/release` directory so stale files cannot enter a release; archive or
remove that generated directory before staging again. Tag creation, GitHub publication,
and native/listening/multiplayer/security acceptance remain distinct operations.

## Build overrides

All Make-managed Gradle commands keep dependency verification strict. `GRADLE_ARGS`
adds ordinary flags such as `--offline`, `--stacktrace`, or `--rerun-tasks`; `GRADLEW`
and `PYTHON` override executable paths. Do not use `GRADLE_ARGS` to override launcher-owned
`-Ppackaged`, `-Pqa*`, or run-directory properties. Named path/duration variables above
keep the validation instance consistent. `.NOTPARALLEL` serializes targets in one Make
invocation, even with `-j`, to protect shared build/run outputs. Separate invocations
should not build/clean the same checkout concurrently.

## Server and maintenance targets

`make server` stages the built JAR and starts a normal headless server in `run/make/server`.
The server operator must review Minecraft's terms and set `eula=true` in that instance's
generated `eula.txt` before the server can run. The launcher never accepts terms or changes
EULA state. Use `SERVER_DIR=run/make/another-server` to isolate another instance.
`make server-dev` uses development classes and `DEV_SERVER_DIR` (default `run/server`).
Type `stop` in a running normal server console for an orderly save/shutdown.

| Command | Work performed |
|---|---|
| `make assets` | Regenerate authored textures/models/localization and editable SVGs |
| `make inventory` | Refresh resolved dependency coordinate/hash inventory; no version/hash acceptance |
| `make integrity` | Verify recorded hashes against official origins; network needed for uncached records |
| `make audit` | Send only public dependency coordinates to OSV; store advisory report for review |
| `make dependencies` / `make tasks` / `make info` | Inspect Gradle dependencies, available tasks, tool versions |
| `make clean` | Remove Gradle `build/` outputs only; keep run directories, worlds, caches, validation logs |
| `make rebuild` | Clean build outputs, then build/check again |
| `make stop` | Stop Gradle daemons; does not terminate running Minecraft processes |

`DEV_CLIENT_DIR`, `DEV_SERVER_DIR`, and `GAMETEST_DIR` customize their named runs. Automated
smoke seed generation always uses the managed `run/make/gametest` seed instance. These
targets do not publish, commit, push, update dependency pins, or delete validation worlds.


Keybinding native probe: `make smoke GRADLE_ARGS=-PqaKeybindings TICKS=60` checks that
K leaves gameplay, chat, and the pause menu unchanged and that the pause menu's
ModularCraft Audio button still opens settings.

Keyboard native probe: `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500` builds RP-003
in a fresh managed world, opens its piano panel, exercises notes/priority/octave/mouse/
settings return/close, asserts server-accepted note states and captures six screenshots
under that instance's `screenshots/`. It does not reuse player saves. Use at least 500
ticks; keep the game window focused so the normal focus-loss safety release does not
interrupt synthetic holds. Listening and two-client timing remain separate checks.


The keyboard fixture `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500` now injects MIDI A4,
sustain on, Note Off under sustain, and pedal release into the real keyboard panel. It retains
GUI overlap, octave acknowledgement, mouse and close assertions and captures the MIDI controls.
It uses no physical MIDI input and does not change local device preferences outside the fresh
fixture. Run `make test TEST='*Midi*Test'` for deterministic input, fake-provider lifecycle,
preferences and snapshot tests. Physical controller procedures are in [acceptance.md](acceptance.md).


Oscillator probe: `make smoke GRADLE_ARGS=-PqaDetune TICKS=600` opens the oscillator
in a fresh RP-001 world. It checks exact 25.125-cent entry, slider maximum, invalid negative
entry rejection, and return to zero, then exercises the note/octave dropdowns with mouse
and keyboard input. Assertions cover no edit before Set Note, full-precision C#/Db4 and C4
frequency acceptance, arbitrary Hz entry, invalid C0, permission loss, Escape/Tab navigation,
unapplied drafts across server updates and resizing, and widget bounds down to 320×240.
It captures `detune-*.png` and `notes-*.png` at normal and compact GUI sizes in the fresh
instance's `screenshots/`. Use at least 600 ticks for the complete probe.
Unit regressions use `make test TEST='*OscillatorNoteTest'` and `make test TEST='*DetuneTest'`;
the full suite includes keyboard pitch precedence, persistence, and maximum-spread DSP
allocation/timing. These synthetic checks do not establish listening or multiplayer acceptance.



### Euclidean sequencer probe

`make smoke GRADLE_ARGS=-PqaSequencer TICKS=780` launches the packaged JAR in a fresh world,
creates an autonomous four-channel/two-voice patch and three clock followers, edits the
editor through native widgets, verifies keyboard redstone toggling, disabled Start/Stop,
powered/unpowered snapshots and compact layout, captures the panel and all four facing
directions, and checks 20 audio teardown/recovery cycles. It retains the generated world/log/screenshots
under the printed `run/make/smoke-*` directory. Do not use an existing manual world.
Run `make test TEST='*Sequencer*Test'` for focused musical and persistence regressions;
`make verify` includes the packaged lifecycle GameTest and artifact/resource checks.


Square probe: `make smoke GRADLE_ARGS=-PqaSquare TICKS=600` creates a fresh square/filter/
envelope/speaker fixture, exercises fractional PWM, slider maximum, invalid rejection,
restoration to 50%, note selection and compact bounds, then exits. Screenshots and logs live
in the newly created managed QA directory; existing worlds are never reused. This is a
functional/visual probe, not a human audition or multiplayer timing pass.

### LFO native fixtures

`make smoke GRADLE_ARGS=-PqaLfo TICKS=600` creates a disposable 16-module patch, drives
all LFO selectors/frequency, resonance depth and four ADSR depths (including compact
scrolling and invalid input), and captures `lfo-*.png` screenshots in that instance.
`make smoke-load GRADLE_ARGS=-PqaLfo TICKS=1200` exercises four 16-module/24-cable patches
and reports audio counters plus server p95/max. Each smoke run needs a fresh QA directory.
For repeated packaged GameTests, select a fresh `GAMETEST_DIR` to avoid accumulating
persisted test identities against world quotas; do not delete or reset manual worlds.

Mixer probes (fresh managed worlds; retain the launcher-selected artifact path in evidence):

```sh
make verify GAMETEST_DIR=run/make/mixer-gametest
make smoke GRADLE_ARGS=-PqaMixer TICKS=600
make smoke-load GRADLE_ARGS=-PqaMixer TICKS=1200
```

The panel probe edits real widgets, validates live drag completion and mute/solo, captures
full/compact/world views, tests local mute/settings return, NBT roundtrip and removal.
The load probe reports server p95 and ordinary native audio counters. Neither is a human
listening test, two-client session, real disk restart acceptance or 30-minute load run.


### README showcase screenshots

`make smoke GRADLE_ARGS=-PqaShowcase TICKS=1200` builds a fresh, packaged ten-module,
eleven-cable instrument and captures `synth-setup.png` plus eleven individual block PNGs in
that instance's `screenshots/` directory. Use this flag alone, without other QA variants.
The test-only fixture sets daylight, clear weather, a neutral platform, spectator camera,
and hidden HUD. It requests a 1920×1080 window; actual framebuffer dimensions depend on
the host display. A separate display pedestal cycles through all eleven registered blocks,
including Step Sequencer, without altering the original ten-block connected instrument.
Client assertions require the original instrument, an enabled speaker, a running sequencer,
and all twelve saved screenshots. The pedestal schedule follows the module count; use at
least 1200 ticks for the current eleven blocks.

Inspect the images before copying them to `docs/images/`; keep original captures and
logs in the generated run directory. The reproducible patch and settings are in the
[user guide](user-guide.md#showcase-all-ten-blocks-together). This is visual documentation
evidence, not a listening, multiplayer, or performance qualification run.


### README example patch screenshots

Run each recipe on its own; the launcher creates a fresh disposable world when `QA_DIR`
is omitted. Do not combine this selector with other QA flags.

```sh
make smoke GRADLE_ARGS=-PqaReadmeExample=drone TICKS=600
make smoke GRADLE_ARGS=-PqaReadmeExample=lead TICKS=600
make smoke GRADLE_ARGS=-PqaReadmeExample=rhythms TICKS=600
```

These test-only fixtures build the [README examples](user-guide.md#readme-example-patches)
with 4/3, 5/5, and 15/17 modules/cables respectively. Client assertions check the complete
wiring and parameter settings, enabled Speaker, and running rhythm sequencer. The lead
probe presses C4/D4, checks authoritative pitch and Amp gate, releases D4 back to C4,
and releases C4 before taking the overview. These are synthetic UI inputs.

Each run requests a 1920×1080 window, fixes daylight and weather, hides the HUD, and
captures one PNG at tick 350. At tick 400 it verifies the file was written; the console
reports the actual framebuffer dimensions. Inspect the image before copying it from the
printed instance's `screenshots/` folder to `docs/images/`. Filenames are
`example-drone.png`, `example-keyboard-lead.png`, and `example-four-voice-rhythms.png`.
Keep original captures and logs in the generated instance; record evidence in the ledger.
The test mod is excluded from the runtime and sources JARs. Screenshot and scripted-input
checks do not establish human listening, physical-input, or multiplayer acceptance.

### Amp / Speaker probes

`make smoke GRADLE_ARGS=-PqaAmp TICKS=600` creates a disposable four-voice patch,
checks Drone/Hold Gate/Speaker widgets, and captures both panels and the complete rig.
`make smoke-load GRADLE_ARGS=-PqaAmp TICKS=1200` measures four 20-module/22-cable
patches with distinct fixture owners and eight default-enabled Speakers (80 modules/88 cables). It retains existing DSP/server budgets;
short runs and screenshots do not establish human listening or sustained acceptance.
Use a fresh `QA_DIR` for each probe; never use a manual world directory.

### Step Sequencer checks and documentation screenshots

`make smoke GRADLE_ARGS=-PqaStepSequencer TICKS=500` exercises the actual recording editor
in a fresh four-module/four-cable RP-009 patch. It requests 1920×1080, uses GUI scale 3,
sets daylight and clear weather, hides the HUD, and moves the cursor away from controls
before captures. At the end it closes the editor and uses a spectator camera framing all
four blocks and their cables. The editor still distinguishes recording and playback.

The native probe verifies entry, held-key suppression, rests, recording completion, compact
bounds, accepted wiring/settings, enabled Speaker, running sequence, and screenshot files.
Its seeded demonstration uses 16 steps, 120 BPM and **80 ms** gates (default **100 ms**),
with a rest entered at step 3. Copy these inspected, unmodified native captures:

| Fresh capture | Documentation image |
|---|---|
| Showcase `step-sequencer.png` | `docs/images/step-sequencer.png` |
| Step probe `step-full.png` | `docs/images/step-sequencer-panel.png` |
| Step probe `step-world.png` | `docs/images/example-step-melody.png` |

Keep all originals and logs in their generated run directories, including compact and
recorded-panel evidence. The screenshot probes are excluded from runtime JARs. Run each
capture flag separately; do not combine `qaShowcase` and `qaStepSequencer`.
The [documentation capture evidence](../validation/step-sequencer/documentation/README.md)
records the original run locations, image hashes, and visual review.

Use `make smoke-load GRADLE_ARGS=-PqaStepSequencer TICKS=1200` for four 20-module
melodic patches. These use fresh disposable worlds and report server/audio measurements;
they do not establish listening or two-client acceptance.
