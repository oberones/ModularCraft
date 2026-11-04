package com.malevolentgods.modularcraft.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/** Bounded deterministic Kahn ordering; no recursion and no implicit conversion or mixing. */
public final class Graph {
  private Graph() {}

  public static List<ModuleState> compile(
      Collection<ModuleState> modules, Collection<Cable> cables) {
    if (modules.size() > 256 || cables.size() > 512)
      throw new IllegalArgumentException("world_capacity");
    Map<UUID, ModuleState> byId = new HashMap<>();
    Map<UUID, Integer> degree = new HashMap<>();
    Map<UUID, List<UUID>> destinations = new HashMap<>();
    Set<String> inputs = new HashSet<>();
    Map<String, Integer> fanout = new HashMap<>();
    for (ModuleState m : modules) {
      if (byId.put(m.id(), m) != null) throw new IllegalArgumentException("duplicate_identity");
      degree.put(m.id(), 0);
      destinations.put(m.id(), new ArrayList<>());
    }
    for (Cable c : cables) {
      ModuleState a = byId.get(c.source()), b = byId.get(c.destination());
      if (a == null || b == null) throw new IllegalArgumentException("missing_endpoint");
      if (a.id().equals(b.id())) throw new IllegalArgumentException("self_connection");
      if (!a.kind().ports.contains(c.output())
          || !b.kind().ports.contains(c.input())
          || !c.output().connects(c.input()))
        throw new IllegalArgumentException("incompatible_ports");
      if (!a.owner().equals(b.owner())) throw new IllegalArgumentException("different_owners");
      if (!a.dimension().equals(b.dimension())) throw new IllegalArgumentException("dimension");
      double dx = (double) a.x() - b.x(), dy = (double) a.y() - b.y(), dz = (double) a.z() - b.z();
      if (dx * dx + dy * dy + dz * dz > 256)
        throw new IllegalArgumentException("cable_distance:16");
      if (!inputs.add(c.destination() + ":" + c.input()))
        throw new IllegalArgumentException("occupied_input");
      if (fanout.merge(c.source() + ":" + c.output(), 1, Integer::sum) > 4)
        throw new IllegalArgumentException("fanout:4");
      destinations.get(a.id()).add(b.id());
      degree.merge(b.id(), 1, Integer::sum);
    }
    PriorityQueue<UUID> ready = new PriorityQueue<>(Comparator.naturalOrder());
    degree.forEach(
        (id, count) -> {
          if (count == 0) ready.add(id);
        });
    List<ModuleState> order = new ArrayList<>();
    while (!ready.isEmpty()) {
      UUID id = ready.remove();
      order.add(byId.get(id));
      for (UUID dest : destinations.get(id))
        if (degree.merge(dest, -1, Integer::sum) == 0) ready.add(dest);
    }
    if (order.size() != modules.size()) throw new IllegalArgumentException("cycle");
    return List.copyOf(order);
  }
}
