# Keyboard red–green evidence

- Initial `make test TEST='*KeyboardTest'` failed with missing KeyboardInput,
  KeyboardState/Performance, typed ports, octave and retrigger API. After implementation,
  all seven domain/session tests passed.
- Session-order regression was added before KeyboardSession existed and failed compilation;
  implementation then passed receive-spacing and stale-sequence cases.
- Packaged native probe exposed blurred labels: the base Screen render applies background
  blur before widgets. Moving panel/text into renderBackground corrected the screenshots.
- Native rapid-input regression (press A, octave up, press S in one frame) failed at tick
  350: expected D6/gate high, observed KeyboardState[note=84, gate=false]. This reproduced
  an older note acknowledgement clearing a pending octave edit, sending a stale revision.
- KeyboardScreen now waits for a strictly newer semantic revision before submitting
  deferred note input and suppresses renewals while the octave edit is pending.
  Re-running the same packaged probe passed at tick 350 with note=86, gate=true.

The probe executes actual panel handlers and server networking in a disposable native world.
It does not measure physical input latency or establish listening acceptance.
