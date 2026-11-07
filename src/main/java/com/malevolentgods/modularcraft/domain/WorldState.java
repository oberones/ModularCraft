package com.malevolentgods.modularcraft.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Logical-server-owned transactional registry, including unloaded modules. No world access. */
public final class WorldState {
  /** Maximum modules in one connected patch, shared with snapshot validation and decoding. */
  public static final int MAX_PATCH_MODULES = 20;

  private final Map<UUID, ModuleState> modules = new LinkedHashMap<>();
  private final Set<Cable> cables = new LinkedHashSet<>();
  private long revision;

  public long revision() {
    return revision;
  }

  public List<ModuleState> modules() {
    return List.copyOf(modules.values());
  }

  public List<Cable> cables() {
    return List.copyOf(cables);
  }

  public ModuleState get(UUID id) {
    ModuleState m = modules.get(id);
    if (m == null) throw new IllegalArgumentException("missing_module");
    return m;
  }

  public boolean contains(UUID id) {
    return modules.containsKey(id);
  }

  public void place(ModuleState module) {
    if (modules.containsKey(module.id())) throw new IllegalArgumentException("duplicate_identity");
    for (ModuleState m : modules.values())
      if (m.dimension().equals(module.dimension())
          && m.x() == module.x()
          && m.y() == module.y()
          && m.z() == module.z()) throw new IllegalArgumentException("occupied_position");
    Map<UUID, ModuleState> next = new LinkedHashMap<>(modules);
    next.put(module.id(), module);
    validate(next, cables);
    commit(next, cables);
  }

  public void edit(
      UUID actor, boolean operator, UUID id, long expected, Parameter parameter, double value) {
    ModuleState old = authorize(actor, operator, id, expected);
    ModuleState next = old.withValue(parameter, value);
    modules.put(id, next);
    revision++;
  }

  /** Revision-protected transient transport command. */
  public void touch(UUID actor, boolean operator, UUID id, long expected) {
    authorize(actor, operator, id, expected);
    bump(id);
    revision++;
  }

  public ModuleState authorize(UUID actor, boolean operator, UUID id, long expected) {
    ModuleState m = get(id);
    if (!operator && !m.owner().equals(actor)) throw new IllegalArgumentException("permission");
    if (!m.loaded()) throw new IllegalArgumentException("unloaded");
    if (m.revision() != expected) throw new IllegalArgumentException("stale");
    return m;
  }

  public void connect(
      UUID actor, boolean operator, Cable cable, long sourceRevision, long destinationRevision) {
    authorize(actor, operator, cable.source(), sourceRevision);
    authorize(actor, operator, cable.destination(), destinationRevision);
    if (cables.contains(cable)) throw new IllegalArgumentException("duplicate_cable");
    Set<Cable> next = new LinkedHashSet<>(cables);
    next.add(cable);
    validate(modules, next);
    commit(modules, next);
    bump(cable.source());
    bump(cable.destination());
  }

  public void disconnect(
      UUID actor, boolean operator, Cable cable, long sourceRevision, long destinationRevision) {
    authorize(actor, operator, cable.source(), sourceRevision);
    authorize(actor, operator, cable.destination(), destinationRevision);
    if (!cables.contains(cable)) throw new IllegalArgumentException("missing_cable");
    Set<Cable> next = new LinkedHashSet<>(cables);
    next.remove(cable);
    validate(modules, next);
    commit(modules, next);
    bump(cable.source());
    bump(cable.destination());
  }

  /** Change playback intent and report every component affected by capacity admission. */
  public Set<UUID> enable(UUID actor, boolean operator, UUID id, long expected, boolean enabled) {
    ModuleState m = authorize(actor, operator, id, expected);
    if (m.kind() != ModuleKind.SPEAKER) throw new IllegalArgumentException("not_speaker");
    modules.put(
        id,
        m.withValue(Parameter.SPEAKER_ENABLED, enabled ? 1 : 0)
            .withValue(Parameter.SPEAKER_SUSPENDED, 0)
            .runtime(m.loaded(), enabled && m.enabled() && !m.suspended()));
    revision++;
    Set<UUID> changed = component(id);
    changed.addAll(refreshPlayback());
    return Set.copyOf(changed);
  }

  /** Admit loaded patches without displacing playback already admitted. Logical server only. */
  public Set<UUID> refreshPlayback() {
    Set<UUID> changed = new LinkedHashSet<>();
    List<Set<UUID>> parts = new ArrayList<>(components());
    parts.sort(
        java.util.Comparator.comparing(ids -> ids.stream().min(UUID::compareTo).orElseThrow()));
    for (Set<UUID> ids : parts) {
      boolean loaded = ids.stream().allMatch(id -> get(id).loaded());
      for (UUID id : ids) {
        ModuleState m = get(id);
        if (m.enabled() && (!loaded || !m.wantsPlayback() || m.suspended())) {
          modules.put(id, m.runtime(m.loaded(), false).bump());
          changed.addAll(ids);
        }
      }
    }
    int active = 0, speakers = 0;
    for (Set<UUID> ids : parts) {
      boolean on = false;
      for (UUID id : ids)
        if (get(id).enabled()) {
          on = true;
          speakers++;
        }
      if (on) active++;
    }
    // Extend active patches before admitting waiting patches, with UUID-stable ties.
    parts.sort(
        java.util.Comparator.comparing(
            (Set<UUID> ids) -> ids.stream().noneMatch(id -> get(id).enabled())));
    for (Set<UUID> ids : parts) {
      if (ids.stream().anyMatch(id -> !get(id).loaded())) continue;
      boolean on = ids.stream().anyMatch(id -> get(id).enabled());
      for (UUID id : ids.stream().sorted().toList()) {
        ModuleState m = get(id);
        if (!m.wantsPlayback()
            || m.suspended()
            || m.enabled()
            || speakers >= 8
            || (!on && active >= 4)) continue;
        modules.put(id, m.runtime(true, true).bump());
        speakers++;
        if (!on) {
          active++;
          on = true;
        }
        changed.addAll(ids);
      }
    }
    if (!changed.isEmpty()) revision++;
    return Set.copyOf(changed);
  }

