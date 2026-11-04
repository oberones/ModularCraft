package com.malevolentgods.modularcraft.domain;

import com.malevolentgods.modularcraft.transport.Transport;
import java.util.UUID;

/** One open panel; server-owned sequence watermark and sample scheduling anchor. */
public final class KeyboardSession {
  private final UUID id = UUID.randomUUID();
  private final UUID module;
  private long sequence = -1, sample = -1, received;

  public KeyboardSession(UUID module) {
    this.module = module;
  }

  public UUID id() {
    return id;
  }

  public UUID module() {
    return module;
  }

  public boolean accept(long next) {
    if (next < 0 || next <= sequence) return false;
    sequence = next;
    return true;
  }

  /** Preserve receive spacing when several inputs are drained in a single world tick. */
  public long schedule(long earliest, long nanos) {
    long spacing = Math.max(48, Transport.samples(Math.max(0, nanos - received)));
    sample = sample < 0 ? earliest : Math.max(earliest, sample + spacing);
    received = nanos;
    return sample;
  }
}
