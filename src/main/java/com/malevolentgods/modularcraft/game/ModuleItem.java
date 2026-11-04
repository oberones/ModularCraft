package com.malevolentgods.modularcraft.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class ModuleItem extends BlockItem {
  public ModuleItem(Block block, Properties properties) {
    super(block, properties);
  }

  @Override
  public InteractionResult place(BlockPlaceContext context) {
    if (context.getPlayer() instanceof ServerPlayer player
        && !ServerRuntime.get(player.getServer()).canPlace(player)) return InteractionResult.FAIL;
    return super.place(context);
  }

  /**
   * Portable block-entity data must never choose the identity, ownership, or controls of a new
   * module.
   */
  @Override
  protected boolean updateCustomBlockEntityTag(
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.level.Level level,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.item.ItemStack stack,
      net.minecraft.world.level.block.state.BlockState state) {
    return false;
  }
}
