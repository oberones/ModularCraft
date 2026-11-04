package com.malevolentgods.modularcraft.transport;

import java.util.function.LongSupplier;

/** Server monotonic transport; a true integrated-server pause freezes this clock. */
public final class Transport {
  private final LongSupplier nanos;
  private final long origin;
  private long excluded, pauseStart;
  private boolean paused;

  public Transport(LongSupplier nanos) {
    this.nanos = nanos;
    origin = nanos.getAsLong();
  }

  public long sample() {
    return samples((paused ? pauseStart : nanos.getAsLong()) - origin - excluded);
  }

  public void paused(boolean value) {
    if (paused == value) return;
    long now = nanos.getAsLong();
    if (value) pauseStart = now;
    else excluded += now - pauseStart;
    paused = value;
  }

  public static long samples(long nanos) {
    return Math.addExact(
        Math.multiplyExact(nanos / 1_000_000_000L, 48000),
        nanos % 1_000_000_000L * 48000 / 1_000_000_000L);
  }
}
