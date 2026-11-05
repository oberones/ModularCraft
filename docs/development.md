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
Minecraft normally: create or select a world and use the [player guide](../README.md).
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
| `make test` | JUnit suite; use `TEST='*SceneTest'` to select tests |
| `make test-tools` | Filesystem isolation and launcher command/failure tests, no Minecraft |
| `make lint` / `make format` | Check Java formatting/static analysis / apply Java formatting |
| `make gametest` | Packaged JAR on a headless GameTest server in `run/make/gametest` |
| `make gametest-dev` | GameTests against development classes |
| `make verify` | Launcher tests, complete Gradle build/check, packaged GameTests, delivery inspection |
| `make ci` | `verify` with `--no-daemon`; headless, no game window or external audit service |

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

`make smoke GRADLE_ARGS=-PqaSequencer TICKS=650` launches the packaged JAR in a fresh world,
creates an autonomous four-channel/two-voice patch and three clock followers, edits the
editor through native widgets, captures the panel and all four facing directions, and
checks 20 audio teardown/recovery cycles. It retains the generated world/log/screenshots
under the printed `run/make/smoke-*` directory. Do not use an existing manual world.
Run `make test TEST='*Sequencer*Test'` for focused musical and persistence regressions;
`make verify` includes the packaged lifecycle GameTest and artifact/resource checks.


Square probe: `make smoke GRADLE_ARGS=-PqaSquare TICKS=600` creates a fresh square/filter/
envelope/speaker fixture, exercises fractional PWM, slider maximum, invalid rejection,
restoration to 50%, note selection and compact bounds, then exits. Screenshots and logs live
in the newly created managed QA directory; existing worlds are never reused. This is a
functional/visual probe, not a human audition or multiplayer timing pass.
