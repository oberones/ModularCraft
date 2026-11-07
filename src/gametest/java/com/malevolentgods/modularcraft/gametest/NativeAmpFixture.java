package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Four articulated voices; disposable native UI and 64-module/72-cable load fixture. */
public final class NativeAmpFixture {
  private static final List<List<ModuleEntity>> PATCHES = new ArrayList<>();
  private static final List<Long> TIMES = new ArrayList<>();

  private NativeAmpFixture() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void build(ServerPlayer player, int x, int y, int z) {
    var runtime = ServerRuntime.get(player.getServer());
    boolean load = Boolean.getBoolean("modularcraft.qa.load");
    player.serverLevel().setDayTime(6000);
    for (int patch = 0; patch < (load ? 4 : 1); patch++) {
      List<ModuleEntity> modules = new ArrayList<>();
      for (int i = 0; i < (load ? 16 : 15); i++) {
        ModuleKind kind =
            i == 0
                ? ModuleKind.EUCLIDEAN_SEQUENCER
                : i < 5
                    ? (i % 2 == 0 ? ModuleKind.SQUARE_OSCILLATOR : ModuleKind.OSCILLATOR)
                    : i < 9
                        ? ModuleKind.AMP
                        : i == 9
                            ? ModuleKind.MIXER
                            : i == 10 || i == 15 ? ModuleKind.SPEAKER : ModuleKind.FILTER;
        BlockPos pos =
            new BlockPos(x + patch % 2 * 8 + i % 4 * 2, y, z + patch / 2 * 8 + i / 4 * 2);
        player
            .serverLevel()
            .setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
        ModuleEntity entity = (ModuleEntity) player.serverLevel().getBlockEntity(pos);
        entity.initialize(player.getUUID());
        modules.add(entity);
        if (kind.isOscillator()) set(runtime, player, entity, Parameter.FREQUENCY, 110 * i);
        if (load && kind == ModuleKind.OSCILLATOR)
          set(runtime, player, entity, Parameter.DETUNE, 100);
        if (kind == ModuleKind.AMP) {
          set(runtime, player, entity, Parameter.ATTACK, .001);
          set(runtime, player, entity, Parameter.RELEASE, .08);
        }
        if (kind == ModuleKind.FILTER) set(runtime, player, entity, Parameter.RESONANCE, 25);
      }
      for (int i = 0; i < 4; i++) {
        connect(
            runtime,
            player,
            modules,
            0,
            List.of(Port.TRIGGER_A, Port.TRIGGER_B, Port.TRIGGER_C, Port.TRIGGER_D).get(i),
            5 + i,
            Port.GATE_IN);
        connect(runtime, player, modules, 1 + i, Port.AUDIO_OUT, 11 + i, Port.AUDIO_IN);
        connect(runtime, player, modules, 11 + i, Port.AUDIO_OUT, 5 + i, Port.AUDIO_IN);
        connect(runtime, player, modules, 5 + i, Port.AUDIO_OUT, 9, MixerSettings.INPUTS.get(i));
      }
      connect(runtime, player, modules, 9, Port.AUDIO_OUT, 10, Port.AUDIO_IN);
      if (load) connect(runtime, player, modules, 9, Port.AUDIO_OUT, 15, Port.AUDIO_IN);
      for (ModuleEntity module : modules) runtime.attach(module);
      PATCHES.add(modules);
    }
    System.out.println(
        "MODULARCRAFT_QA amp fixture modules=" + (load ? 64 : 15) + " cables=" + (load ? 72 : 17));
  }

  public static void tick(ServerPlayer player, int tick) {
    if (PATCHES.isEmpty()) return;
    var runtime = ServerRuntime.get(player.getServer());
    boolean load = Boolean.getBoolean("modularcraft.qa.load");
    if (tick >= 40 && tick < 40 + PATCHES.size() * 10 && tick % 10 == 0) {
      ModuleEntity seq = PATCHES.get((tick - 40) / 10).getFirst();
      view(player, seq);
      var m = runtime.state().get(seq.state().id());
      ServerRuntime.enqueue(
          player,
          new Messages.Control(
              m.id(), m.revision(), Messages.Action.SEQUENCER_START, Parameter.TEMPO, 0));
    }
    if (!load && (tick == 80 || tick == 210)) {
      ModuleEntity module = PATCHES.getFirst().get(tick == 80 ? 5 : 10);
      view(player, module);
      runtime.inspect(player, module, true);
    }
    if (tick == 280) {
      player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      BlockPos origin = PATCHES.getFirst().getFirst().getBlockPos();
      player.connection.teleport(origin.getX() + 8, origin.getY() + 7, origin.getZ() - 7, 23, 35);
    }
    if (tick > 100) TIMES.add(runtime.lastTickNanos());
    if (tick == Integer.getInteger("modularcraft.qa.ticks", 600) - 20) {
      long[] times = TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
      long p95 = times[(int) (times.length * .95)];
      for (List<ModuleEntity> patch : PATCHES) {
        var seq = patch.getFirst().state();
        if (!runtime.currentPatch(seq.id()).sequencers().get(seq.id()).running())
          throw new IllegalStateException("Amp fixture sequencer is not running");
      }
      System.out.println("MODULARCRAFT_QA amp running sequencers PASS count=" + PATCHES.size());
      long speakers = runtime.state().modules().stream().filter(m -> m.enabled()).count();
      if (speakers != (load ? 8 : 1))
        throw new IllegalStateException("Default-enabled speakers not admitted: " + speakers);
      System.out.println("MODULARCRAFT_QA amp default-enabled speakers PASS count=" + speakers);
      System.out.println(
          "MODULARCRAFT_QA amp server p95Ns="
              + p95
              + " maxNs="
              + times[times.length - 1]
              + " target2ms="
              + (p95 < 2_000_000 ? "PASS" : "FAIL"));
    }
  }

  private static void view(ServerPlayer player, ModuleEntity module) {
    BlockPos p = module.getBlockPos();
    player.connection.teleport(p.getX() + .5, p.getY(), p.getZ() - 2, 0, 12);
  }

  private static void set(
      ServerRuntime runtime,
      ServerPlayer player,
      ModuleEntity entity,
      Parameter parameter,
      double value) {
    var m = runtime.state().get(entity.state().id());
    runtime.state().edit(player.getUUID(), false, m.id(), m.revision(), parameter, value);
  }

  private static void connect(
      ServerRuntime runtime,
      ServerPlayer player,
      List<ModuleEntity> modules,
      int from,
      Port output,
      int to,
      Port input) {
    var a = runtime.state().get(modules.get(from).state().id());
    var b = runtime.state().get(modules.get(to).state().id());
    runtime
        .state()
        .connect(
            player.getUUID(),
            false,
            new Cable(a.id(), output, b.id(), input),
            a.revision(),
            b.revision());
  }
}
