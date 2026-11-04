package com.malevolentgods.modularcraft.dsp;

/**
 * Analytic linear ADSR in 48 kHz sample coordinates. Owned by one thread. Stage durations are
 * captured on entry. Seeking advances at most three stages.
 */
public final class Envelope {
  public enum Stage {
    IDLE,
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
  }

  public record Checkpoint(
      Stage stage,
      long start,
      double from,
      double target,
      long duration,
      boolean gate,
      double attack,
      double decay,
      double sustain,
      double release) {
    public Checkpoint {
      if (stage == null
          || start < 0
          || duration < 1
          || duration > 480000
          || !unit(from)
          || !unit(target)
          || !time(attack)
          || !time(decay)
          || !unit(sustain)
          || !time(release)) throw new IllegalArgumentException("envelope_checkpoint");
    }

    private static boolean unit(double v) {
      return Double.isFinite(v) && v >= 0 && v <= 1;
    }

    private static boolean time(double v) {
      return Double.isFinite(v) && v >= .001 && v <= 10;
    }
  }

  private Stage stage = Stage.IDLE;
  private long start, duration = 1;
  private double from, target, attack = .01, decay = .2, sustain = .7, release = .3;
  private boolean gate;

  public Envelope() {}

  public Envelope(Checkpoint c) {
    stage = c.stage;
    start = c.start;
    from = c.from;
    target = c.target;
    duration = c.duration;
    gate = c.gate;
    attack = c.attack;
    decay = c.decay;
    sustain = c.sustain;
    release = c.release;
  }

  public void configure(double a, double d, double s, double r, long sample) {
    com.malevolentgods.modularcraft.domain.Parameter.ATTACK.validate(a);
    com.malevolentgods.modularcraft.domain.Parameter.DECAY.validate(d);
    com.malevolentgods.modularcraft.domain.Parameter.SUSTAIN.validate(s * 100);
    com.malevolentgods.modularcraft.domain.Parameter.RELEASE.validate(r);
    double current = value(sample);
    attack = a;
    decay = d;
    release = r;
    if (stage == Stage.SUSTAIN && sustain != s) enter(Stage.SUSTAIN, sample, current, s, .01);
    sustain = s;
  }

  public void gate(boolean value, long sample) {
    double current = value(sample);
    if (gate == value) return;
    gate = value;
    enter(
        value ? Stage.ATTACK : Stage.RELEASE,
        sample,
        current,
        value ? 1 : 0,
        value ? attack : release);
  }

  private void enter(Stage next, long sample, double begin, double end, double seconds) {
    stage = next;
    start = sample;
    from = begin;
    target = end;
    duration = Math.max(1, Math.round(seconds * 48000));
  }

  private void advance(long sample) {
    if (sample < 0) throw new IllegalArgumentException("negative_sample");
    for (int i = 0; i < 3 && sample - start >= duration; i++) {
      long end = start + duration;
      switch (stage) {
        case ATTACK -> enter(Stage.DECAY, end, 1, sustain, decay);
        case DECAY -> enter(Stage.SUSTAIN, end, target, sustain, .01);
        case RELEASE -> enter(Stage.IDLE, end, 0, 0, .001);
        default -> {
          return;
        }
      }
    }
  }

  public double value(long sample) {
    advance(sample);
    double position = Math.clamp((double) (sample - start) / duration, 0, 1);
    return from + (target - from) * position;
  }

  public Stage stage(long sample) {
    advance(sample);
    return stage;
  }

  public Checkpoint checkpoint(long sample) {
    advance(sample);
    return new Checkpoint(
        stage, start, from, target, duration, gate, attack, decay, sustain, release);
  }
}