  public void remove(UUID id) {
    if (!modules.containsKey(id)) return;
    Set<UUID> affected = component(id, cables);
    modules.remove(id);
    cables.removeIf(c -> c.source().equals(id) || c.destination().equals(id));
    try {
      validate(modules, cables);
    } catch (IllegalArgumentException capacity) {
      suspend(affected);
    }
    revision++;
  }

  public void reconcile(ModuleState value) {
    Map<UUID, ModuleState> next = new LinkedHashMap<>(modules);
    next.put(value.id(), value.runtime(false, false));
    validate(next, cables);
    commit(next, cables);
  }

  public void loaded(UUID id, boolean value) {
    ModuleState m = get(id);
    if (m.loaded() == value) return;
    if (!value) deactivate(component(id, cables), false);
    modules.put(id, get(id).runtime(value, false));
    revision++;
  }

  public void suspend(Set<UUID> ids) {
    deactivate(ids, true);
  }

  private void deactivate(Set<UUID> ids, boolean latch) {
    for (UUID id : ids)
      if (modules.containsKey(id)) {
        ModuleState m = get(id);
        if (m.kind() != ModuleKind.SPEAKER) continue;
        if (latch && !m.suspended()) m = m.withValue(Parameter.SPEAKER_SUSPENDED, 1);
        if (m.enabled()) m = m.runtime(m.loaded(), false).bump();
        modules.put(id, m);
      }
  }

  public Set<UUID> component(UUID id) {
    return component(id, cables);
  }

  public List<Set<UUID>> components() {
    List<Set<UUID>> result = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    for (UUID id : modules.keySet())
      if (!seen.contains(id)) {
        Set<UUID> part = component(id, cables);
        seen.addAll(part);
        result.add(part);
      }
    return List.copyOf(result);
  }

  private static Set<UUID> component(UUID id, Collection<Cable> links) {
    Set<UUID> seen = new LinkedHashSet<>();
    ArrayDeque<UUID> pending = new ArrayDeque<>();
    pending.add(id);
    while (!pending.isEmpty()) {
      UUID node = pending.remove();
      if (!seen.add(node)) continue;
      for (Cable c : links) {
        if (c.source().equals(node) && !seen.contains(c.destination()))
          pending.add(c.destination());
        if (c.destination().equals(node) && !seen.contains(c.source())) pending.add(c.source());
      }
    }
    return seen;
  }

  public static void validate(Map<UUID, ModuleState> states, Collection<Cable> links) {
    Graph.compile(states.values(), links);
    Map<UUID, Integer> owned = new HashMap<>(), ownedCables = new HashMap<>();
    for (ModuleState m : states.values())
      if (owned.merge(m.owner(), 1, Integer::sum) > 64)
        throw new IllegalArgumentException("player_modules:64");
    for (Cable c : links)
      if (ownedCables.merge(states.get(c.source()).owner(), 1, Integer::sum) > 128)
        throw new IllegalArgumentException("player_cables:128");
    Set<UUID> seen = new HashSet<>();
    int active = 0, enabled = 0;
    for (ModuleState m : states.values())
      if (!seen.contains(m.id())) {
        Set<UUID> ids = component(m.id(), links);
        seen.addAll(ids);
        int count = 0, speakers = 0;
        boolean on = false, loaded = true;
        for (UUID id : ids) {
          ModuleState member = states.get(id);
          if (member.kind() == ModuleKind.SPEAKER) speakers++;
          if (member.enabled()) {
            enabled++;
            on = true;
          }
          loaded &= member.loaded();
        }
        for (Cable c : links) if (ids.contains(c.source())) count++;
        if (ids.size() > MAX_PATCH_MODULES)
          throw new IllegalArgumentException("patch_modules:" + MAX_PATCH_MODULES);
        if (count > 24) throw new IllegalArgumentException("patch_cables:24");
        if (speakers > 2) throw new IllegalArgumentException("patch_speakers:2");
        if (on && loaded) active++;
      }
    if (active > 4) throw new IllegalArgumentException("active_patches:4");
    if (enabled > 8) throw new IllegalArgumentException("active_speakers:8");
  }

  private void bump(UUID id) {
    modules.put(id, get(id).bump());
  }

  private void commit(Map<UUID, ModuleState> next, Collection<Cable> links) {
    Map<UUID, ModuleState> copy = new LinkedHashMap<>(next);
    Set<Cable> copyLinks = new LinkedHashSet<>(links);
    modules.clear();
    modules.putAll(copy);
    cables.clear();
    cables.addAll(copyLinks);
    revision++;
  }

  /** Fully validated restore; all activation is deliberately transient. */
  public void restore(List<ModuleState> states, List<Cable> links) {
    Map<UUID, ModuleState> next = new LinkedHashMap<>();
    for (ModuleState m : states)
      if (next.put(m.id(), m.runtime(false, false)) != null)
        throw new IllegalArgumentException("duplicate_identity");
    validate(next, links);
    commit(next, links);
  }
}
