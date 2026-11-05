package com.malevolentgods.modularcraft.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class PatchingTool extends Item {
  public PatchingTool(Properties properties) {
    super(properties);
  }

  // Minecraft skips block use while sneaking; retain the same module handler in that path.
  @Override
  public InteractionResult useOn(UseOnContext context) {
    var player = context.getPlayer();
    var state = context.getLevel().getBlockState(context.getClickedPos());
    if (player == null || !(state.getBlock() instanceof ModuleBlock)) return InteractionResult.PASS;
    return state
        .useItemOn(
            context.getItemInHand(),
            context.getLevel(),
            player,
            context.getHand(),
            new BlockHitResult(
                context.getClickLocation(),
                context.getClickedFace(),
                context.getClickedPos(),
                context.isInside()))
        .result();
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server) ServerRuntime.get(server.getServer()).cancel(server);
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
