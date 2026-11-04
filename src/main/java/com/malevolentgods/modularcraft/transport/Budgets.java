package com.malevolentgods.modularcraft.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Logical-server-owned fixed one-second admission windows. Cleanup never consumes edit tokens. */
public final class Budgets {
  private static final class Window {
    long start;
    int edits, bytes;

    void reset(long now) {
      if (now - start >= 1_000_000_000L || now < start) {
        start = now;
        edits = 0;
        bytes = 0;
      }
    }
  }

  private final Map<UUID, Window> players = new HashMap<>();
  private final Window world = new Window();

  public boolean edit(UUID player, long nanos, boolean cleanup) {
    if (cleanup) return true;
    Window window = players.computeIfAbsent(player, ignored -> new Window());
    window.reset(nanos);
    world.reset(nanos);
    if (window.edits >= 20 || world.edits >= 200) return false;
    window.edits++;
    world.edits++;
    return true;
  }

  public boolean bytes(UUID player, long nanos, int count) {
    Window window = players.computeIfAbsent(player, ignored -> new Window());
    window.reset(nanos);
    world.reset(nanos);
    if (count < 0 || count > 131072 - window.bytes || count > 2097152 - world.bytes) return false;
    window.bytes += count;
    world.bytes += count;
    return true;
  }

  /** Admission for a small empty roster that forces bounded client recovery. */
  public boolean recovery(UUID player, long nanos) {
    return bytes(player, nanos, 160);
  }

  public void forget(UUID player) {
    players.remove(player);
  }
}
