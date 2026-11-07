# Amp / Speaker validation — 2026-11-06

Implemented AS-001–006 with the existing Minecraft 1.21.1 / NeoForge 21.1.218 /
Java 21 matrix. Host: Apple M2 Pro, 16 GiB RAM, macOS 26.6.2 (25G83).
Machine-readable results: [results.json](results.json); retained output excerpts:
[evidence.txt](evidence.txt). Complete local command logs are ignored by Git.

## Automated evidence

- Baseline Amp regressions: three expected failures before implementation (no Amp,
  Speaker accepted Gain In, legacy records remained combined). Observed green afterward.
- `make verify GAMETEST_DIR=run/make/amp-gametest-final`: PASS, 172 JUnit tests,
  35 packaged GameTests, Python launcher/release checks, formatting, Checkstyle, PMD,
  strict dependency verification, Java 21/package isolation, and ten asset sets.
- Coverage includes independent A/B/C/D articulation through a mixer and one Speaker;
  gain precedence, Drone, disconnected silence, smooth graph adoption, third-Speaker
  rejection, whole-patch load eligibility, persisted switch/overload, waiting admission,
  legacy world/block decoding with cables, malformed records, oriented ports, NBT,
  removal, and stale/foreign edits. Existing keyboard/envelope timing tests remain passing.
- Full voice DSP fixture: 64 modules / 72 cables / eight Speakers, four sequencers,
  sixteen detuned saw oscillators and sixteen Amps. 5,000 warmup / 10,000 measured blocks:
  p95 **0.634958 ms**, **0 bytes** measured steady allocation. Existing 96-cable stress
  fixtures also pass with legal filter-resonance destinations replacing old Speaker gain.
- Local documentation links and `git diff --check`: PASS.

The benchmark caught a boxed zero introduced while sharing ADSR handling; it allocated
1,637,376 bytes in a measured sequencer run. Conditional primitive depth handling removed
it. Packaged cable interaction also exposed stale block views when admission enabled
another component during the same tick; synchronizing admitted state before subsequent
interactions fixed it. Both findings were observed failing and subsequently passing.

## Native evidence

All probes used disposable managed directories; manual worlds were not opened or edited.

- `make smoke GRADLE_ARGS=-PqaAmp TICKS=600 QA_DIR=run/make/amp-native-final`: PASS
  real Drone toggles, Hold Gate press/release, Speaker on/off, default enable, and native
  output. End-of-run audio p95 0.097833 ms; post-warmup server p95 0.318666 ms.
- `make smoke GRADLE_ARGS=-PqaAmp TICKS=400 QA_DIR=run/make/amp-native-capture`: PASS
  the same widgets, running sequencer, default-enabled Speaker, and reviewed captures.
  [Amp panel](../../docs/images/amp-panel.png), [Speaker panel](../../docs/images/speaker-panel.png),
  [four-voice patch](../../docs/images/four-voice-amp.png) show the actual packaged mod.
- `make smoke-load GRADLE_ARGS=-PqaAmp TICKS=1200 QA_DIR=run/make/amp-load-final`:
  PASS four running sequencers, 64 modules / 72 cables / eight default-enabled Speakers.
  End-of-run audio p95 **0.516375 ms**, server p95 **0.568709 ms**; 22,435 audio blocks,
  peak 0.118772, maximum queue 12 buffers, no failed audio engine.

Each final native probe recorded **one underrun**. The load run's maximum audio block
was **12.873833 ms**, overall server-work maximum **13.407791 ms**; post-warmup server
maximum was 1.548792 ms. The 600-tick UI run observed a 25.572500 ms audio maximum.
Passing p95 targets does not qualify those tails, listening, or sustained playback.

## Review and remaining gates

Codex self-review: PASS for Amp/spatial-output separation, shared envelope lifecycle,
explicit migration/registry mapping, finite gain bounds, revision-protected controls,
persisted enable intent versus activation, deterministic admission, overload latch,
no forced chunk loading, dedicated-server isolation, original generated artwork, and
preserved dependency pins. Automated checks do not establish audible compatibility.

PENDING: human listening/click/acoustic assessment, native two-client synchronization
and lifecycle acceptance, 30-minute sustained qualification, underrun/tail investigation,
and earlier security/platform/release gates. No commit, push, installation into a manual
play instance, or publication occurred.

Existing combined blocks become Amps and need a newly placed Speaker connected to Audio
Out (directly or through a Mixer). Migration retains old Volume as Amp Level and enables
Drone; new Speakers add another volume stage. Back up the world and read the user guide's
upgrade section before loading older compositions. Restore the backup to downgrade.
