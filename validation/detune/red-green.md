# Saw unison detune — 2026-11-04

Source: `b438683` plus this uncommitted change. User clarification selected unison/spread,
not fine pitch offset. The implementation uses three mono voices and 0..100 cents per side.

- **RED:** `make test TEST='*DetuneTest'` initially compiled and failed all eight cases
  because the Detune parameter did not exist; see `missing-control-red.xml`.
- **RED:** registering the control alone left missing side frequencies, legacy save
  migration and old network refusal failing; see `behavior-red.xml`. An additional
  overstrict raw-saw step assertion failed too: the constant-signal <0.02 criterion
  does not apply to a saw discontinuity. It was removed, retaining phase continuity and
  the 480-sample return-to-single-saw reference assertions. Fractional spectral assertions
  were tightened relative to center power so leakage cannot masquerade as side voices.
- **GREEN:** nine detune cases now pass, including complete v4 snapshot enforcement.
  `green.xml` covers finite/range/ownership/stale checks, mono reference, three pitches,
  fractional spread, CV disconnection, transition/return, extreme MIDI notes, stored
  values, exact legacy migration and preservation of malformed record bytes.
- **PASS:** `make verify`: 91 JUnit, 13 packaged GameTests, 12 Python launcher tests,
  formatting, Checkstyle, PMD and artifact inspection. The existing MIDI schema assertion
  was updated from 3 to 4 after the first full run exposed the obsolete expectation.
- **PASS:** native `make smoke GRADLE_ARGS=-PqaDetune TICKS=300`, normal exit, isolated
  `run/make/smoke-ht8vxjec`. Exact entry 25.125, slider 100, invalid -1 preserving 100,
  then zero all matched server snapshots. Inspected fractional/maximum PNGs: readable
  labels/range, numeric field, slider and Apply; no clipping at 854×480. Default/reset
  captures are also retained. These are programmatically exercised real game widgets.
- **PASS:** fixed 64-module/96-cable/eight-speaker DSP fixture, 48 kHz/128 frames,
  5,000 warmup plus 10,000 measured blocks: p95 0.185 ms at zero spread and 0.275 ms
  at 100 cents; zero steady allocated bytes. See `measurements.xml` and `results.json`.
- **PENDING:** human listening, physical MIDI audition and two-client timing. Native
  smoke reported one underrun, max block 1.172 ms and max server work 3.326 ms; the short
  smoke is not performance qualification. All earlier security/release gates remain.

## Self-review

Reviewed server authority and finite bounds, exact stored-only migration, complete v4
snapshots, retained enum ordinals, unknown-record preservation and downgrade guidance.
Unison calculation happens during graph preparation; sample processing has bounded
three-voice work and no allocation, locks, I/O or exponentiation. The blend is convex
and zero spread skips side DSP; re-enabling inherits center phase/history. Existing
graph ownership and local mute remain in effect. No dependencies or manual worlds changed.
No commit, push or publication. Runtime artifact hash is recorded in `results.json`.
