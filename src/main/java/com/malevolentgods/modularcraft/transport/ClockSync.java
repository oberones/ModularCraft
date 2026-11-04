package com.malevolentgods.modularcraft.transport;

/**
 * Immutable anchors cross from client game thread to audio thread. Lowest-jitter midpoint updates.
 */
public final class ClockSync {
  private record Anchor(long nanos, long sample) {}

  private volatile Anchor anchor;
  private long bestRtt = Long.MAX_VALUE;

  public void observe(long sent, long received, long serverSample) {
    long rtt = received - sent;
    if (rtt < 0 || rtt > 1_000_000_000L || serverSample < 0) return;
    if (anchor == null || rtt <= bestRtt + 10_000_000L) {
      anchor = new Anchor(sent + rtt / 2, serverSample);
      bestRtt = Math.min(bestRtt, rtt);
    }
  }

  public long sample(long nanos) {
    Anchor current = anchor;
    return current == null
        ? 0
        : Math.max(0, current.sample + Transport.samples(nanos - current.nanos));
  }

  public boolean ready() {
    return anchor != null;
  }
}
