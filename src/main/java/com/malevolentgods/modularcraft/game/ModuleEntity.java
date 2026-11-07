package com.malevolentgods.modularcraft.game;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.StateCodec;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ModuleEntity extends BlockEntity {
  private ModuleState module;
  private CompoundTag refused;

  public ModuleEntity(BlockPos pos, BlockState state) {
    super(ModularCraft.ENTITY.get(), pos, state);
  }

  public boolean valid() {
    return module != null && refused == null;
  }

  public ModuleState state() {
    if (!valid()) throw new IllegalArgumentException("quarantined");
    return module;
  }

  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public void initialize(UUID owner) {
    if (module != null || refused != null) return;
    var kind = ((ModuleBlock) getBlockState().getBlock()).kind;
    String dimension =
        level == null ? "minecraft:overworld" : level.dimension().location().toString();
    module =
        new ModuleState(
            UUID.randomUUID(),
            owner,
            kind,
            dimension,
            worldPosition.getX(),
            worldPosition.getY(),
            worldPosition.getZ(),
            0,
            kind.defaults(),
            true,
            false);
    if (level instanceof ServerLevel server) ServerRuntime.get(server.getServer()).attach(this);
    setChanged();
  }

  public void quarantine() {
    refused = new CompoundTag();
    refused.putInt("Schema", 2);
    if (module != null) refused.putByteArray("Module", StateCodec.moduleBytes(module));
    module = null;
    setChanged();
  }

  public void accepted(ModuleState value) {
    module = value;
    setChanged();
  }

  public boolean eligible(Player player) {
    return valid()
        && level != null
        && player.level() == level
        && level.hasChunkAt(worldPosition)
        && player.distanceToSqr(worldPosition.getCenter()) <= 36
        && level.mayInteract(player, worldPosition)
        && !player.isSpectator()
        && player.isAlive();
  }

  public boolean editable(Player player) {
    return eligible(player)
        && (player.getUUID().equals(module.owner()) || player.hasPermissions(2));
  }

  @Override
  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public void onLoad() {
    super.onLoad();
    if (valid() && level instanceof ServerLevel server)
      ServerRuntime.get(server.getServer()).attach(this);
  }

  @Override
  // Borrowed Minecraft singleton/world; its lifecycle belongs to the host.
  @SuppressWarnings("PMD.CloseResource")
  public void setRemoved() {
    if (valid() && level instanceof ServerLevel server)
      ServerRuntime.get(server.getServer()).unload(module.id());
    super.setRemoved();
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (refused != null) {
      tag.merge(refused.copy());
      return;
    }
    if (module != null) {
      tag.putInt("Schema", 2);
      tag.putByteArray("Module", StateCodec.moduleBytes(module));
    }
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    try {
      if ((tag.getInt("Schema") != 1 && tag.getInt("Schema") != 2) || !tag.contains("Module", 7))
        throw new IllegalArgumentException("schema");
      ModuleState loaded = StateCodec.moduleBytes(tag.getByteArray("Module"), tag.getInt("Schema"));
      if (loaded.kind() != ((ModuleBlock) getBlockState().getBlock()).kind
          || loaded.x() != worldPosition.getX()
          || loaded.y() != worldPosition.getY()
          || loaded.z() != worldPosition.getZ())
        throw new IllegalArgumentException("identity_position");
      module = loaded.runtime(false, false);
    } catch (IllegalArgumentException invalid) {
      refused = tag.copy();
      module = null;
    }
  }
}
