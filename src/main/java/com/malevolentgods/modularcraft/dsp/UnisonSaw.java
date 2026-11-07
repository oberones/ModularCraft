package com.malevolentgods.modularcraft.dsp;

/**
 * Three mono saw voices at center pitch and symmetric 0..100-cent offsets. Prepared off the audio
 * thread from validated module values. Audio owns next/adopt; both are bounded and allocation-free.
 * Zero spread uses the original center saw. Nonzero spread averages all three voices; a 10 ms mix
 * ramp preserves the selected level ceiling while entering or leaving unison.
 */
public final class UnisonSaw {
  private final Oscillator[] voices = {new Oscillator(), new Oscillator(), new Oscillator()};
  private final Smoother[] frequencies;
  private final Smoother mix;

  public UnisonSaw(double baseHz, double cents, long sample) {
    double ratio = Math.pow(2, cents / 1200);
    frequencies =
        new Smoother[] {
          new Smoother(baseHz), new Smoother(baseHz / ratio), new Smoother(baseHz * ratio)
        };
    mix = new Smoother(cents == 0 ? 0 : 1);
    for (int i = 0; i < voices.length; i++)
      voices[i].phase(sample * frequencies[i].current() / 48000.0);
  }

  /**
   * Transfer phase/history and ramp to the newly prepared pitch/mix without changing the source.
   */
  public void adopt(UnisonSaw previous) {
    for (int i = 0; i < voices.length; i++) {
      int source = previous.mix.current() == 0 ? 0 : i;
      voices[i].copyFrom(previous.voices[source]);
      double target = frequencies[i].current();
      frequencies[i].reset(previous.frequencies[source].current());
      frequencies[i].target(target);
    }
    double target = mix.current();
    mix.reset(previous.mix.current());
    mix.target(target);
  }

  /** Set a new concert pitch with the existing 10 ms ramp; audio-owner only. */
  public void pitch(double hz, double cents) {
    double ratio = Math.pow(2, cents / 1200);
    frequencies[0].target(hz);
    frequencies[1].target(hz / ratio);
    frequencies[2].target(hz * ratio);
  }

  public double next(double level) {
    double center = voices[0].next(frequencies[0].next(), level);
    double lowHz = frequencies[1].next(), highHz = frequencies[2].next();
    double sideMix = mix.next();
    if (sideMix == 0) return center;
    double low = voices[1].next(lowHz, level), high = voices[2].next(highHz, level);
    return center * (1 - 2 * sideMix / 3) + (low + high) * sideMix / 3;
  }
}
