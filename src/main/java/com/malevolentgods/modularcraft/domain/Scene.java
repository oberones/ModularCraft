package com.malevolentgods.modularcraft.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/** Validates a complete listener roster before replacing its accepted scene. */
public final class Scene {
  private Scene() {}

  public static void validate(Collection<PatchSnapshot> patches, String dimension) {
    if (patches.size() > 256) throw new IllegalArgumentException("world_capacity");
    var identities = new HashSet<UUID>();
    var modules = new HashMap<UUID, ModuleState>();
    var cables = new ArrayList<Cable>();
    for (PatchSnapshot patch : patches) {
      if (!identities.add(patch.id())) throw new IllegalArgumentException("duplicate_patch");
      for (ModuleState module : patch.modules()) {
        if (!module.dimension().equals(dimension)) throw new IllegalArgumentException("dimension");
        if (modules.put(module.id(), module) != null)
          throw new IllegalArgumentException("duplicate_identity");
        if (modules.size() > 256) throw new IllegalArgumentException("world_capacity");
      }
      if (cables.size() + patch.cables().size() > 512)
        throw new IllegalArgumentException("world_capacity");
      cables.addAll(patch.cables());
    }
    WorldState.validate(modules, cables);
  }
}
