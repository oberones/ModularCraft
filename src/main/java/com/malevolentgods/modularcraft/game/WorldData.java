package com.malevolentgods.modularcraft.game;

import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Global quota/cable index in the overworld, spanning all dimensions. */
public final class WorldData extends SavedData {
  public static final Factory<WorldData> FACTORY = new Factory<>(WorldData::new, WorldData::load);
  private WorldState state = new WorldState();
  private List<byte[]> quarantine = List.of();
  private CompoundTag refused;

  public WorldState state() {
    return state;
  }

  public boolean writable() {
    return refused == null;
  }

  public boolean blocked(java.util.UUID id) {
    for (byte[] raw : quarantine)
      if (raw.length >= 17 && raw[0] == 1) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(raw, 1, 16);
        if (new java.util.UUID(buffer.getLong(), buffer.getLong()).equals(id)) return true;
      }
    return false;
  }

  public int quarantined() {
    return quarantine.size();
  }

  private static WorldData load(CompoundTag tag, HolderLookup.Provider registries) {
    WorldData data = new WorldData();
    try {
      if ((tag.getInt("Schema") != 1 && tag.getInt("Schema") != 2) || !tag.contains("Registry", 7))
        throw new IllegalArgumentException("schema");
      var stored = StateCodec.load(tag.getByteArray("Registry"));
      data.state = stored.state();
      data.quarantine = stored.quarantined();
    } catch (IllegalArgumentException invalid) {
      data.refused = tag.copy();
    }
    return data;
  }

  public void reserveRecord(int maximumBytes) {
    if (!writable()
        || state.modules().size() + state.cables().size() + quarantine.size() >= 1024
        || StateCodec.save(state, quarantine).length + maximumBytes + 2 > StateCodec.MAX_WORLD)
      throw new IllegalArgumentException("encoded_size");
  }

  public void validateSize() {
    StateCodec.save(state, quarantine);
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    if (refused != null) return refused.copy();
    tag.putInt("Schema", 2);
    tag.putByteArray("Registry", StateCodec.save(state, quarantine));
    return tag;
  }
}
