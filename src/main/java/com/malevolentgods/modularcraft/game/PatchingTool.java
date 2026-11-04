package com.malevolentgods.modularcraft.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PatchingTool extends Item {
  public PatchingTool(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server) ServerRuntime.get(server.getServer()).cancel(server);
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
