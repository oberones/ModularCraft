package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.client.ClientRuntime;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Port;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native client dispatch and server acknowledgement in the disposable RP-001 fixture. */
final class NativeCableProbe {
  private static Set<Cable> original;
  private static Cable removed;

  private NativeCableProbe() {}

  // Minecraft owns the borrowed client and framebuffer.
  @SuppressWarnings("PMD.CloseResource")
  static void tick(int ticks) {
    Minecraft mc = Minecraft.getInstance();
    if (ticks == 100) {
      if (!mc.player.getMainHandItem().is(ModularCraft.TOOL.get())
          || !mc.player.getOffhandItem().is(Items.STICK))
        throw new IllegalStateException("Cable fixture must equip tool and occupied offhand");
      original = cables();
      if (original.size() != 4) throw new IllegalStateException("Expected four RP-001 cables");
      removed =
          original.stream()
              .filter(c -> c.destination().equals(module(ModuleKind.FILTER).id()))
              .findFirst()
              .orElseThrow();
    }
    if (ticks == 120 || ticks == 220) click(ModuleKind.FILTER, Port.AUDIO_IN, true);
    if (ticks == 160) click(ModuleKind.OSCILLATOR, Port.AUDIO_OUT, false);
    if (ticks == 180) click(ModuleKind.FILTER, Port.AUDIO_IN, false);
    if (ticks == 140 || ticks == 200 || ticks == 240) {
      Set<Cable> expected =
          original.stream()
              .filter(c -> ticks == 200 || !c.equals(removed))
              .collect(Collectors.toSet());
      if (!cables().equals(expected))
        throw new IllegalStateException("Cable server acknowledgement mismatch at tick " + ticks);
      System.out.println(
          "MODULARCRAFT_QA cableRemoval tick=" + ticks + " cables=" + expected.size() + " PASS");
      Screenshot.grab(
          mc.gameDirectory,
          "cable-removal-" + ticks + ".png",
          mc.getMainRenderTarget(),
          message -> System.out.println("MODULARCRAFT_QA " + message.getString()));
    }
  }

  private static Set<Cable> cables() {
    return ClientRuntime.patches().stream()
        .flatMap(patch -> patch.cables().stream())
        .collect(Collectors.toSet());
  }

  private static ModuleState module(ModuleKind kind) {
    return ClientRuntime.patches().stream()
        .flatMap(patch -> patch.modules().stream())
        .filter(module -> module.kind() == kind)
        .findFirst()
        .orElseThrow();
  }

  // Minecraft owns the borrowed client.
  @SuppressWarnings("PMD.CloseResource")
  private static void click(ModuleKind kind, Port port, boolean sneak) {
    Minecraft mc = Minecraft.getInstance();
    ModuleState module = module(kind);
    BlockPos pos = new BlockPos(module.x(), module.y(), module.z());
    double x = 1 - (kind.ports.indexOf(port) + .5) / kind.ports.size();
    Vec3 point = new Vec3(pos.getX() + x, pos.getY() + .2, pos.getZ());
    mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
    mc.player.input.shiftKeyDown = sneak;
    if (sneak)
      mc.player.connection.send(
          new ServerboundPlayerCommandPacket(
              mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
    try {
      var result =
          mc.gameMode.useItemOn(
              mc.player,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(point, Direction.NORTH, pos, false));
      if (!result.consumesAction())
        throw new IllegalStateException("Native port click fell through to cancellation");
    } finally {
      mc.player.input.shiftKeyDown = false;
      if (sneak)
        mc.player.connection.send(
            new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
    }
  }
}
