package com.malevolentgods.modularcraft.transport;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded network-to-game handoff. Locks are confined to network/game threads, never audio. A
 * release removes older pending gate presses before entering the priority cleanup lane.
 */
public final class EditInbox<T> {
  private record Entry<T>(UUID actor, String key, boolean gate, T value) {}

  private final ArrayDeque<Entry<T>> ordinary = new ArrayDeque<>();
  private final Map<UUID, T> cleanup = new LinkedHashMap<>();
  private final Map<UUID, Integer> counts = new HashMap<>();

  public synchronized boolean offer(
      UUID actor, String key, boolean gatePress, boolean release, T value) {
    if (release) {
      if (cleanup.size() >= 256 && !cleanup.containsKey(actor)) return false;
      var iterator = ordinary.iterator();
      while (iterator.hasNext()) {
        Entry<T> entry = iterator.next();
        if (entry.actor.equals(actor) && entry.gate) {
          iterator.remove();
          decrement(actor);
        }
      }
      cleanup.put(actor, value);
      return true;
    }
    if (!key.isEmpty()) {
      var iterator = ordinary.iterator();
      while (iterator.hasNext()) {
        Entry<T> entry = iterator.next();
        if (entry.actor.equals(actor) && entry.key.equals(key)) {
          iterator.remove();
          decrement(actor);
          break;
        }
      }
    }
    if (ordinary.size() >= 256 || counts.getOrDefault(actor, 0) >= 64) return false;
    ordinary.add(new Entry<>(actor, key, gatePress, value));
    counts.merge(actor, 1, Integer::sum);
    return true;
  }

  public synchronized T poll() {
    if (!cleanup.isEmpty()) {
      var iterator = cleanup.entrySet().iterator();
      T next = iterator.next().getValue();
      iterator.remove();
      return next;
    }
    Entry<T> next = ordinary.poll();
    if (next == null) return null;
    decrement(next.actor);
    return next.value;
  }

  public synchronized int size() {
    return ordinary.size() + cleanup.size();
  }

  private void decrement(UUID actor) {
    counts.computeIfPresent(actor, (id, count) -> count <= 1 ? null : count - 1);
  }
}
