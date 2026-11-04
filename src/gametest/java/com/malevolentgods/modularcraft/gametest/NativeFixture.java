package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Opt-in disposable fixture. Compiled only in the development test mod, excluded from both JARs.
 */
@Mod("modularcraft_tests")
public final class NativeFixture {
  private static final List<BlockPos> ENVELOPES = new ArrayList<>();
  private static ServerPlayer performer;
  private static int ticks;
  private static BlockPos cableOrigin;

  public NativeFixture() {
    if (!Boolean.getBoolean("modularcraft.qa")) return;
    NeoForge.EVENT_BUS.addListener(
        (PlayerEvent.PlayerLoggedInEvent event) -> {
          if (event.getEntity() instanceof ServerPlayer player && performer == null) {
            performer = player;
            ticks = 0;
          }
        });
    NeoForge.EVENT_BUS.addListener(
        (ServerTickEvent.Post event) -> {
          if (performer == null || event.getServer() != performer.getServer()) return;
          if (++ticks == 20) build(performer);
          if (cableOrigin != null && (ticks == 220 || ticks == 400)) cableView(performer, ticks);
          if (ticks >= 20 && (ticks - 20) % 40 == 0) {
            boolean on = ((ticks - 20) / 40) % 2 == 0;
            for (BlockPos pos : ENVELOPES)
              performer
                  .serverLevel()
                  .setBlock(
                      pos.below(),
                      on
                          ? Blocks.REDSTONE_BLOCK.defaultBlockState()
                          : Blocks.STONE.defaultBlockState(),
                      3);
          }
          if (ticks % 200 == 0)
            System.out.println(
                "MODULARCRAFT_QA server lastNs="
                    + ServerRuntime.get(event.getServer()).lastTickNanos()
                    + " maxNs="
                    + ServerRuntime.get(event.getServer()).maxTickNanos());
        });
  }

  // The server owns this borrowed level.
  @SuppressWarnings("PMD.CloseResource")
  private static void build(ServerPlayer player) {
    var level = player.serverLevel();
    ServerRuntime runtime = ServerRuntime.get(player.getServer());
    int y = player.blockPosition().getY();
    int originX = player.blockPosition().getX(), originZ = player.blockPosition().getZ() + 4;
    boolean cableVisuals = Boolean.getBoolean("modularcraft.qa.cables");
    for (int x = -2; x < 18; x++)
      for (int z = -2; z < 18; z++)
        level.setBlock(
            new BlockPos(originX + x, y - 1, originZ + z), Blocks.STONE.defaultBlockState(), 3);
    int patchCount = Boolean.getBoolean("modularcraft.qa.load") ? 4 : 1;
    for (int patch = 0; patch < patchCount; patch++) {
      List<ModuleEntity> modules = new ArrayList<>();
      int count = patchCount == 4 ? 16 : 4;
      for (int i = 0; i < count; i++) {
        ModuleKind kind =
            patchCount == 4
                ? (i == 0
                    ? ModuleKind.OSCILLATOR
                    : i <= 10
                        ? ModuleKind.FILTER
                        : i <= 13 ? ModuleKind.ENVELOPE : ModuleKind.SPEAKER)
                : ModuleKind.values()[i];
        BlockPos pos =
            new BlockPos(
                originX + (patch % 2) * 8 + (cableVisuals ? 3 - i % 4 : (i % 4) * 2),
                y,
                originZ + (patch / 2) * 8 + (i / 4) * 2);
        level.setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
        ModuleEntity entity = (ModuleEntity) level.getBlockEntity(pos);
        entity.initialize(player.getUUID());
        modules.add(entity);
        if (kind == ModuleKind.ENVELOPE) ENVELOPES.add(pos);
      }
      if (patchCount == 4) {
        set(runtime, player, modules.getFirst(), Parameter.FREQUENCY, (patch + 1) * 110);
        for (int i = 1; i <= 10; i++) {
          set(runtime, player, modules.get(i), Parameter.RESONANCE, 25);
          set(runtime, player, modules.get(i), Parameter.AMOUNT, 50);
        }
        for (int i = 0; i < 10; i++)
          connect(
              runtime, player, modules.get(i), Port.AUDIO_OUT, modules.get(i + 1), Port.AUDIO_IN);
        for (int i = 14; i < 16; i++)
          connect(runtime, player, modules.get(10), Port.AUDIO_OUT, modules.get(i), Port.AUDIO_IN);
        int[] destinations = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15};
        for (int i = 0; i < 12; i++)
          connect(
              runtime,
              player,
              modules.get(11 + i / 4),
              Port.ENVELOPE_OUT,
              modules.get(destinations[i]),
              destinations[i] < 14 ? Port.CUTOFF_IN : Port.GAIN_IN);
      } else {
        connect(runtime, player, modules.get(0), Port.AUDIO_OUT, modules.get(1), Port.AUDIO_IN);
        connect(runtime, player, modules.get(1), Port.AUDIO_OUT, modules.get(3), Port.AUDIO_IN);
        connect(runtime, player, modules.get(2), Port.ENVELOPE_OUT, modules.get(3), Port.GAIN_IN);
      }
      for (ModuleEntity module : modules) {
        runtime.attach(module);
        if (module.state().kind() == ModuleKind.SPEAKER)
          runtime
              .state()
              .enable(
                  player.getUUID(), false, module.state().id(), module.state().revision(), true);
        runtime.attach(module);
      }
    }
    player.teleportTo(originX + 7, y + 2, originZ - 2);
    if (cableVisuals) {
      cableOrigin = new BlockPos(originX, y, originZ);
      player.setGameMode(GameType.SPECTATOR);
      level.setDayTime(6000);
      cableView(player, 20);
    }
    System.out.println(
        "MODULARCRAFT_QA fixture patches="
            + patchCount
            + " modules="
            + runtime.state().modules().size()
            + " cables="
            + runtime.state().cables().size());
  }

  private static void cableView(ServerPlayer player, int tick) {
    double x = cableOrigin.getX(), y = cableOrigin.getY(), z = cableOrigin.getZ();
    if (tick == 20) player.connection.teleport(x + 2, y - .7, z - 3, 0, 8);
    else if (tick == 220) player.connection.teleport(x - 1, y + .1, z - 3, -45, 20);
    else player.connection.teleport(x + 2, y - .7, z + 3, 180, 8);
  }

  private static void set(
      ServerRuntime runtime,
      ServerPlayer player,
      ModuleEntity module,
      Parameter parameter,
      double value) {
    var state = runtime.state().get(module.state().id());
    runtime.state().edit(player.getUUID(), false, state.id(), state.revision(), parameter, value);
  }

  private static void connect(
      ServerRuntime runtime,
      ServerPlayer player,
      ModuleEntity source,
      Port out,
      ModuleEntity destination,
      Port in) {
    var a = runtime.state().get(source.state().id());
    var b = runtime.state().get(destination.state().id());
    runtime
        .state()
        .connect(
            player.getUUID(),
            false,
            new Cable(a.id(), out, b.id(), in),
            a.revision(),
            b.revision());
  }
}
