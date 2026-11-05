package com.malevolentgods.modularcraft.dsp;

/**
 * One audio writer and one UI reader. Preallocated publication uses a sequence stamp; the reader
 * skips a contended frame instead of blocking. Peaks decay linearly to zero in 300 ms at 48 kHz.
 */
public final class MixerMeter {
  public record Reading(double[] peaks, boolean[] overloads) {}

  private final double[] peaks = new double[5], decay = new double[5], published = new double[5];
  private final int[] hold = new int[5];
  private final boolean[] overloaded = new boolean[5];
  private volatile long stamp;
  private long publishedAt;
  private boolean available;

  public void adopt(MixerMeter old) {
    System.arraycopy(old.peaks, 0, peaks, 0, 5);
    System.arraycopy(old.decay, 0, decay, 0, 5);
    System.arraycopy(old.hold, 0, hold, 0, 5);
  }

  public void sample(double[] levels) {
    for (int i = 0; i < 5; i++) {
      double peak = Math.abs(levels[i]);
      if (peak >= peaks[i]) {
        peaks[i] = peak;
        decay[i] = peak / 14400;
      } else peaks[i] = Math.max(peak, peaks[i] - decay[i]);
      if (peak > 1) hold[i] = 48000;
      else if (hold[i] > 0) hold[i]--;
    }
  }

  /** Called at the end of a rendered chunk; no allocation or synchronization locks. */
  public void publish(long now) {
    stamp++;
    for (int i = 0; i < 5; i++) {
      published[i] = peaks[i];
      overloaded[i] = hold[i] > 0;
    }
    publishedAt = now;
    available = true;
    stamp++;
  }

  /** UI only. A missing/stale/contended publication is unavailable, not a measured zero. */
  public Reading read(long now) {
    long before = stamp;
    if ((before & 1) != 0 || !available || now - publishedAt > 500_000_000L) return null;
    double[] values = published.clone();
    boolean[] clips = overloaded.clone();
    java.lang.invoke.VarHandle.acquireFence();
    return before == stamp ? new Reading(values, clips) : null;
  }
}
