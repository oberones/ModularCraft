# Square oscillator review — 2026-11-04

Implemented the approved separate Square Oscillator with fractional 5–95% manual PWM,
50% default, shared note/Hz controls, Level and mono audio/pitch ports. No LFO, extra
input, unison or new dependency. [Results](results.json), [panel](panel.png),
[note menu](notes.png), [compact layout](compact.png).

## Automated evidence

- [Initial red](red.txt): all five initial square behavior tests failed on absent module/
  parameter enum values before implementation. The completed suite adds malformed save/
  snapshot and Level-transition cases; all seven square tests pass.
- [Alias red](alias-red.xml): two-sample PolyBLEP with the saw's 21.6 kHz decimator corner
  failed at the coherent 8 kHz bin / 5% duty (-28.28 dB versus -35 dB required). A wider
  four-sample edge alone remained insufficient. The pulse now uses an 18 kHz corner
  with the same 4x/95-tap design and integrated cubic B-spline edges. Saw coefficients
  retain their existing .1125 normalized corner and arithmetic.
- [Measurement results](MeasurementTest.xml): independent 16x pulse reference, 5/25/50/95%
  duty and coherent bins near 55/220/880/3520/8000 Hz; worst alias residual -86.279 dB,
  DC below -60 dBFS. These alias measurements follow the existing non-harmonic residual
  metric, not a claim of identical pass-band response or listening acceptance.
- Square extremes include manual 20/8000 Hz and MIDI 0/127, finite peak bounds, pitch/
  harmonic behavior, phase/history adoption, CV disconnect, smoothing to Level 0,
  exact fractional persistence, v1 quarantine and protocol 1–5 refusal.
- [Full verify](verify.txt): 124 JUnit, 19 packaged GameTests, 12 launcher tests; formatting,
  Checkstyle/PMD, Java 21 artifact inspection and seven complete block asset sets pass.
- Four-patch 64-module/96-cable/eight-speaker synthetic DSP fixture: 5,000 warmup and 10,000
  measured 128-frame blocks at 48 kHz. Square p95 0.211 ms; mixed square/max-detuned saw
  p95 0.261 ms; zero measured steady allocation. Same-run all-saw baseline 0.213 ms and
  max-unison 0.295 ms. These are local synthetic measurements, not native load acceptance.

## Native evidence and limits

`make smoke GRADLE_ARGS=-PqaSquare TICKS=600` passed with the packaged JAR in fresh
`run/make/smoke-un40b9sb`; [native transcript](native.txt). Apple M2 Pro, macOS 26.6.2,
Java 21.0.12.1, 854x480 window, render distance 12, simulation 8, VSync off.

Real widgets accepted 25.125%, slider 95%, rejected -1 without changing accepted 95%,
then restored 50%. The shared note probe checked exact note frequencies, arbitrary Hz,
below-E0 rejection, permission loss, retained drafts, Escape/Tab, both
GUI scales and 320x240 logical bounds. Panel, dropdown and compact screenshots were
visually inspected: readable labels, no clipped controls, matching saw workflow.

The 600-tick run recorded one underrun, maximum server work 2.975 ms (above the 2 ms
acceptance target), maximum audio block 1.685 ms, final audio p95 0.0363 ms, failed=false.
Native sustained performance remains unqualified; previous failures are not erased.
Human listening/acoustic and two-client acceptance remain PENDING, as do earlier
security, other-platform and release gates. No physical MIDI hardware was exercised.

## Explicit self-review

Reviewed enum append compatibility, strict fractional bounds and integer classification,
server ownership/revisions and CV status, matching-kind graph adoption, save quarantine,
protocol-v6 refusal, and client/common isolation. Audio state is preallocated and copied
only at graph adoption; the fixed pulse voice and existing quotas bound work. The wider
nonnegative edge kernel and L1 decimator preserve the selected Level ceiling after mean
compensation. No saw runtime synthesis path was replaced.

Reviewed all seven generated asset sets, original provenance, localized controls, native
screenshots, RP-005 instructions and upgrade/backup notes. No existing worlds were removed,
no pins changed, and no commit, push or publication occurred. Manual listening and native
performance results are deliberately kept separate from functional passes.
