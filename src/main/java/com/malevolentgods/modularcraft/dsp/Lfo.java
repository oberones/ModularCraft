package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.LfoSettings;

/** Analytic sample-domain LFO. A single server, audio, or visual timeline owns each instance. */
public final class Lfo {
  public record Checkpoint(
      long sample,
      long anchor,
      double phase,
      double fromRate,
      double rate,
      boolean external,
      boolean high,
      long lastClock,
      long interval,
      int pulse,
      long transition,
      double fromValue) {
    public Checkpoint {
      if (sample < 0
          || sample > Long.MAX_VALUE - 1000000
          || anchor < 0
          || anchor > sample
          || !Double.isFinite(phase)
          || phase < 0
          || phase >= 1
          || !validRate(fromRate)
          || !validRate(rate)
          || lastClock < -1
          || lastClock > sample
          || interval < 0
          || interval > Long.MAX_VALUE / 256
          || pulse < 0
          || pulse > 63
          || transition < -480
          || transition > sample
          || !Double.isFinite(fromValue)
          || Math.abs(fromValue) > 1
          || (interval > 0 && lastClock < 0)) throw new IllegalArgumentException("lfo_checkpoint");
    }

    private static boolean validRate(double value) {
      return Double.isFinite(value) && value >= 0 && value <= 50.0 / 48000;
    }
  }

  private LfoSettings settings;
  private long anchor, lastClock = -1, interval, transition = -480;
  private double phase, fromRate, rate, fromValue;
  private boolean external, high;
  private int pulse;

  public Lfo(LfoSettings settings, long sample) {
    if (sample < 0) throw new IllegalArgumentException("negative_sample");
    this.settings = settings;
    anchor = sample;
    rate = settings.frequency() / 48000;
    fromRate = rate;
  }

  public Lfo(LfoSettings settings, Checkpoint state) {
    this.settings = settings;
    anchor = state.anchor();
    phase = state.phase();
    fromRate = state.fromRate();
    rate = state.rate();
    external = state.external();
    high = state.high();
    lastClock = state.lastClock();
    interval = state.interval();
    pulse = state.pulse();
    transition = state.transition();
    fromValue = state.fromValue();
  }

  private static double wrap(double value) {
    return value - Math.floor(value);
  }

  public double phase(long sample) {
    long at = external && interval > 0 ? Math.min(sample, timeout()) : sample;
    double elapsed = Math.max(0, at - anchor), ramp = Math.min(480, elapsed);
    return wrap(
        phase
            + fromRate * ramp
            + (rate - fromRate) * ramp * ramp / 960
            + rate * Math.max(0, elapsed - 480));
  }

  private double currentRate(long sample) {
    if (waiting(sample)) return 0;
    return fromRate + (rate - fromRate) * Math.clamp((sample - anchor) / 480.0, 0, 1);
  }

  private void anchor(long sample, double nextPhase, double startRate, double nextRate) {
    anchor = sample;
    phase = wrap(nextPhase);
    fromRate = startRate;
    rate = nextRate;
  }

  public void configure(LfoSettings next, long sample) {
    if (settings.equals(next)) return;
    double current = value(sample), p = phase(sample), r = currentRate(sample);
    boolean shape = settings.wave() != next.wave() || settings.unipolar() != next.unipolar();
    settings = next;
    double target =
        external
            ? (waiting(sample) ? 0 : 1.0 / (interval * settings.pulses()))
            : settings.frequency() / 48000;
    anchor(sample, p, r, target);
    pulse %= settings.pulses();
    if (shape) {
      transition = sample;
      fromValue = current;
    }
  }

  public double value(long sample) {
    double p = phase(sample);
    double value =
        switch (settings.wave()) {
          case 0 -> Math.sin(2 * Math.PI * p);
          case 1 -> 1 - 4 * Math.abs(p - .5);
          case 2 -> 2 * p - 1;
          case 3 -> 1 - 2 * p;
          case 4 -> p < .5 ? 1 : -1;
          default -> throw new IllegalStateException("lfo_wave");
        };
    if (settings.unipolar()) value = (value + 1) / 2;
    double mix = Math.clamp((sample - transition) / 480.0, 0, 1);
    return fromValue + (value - fromValue) * mix;
  }

  /** Prime wiring without treating an already-high source as an edge. */
  public void connect(boolean connected, boolean clock, long sample) {
    double p = phase(sample);
    external = connected;
    high = clock;
    lastClock = -1;
    interval = 0;
    pulse = 0;
    double target = connected ? 0 : settings.frequency() / 48000;
    anchor(sample, p, target, target);
  }

  public void input(boolean clock, long sample) {
    if (external
        && clock
        && !high
        && (lastClock < 0 || sample - lastClock >= Sequencer.MIN_CLOCK)) {
      boolean acquire = waiting(sample);
      if (lastClock < 0 || (interval > 0 && acquire)) {
        pulse = 0;
        interval = 0;
      } else {
        interval = Math.min(sample - lastClock, Long.MAX_VALUE / 256);
        pulse = (pulse + 1) % settings.pulses();
      }
      lastClock = sample;
      double nextRate = interval == 0 ? 0 : 1.0 / (interval * settings.pulses());
      anchor(sample, (double) pulse / settings.pulses(), nextRate, nextRate);
    }
    high = clock;
  }

  private long timeout() {
    return lastClock > Long.MAX_VALUE - 2 * interval ? Long.MAX_VALUE : lastClock + 2 * interval;
  }

  public boolean waiting(long sample) {
    return external && (interval == 0 || sample >= timeout());
  }

  public boolean external() {
    return external;
  }

  public Checkpoint checkpoint(long sample) {
    return new Checkpoint(
        sample,
        anchor,
        phase,
        fromRate,
        rate,
        external,
        high,
        lastClock,
        interval,
        pulse,
        transition,
        fromValue);
  }
}
