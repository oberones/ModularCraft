package com.malevolentgods.modularcraft.game;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Port;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class ModuleBlock extends BaseEntityBlock {
  public final ModuleKind kind;

  public ModuleBlock(ModuleKind kind, Properties properties) {
    super(properties);
    this.kind = kind;
    registerDefaultState(
        stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(p -> new ModuleBlock(kind, p));
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(BlockStateProperties.HORIZONTAL_FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState()
        .setValue(
            BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ModuleEntity(pos, state);
  }

  @Override
  public void setPlacedBy(
      Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
    if (!level.isClientSide
        && entity instanceof Player player
        && level.getBlockEntity(pos) instanceof ModuleEntity module)
      module.initialize(player.getUUID());
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide
        && player instanceof ServerPlayer server
        && level.getBlockEntity(pos) instanceof ModuleEntity module)
      ServerRuntime.get(server.getServer()).inspect(server, module, true);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (stack.is(ModularCraft.TOOL.get())) {
      if (!level.isClientSide
          && player instanceof ServerPlayer server
          && level.getBlockEntity(pos) instanceof ModuleEntity module) {
        Port port = portAt(state, hit);
        ServerRuntime.get(server.getServer()).patch(server, module, port, player.isShiftKeyDown());
      }
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  public Port portAt(BlockState state, BlockHitResult hit) {
    if (hit.getDirection() != state.getValue(BlockStateProperties.HORIZONTAL_FACING)) return null;
    double y = hit.getLocation().y - hit.getBlockPos().getY();
    if (y > .34 || y < .06) return null;
    Direction face = hit.getDirection();
    double local =
        face.getAxis() == Direction.Axis.Z
            ? hit.getLocation().x - hit.getBlockPos().getX()
            : hit.getLocation().z - hit.getBlockPos().getZ();
    if (face == Direction.NORTH || face == Direction.EAST) local = 1 - local;
    int index = Math.min(kind.ports.size() - 1, (int) (local * kind.ports.size()));
    return kind.ports.get(index);
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
    if (state.getBlock() != next.getBlock()
        && !level.isClientSide
        && level.getBlockEntity(pos) instanceof ModuleEntity module
        && module.valid()) ServerRuntime.get(level.getServer()).remove(module.state().id());
    super.onRemove(state, level, pos, next, moving);
  }
}
