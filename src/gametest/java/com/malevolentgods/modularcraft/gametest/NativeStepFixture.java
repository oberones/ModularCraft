package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StepSequencerSettings;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/** Fresh melodic reference patch; load mode runs four 20-module patches. */
public final class NativeStepFixture {
  private static final List<ModuleEntity> SEQUENCERS = new ArrayList<>();
  private static final List<Long> TIMES = new ArrayList<>();

  private NativeStepFixture() {}

  // Borrowed server world remains owned by Minecraft.
  @SuppressWarnings("PMD.CloseResource")
  public static void build(ServerPlayer player, int x, int y, int z) {
    var runtime = ServerRuntime.get(player.getServer());
    boolean load = Boolean.getBoolean("modularcraft.qa.load");
    if (!load) {
      var level = player.serverLevel();
      level.setDayTime(6000);
      level.setWeatherParameters(24000, 0, false, false);
      level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer());
      level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.getServer());
    }
    for (int patch = 0; patch < (load ? 4 : 1); patch++) {
      ServerPlayer owner =
          load
              ? net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                  player.serverLevel(),
                  new com.mojang.authlib.GameProfile(
                      new java.util.UUID(0, 500 + patch), "StepLoad" + patch))
              : player;
      List<ModuleEntity> modules = new ArrayList<>();
      int count = load ? 20 : 4;
      for (int i = 0; i < count; i++) {
        ModuleKind kind =
            i == 0
                ? ModuleKind.STEP_SEQUENCER
                : i == 1
                    ? ModuleKind.OSCILLATOR
                    : i == 2
                        ? ModuleKind.AMP
                        : i == count - 1 ? ModuleKind.SPEAKER : ModuleKind.FILTER;
        BlockPos pos =
            new BlockPos(
                x + (load ? patch % 2 * 10 + i % 5 * 2 : 6 - i * 2),
                y,
                z + patch / 2 * 10 + i / 5 * 2);
        player
            .serverLevel()
            .setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
        var entity = (ModuleEntity) player.serverLevel().getBlockEntity(pos);
        entity.initialize(owner.getUUID());
        modules.add(entity);
        if (i == 0) {
          SEQUENCERS.add(entity);
          for (int step = 0; step < 16; step++)
            set(
                runtime,
                owner,
                entity,
                StepSequencerSettings.NOTES.get(step),
                60 + new int[] {0, 4, 7, 12}[step % 4]);
          set(runtime, owner, entity, Parameter.GATE_TIME, 80);
          if (load) {
            set(runtime, owner, entity, Parameter.TEMPO, 300);
            set(runtime, owner, entity, Parameter.REDSTONE_MODE, 1);
            player
                .serverLevel()
                .setBlock(
                    pos.below(),
                    net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(),
                    3);
          }
        }
        if (load && i == 1) set(runtime, owner, entity, Parameter.DETUNE, 100);
      }
      connect(runtime, owner, modules.get(0), Port.CV_OUT, modules.get(1), Port.PITCH_IN);
      connect(runtime, owner, modules.get(0), Port.GATE_OUT, modules.get(2), Port.GATE_IN);
      for (int i = 1; i < count - 1; i++)
        connect(runtime, owner, modules.get(i), Port.AUDIO_OUT, modules.get(i + 1), Port.AUDIO_IN);
      for (var module : modules) runtime.attach(module);
    }
    System.out.println(
        "MODULARCRAFT_QA step fixture patches="
            + SEQUENCERS.size()
            + " modules="
            + (load ? 80 : 4));
  }

  private static void set(
      ServerRuntime runtime, ServerPlayer player, ModuleEntity entity, Parameter p, double value) {
    var m = runtime.state().get(entity.state().id());
    runtime.state().edit(player.getUUID(), false, m.id(), m.revision(), p, value);
  }

  private static void connect(
      ServerRuntime runtime,
      ServerPlayer player,
      ModuleEntity a,
      Port output,
      ModuleEntity b,
      Port input) {
    var source = runtime.state().get(a.state().id());
    var dest = runtime.state().get(b.state().id());
    runtime
        .state()
        .connect(
            player.getUUID(),
            false,
            new Cable(source.id(), output, dest.id(), input),
            source.revision(),
            dest.revision());
  }

  public static void tick(ServerPlayer player, int tick) {
    if (SEQUENCERS.isEmpty()) return;
    var runtime = ServerRuntime.get(player.getServer());
    if (tick == 70 && !Boolean.getBoolean("modularcraft.qa.load"))
      for (var entity : SEQUENCERS)
        ServerRuntime.enqueue(
            player,
            new Messages.Control(
                entity.state().id(),
                entity.state().revision(),
                Messages.Action.SEQUENCER_START,
                Parameter.TEMPO,
                0));
    if (tick == 60) {
      var entity = SEQUENCERS.getFirst();
      var p = entity.getBlockPos();
      player.connection.teleport(p.getX() + .5, p.getY(), p.getZ() - 2, 0, 8);
      if (!Boolean.getBoolean("modularcraft.qa.load")) runtime.inspect(player, entity, true);
    }
    if (tick == 405 && !Boolean.getBoolean("modularcraft.qa.load")) {
      player.setGameMode(GameType.SPECTATOR);
      var p = SEQUENCERS.getFirst().getBlockPos();
      double dx = -1.8, dy = -2.5, dz = 8;
      player.connection.teleport(
          p.getX() - .7,
          p.getY() + 3 - player.getEyeHeight(),
          p.getZ() - 7.5,
          (float) Math.toDegrees(Math.atan2(-dx, dz)),
          (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
    if (tick > 100) TIMES.add(runtime.lastTickNanos());
    if (tick == Integer.getInteger("modularcraft.qa.ticks", 500) - 20) {
      long[] times = TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
      long p95 = times[(int) (times.length * .95)];
      System.out.println(
          "MODULARCRAFT_QA step server samples="
              + times.length
              + " p95Ns="
              + p95
              + " maxNs="
              + times[times.length - 1]
              + " target2ms="
              + (p95 < 2000000 ? "PASS" : "FAIL"));
    }
  }
}
