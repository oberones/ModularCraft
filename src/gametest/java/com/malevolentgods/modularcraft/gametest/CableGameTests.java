package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModularCraft.ID)
@PrefixGameTestTemplate(false)
public final class CableGameTests {
  private CableGameTests() {}

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void sneakRemovalThroughGameMode(GameTestHelper h) {
    removal(h, false);
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void sneakRemovalWithOccupiedOffhand(GameTestHelper h) {
    removal(h, true);
  }

  private static void removal(GameTestHelper h, boolean offhand) {
    var fixture = new Fixture(h);
    fixture.connect(fixture.first);
    fixture.connect(fixture.second);
    if (offhand) fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
    fixture.player.setShiftKeyDown(true);
    fixture.click(fixture.first, Port.AUDIO_IN);
    h.assertTrue(!fixture.connected(fixture.first), "Sneak-click removes destination cable");
    h.assertTrue(fixture.connected(fixture.second), "Other output branch survives");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void invalidAndForeignRemovalPreservesCable(GameTestHelper h) {
    var fixture = new Fixture(h);
    fixture.connect(fixture.first);
    fixture.player.setShiftKeyDown(true);
    fixture.click(fixture.source, Port.AUDIO_OUT);
    fixture.click(fixture.second, Port.AUDIO_IN);
    h.assertTrue(fixture.connected(fixture.first), "Output and empty input do not delete cables");
    var guest = player(h, UUID.randomUUID());
    guest.setShiftKeyDown(true);
    h.assertTrue(
        dispatch(guest, hit(fixture.first, Port.AUDIO_IN)).consumesAction(),
        "Foreign removal is handled without falling through to air use");
    h.assertTrue(fixture.connected(fixture.first), "Foreign player cannot remove owner's cable");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void airAndNonPortClicksCancelSelection(GameTestHelper h) {
    var fixture = new Fixture(h);
    fixture.click(fixture.source, Port.AUDIO_OUT);
    fixture.player.gameMode.useItem(
        fixture.player, h.getLevel(), fixture.player.getMainHandItem(), InteractionHand.MAIN_HAND);
    fixture.click(fixture.first, Port.AUDIO_IN);
    h.assertTrue(!fixture.connected(fixture.first), "Air use cancels the selected output");
    fixture.click(fixture.source, Port.AUDIO_OUT);
    fixture.player.setShiftKeyDown(true);
    BlockPos pos = fixture.source.getBlockPos();
    h.assertTrue(
        dispatch(
                fixture.player,
                new BlockHitResult(
                    new Vec3(pos.getX() + .5, pos.getY() + .8, pos.getZ()),
                    Direction.NORTH,
                    pos,
                    false))
            .consumesAction(),
        "Sneak-click on non-port module area is consumed");
    fixture.player.setShiftKeyDown(false);
    fixture.click(fixture.first, Port.AUDIO_IN);
    h.assertTrue(!fixture.connected(fixture.first), "Non-port use cancels the selected output");
    h.succeed();
  }

  private static ServerPlayer player(GameTestHelper h, UUID owner) {
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "CableTester"));
    player.setGameMode(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModularCraft.TOOL.get()));
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    return player;
  }

  private static BlockHitResult hit(ModuleEntity module, Port port) {
    BlockPos pos = module.getBlockPos();
    var ports = module.state().kind().ports;
    double x = 1 - (ports.indexOf(port) + .5) / ports.size();
    return new BlockHitResult(
        new Vec3(pos.getX() + x, pos.getY() + .2, pos.getZ()), Direction.NORTH, pos, false);
  }

  private static InteractionResult dispatch(ServerPlayer player, BlockHitResult hit) {
    player.setPos(hit.getBlockPos().getCenter().add(0, 0, -2));
    return player.gameMode.useItemOn(
        player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
  }

  private static final class Fixture {
    private final GameTestHelper helper;
    private final ServerPlayer player;
    private final ModuleEntity source, first, second;
    private final ServerRuntime runtime;

    Fixture(GameTestHelper helper) {
      this.helper = helper;
      player = player(helper, UUID.randomUUID());
      source = module(1, ModuleKind.OSCILLATOR);
      first = module(2, ModuleKind.SPEAKER);
      second = module(3, ModuleKind.SPEAKER);
      runtime = ServerRuntime.get(helper.getLevel().getServer());
    }

    private ModuleEntity module(int x, ModuleKind kind) {
      BlockPos relative = new BlockPos(x, 1, 1);
      helper.setBlock(relative, ModularCraft.MODULES.get(kind).get());
      var module = (ModuleEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
      module.initialize(player.getUUID());
      return module;
    }

    void click(ModuleEntity module, Port port) {
      helper.assertTrue(
          dispatch(player, hit(module, port)).consumesAction(),
          "Module port click must be consumed, including while sneaking");
    }

    void connect(ModuleEntity destination) {
      click(source, Port.AUDIO_OUT);
      click(destination, Port.AUDIO_IN);
      helper.assertTrue(connected(destination), "Ordinary clicks connect ports");
    }

    boolean connected(ModuleEntity destination) {
      return runtime
          .state()
          .cables()
          .contains(
              new Cable(
                  source.state().id(), Port.AUDIO_OUT, destination.state().id(), Port.AUDIO_IN));
    }
  }
}
