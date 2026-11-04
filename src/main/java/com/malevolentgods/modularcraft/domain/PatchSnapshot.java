package com.malevolentgods.modularcraft.domain;

import com.malevolentgods.modularcraft.dsp.Envelope;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PatchSnapshot(
    UUID id,
    long revision,
    long sequence,
    long sample,
    List<ModuleState> modules,
    List<Cable> cables,
    Map<UUID, Envelope.Checkpoint> envelopes) {
  public PatchSnapshot {
    modules = List.copyOf(modules);
    cables = List.copyOf(cables);
    envelopes = Map.copyOf(envelopes);
    if (id == null
        || revision < 0
        || sequence < 0
        || sample < 0
        || modules.size() > 16
        || cables.size() > 24
        || envelopes.size() > 16) throw new IllegalArgumentException("snapshot_bounds");
    java.util.Map<UUID, ModuleState> map = new java.util.HashMap<>();
    for (ModuleState m : modules)
      if (map.put(m.id(), m) != null) throw new IllegalArgumentException("duplicate_identity");
    WorldState.validate(map, cables);
    for (UUID key : envelopes.keySet())
      if (!map.containsKey(key) || map.get(key).kind() != ModuleKind.ENVELOPE)
        throw new IllegalArgumentException("envelope_identity");
  }
}
