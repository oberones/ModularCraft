package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
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

/** Disposable 16-module/24-cable fixtures; never included in the published mod. */
public final class NativeLfoFixture {
  private static final List<List<ModuleEntity>> PATCHES = new ArrayList<>();
  private static final List<Long> TIMES = new ArrayList<>();

  private NativeLfoFixture() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void build(ServerPlayer player, int x, int y, int z) {
    var runtime = ServerRuntime.get(player.getServer());
    var level = player.serverLevel();
    for (int patch = 0; patch < (Boolean.getBoolean("modularcraft.qa.load") ? 4 : 1); patch++) {
      List<ModuleEntity> modules = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        ModuleKind kind =
            i == 0
                ? ModuleKind.EUCLIDEAN_SEQUENCER
                : i < 4
                    ? ModuleKind.LFO
                    : i == 4
                        ? ModuleKind.OSCILLATOR
                        : i < 12
                            ? ModuleKind.FILTER
                            : i < 14 ? ModuleKind.ENVELOPE : ModuleKind.SPEAKER;
        BlockPos pos =
            new BlockPos(x + patch % 2 * 8 + i % 4 * 2, y, z + patch / 2 * 8 + i / 4 * 2);
        level.setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
        ModuleEntity entity = (ModuleEntity) level.getBlockEntity(pos);
        entity.initialize(player.getUUID());
        modules.add(entity);
        if (kind == ModuleKind.LFO) {
          set(runtime, player, entity, Parameter.LFO_WAVE, i);
          set(runtime, player, entity, Parameter.LFO_DIVISION, 0);
        }
        if (kind == ModuleKind.OSCILLATOR) set(runtime, player, entity, Parameter.DETUNE, 100);
        if (kind == ModuleKind.FILTER) {
          set(runtime, player, entity, Parameter.AMOUNT, 50);
          set(runtime, player, entity, Parameter.RESONANCE_DEPTH, 50);
        }
        if (kind == ModuleKind.ENVELOPE)
          for (Parameter p :
              List.of(
                  Parameter.ATTACK_DEPTH,
                  Parameter.DECAY_DEPTH,
                  Parameter.SUSTAIN_DEPTH,
                  Parameter.RELEASE_DEPTH)) set(runtime, player, entity, p, 50);
        if (i == 0) {
          set(runtime, player, entity, Parameter.TEMPO, 300);
          set(runtime, player, entity, Parameter.A_HITS, 16);
          set(runtime, player, entity, Parameter.B_HITS, 16);
        }
      }
      for (int i = 4; i < 11; i++)
        connect(runtime, player, modules, i, Port.AUDIO_OUT, i + 1, Port.AUDIO_IN);
      for (int i = 14; i < 16; i++)
        connect(runtime, player, modules, 11, Port.AUDIO_OUT, i, Port.AUDIO_IN);
      for (int i = 1; i < 4; i++)
        connect(runtime, player, modules, 0, Port.CLOCK_OUT, i, Port.CLOCK_IN);
      connect(runtime, player, modules, 0, Port.TRIGGER_A, 12, Port.GATE_IN);
      connect(runtime, player, modules, 0, Port.TRIGGER_B, 13, Port.GATE_IN);
      connect(runtime, player, modules, 12, Port.ENVELOPE_OUT, 14, Port.GAIN_IN);
      connect(runtime, player, modules, 13, Port.ENVELOPE_OUT, 15, Port.GAIN_IN);
      for (Port port : List.of(Port.ATTACK_IN, Port.DECAY_IN, Port.SUSTAIN_IN, Port.RELEASE_IN))
        connect(runtime, player, modules, 1, Port.CONTROL_OUT, 12, port);
      for (int i = 5; i < 9; i++)
        connect(
            runtime,
            player,
            modules,
            2,
            Port.CONTROL_OUT,
            i,
            i % 2 == 0 ? Port.RESONANCE_IN : Port.CUTOFF_IN);
      for (int i = 14; i < 16; i++) {
        var speaker = runtime.state().get(modules.get(i).state().id());
        runtime.state().enable(player.getUUID(), false, speaker.id(), speaker.revision(), true);
      }
      for (ModuleEntity entity : modules) runtime.attach(entity);
      PATCHES.add(modules);
    }
    System.out.println(
        "MODULARCRAFT_QA lfo fixture patches="
            + PATCHES.size()
            + " modules="
            + PATCHES.size() * 16
            + " cables="
            + PATCHES.size() * 24);
  }

  public static void tick(ServerPlayer player, int tick) {
    if (PATCHES.isEmpty()) return;
    var runtime = ServerRuntime.get(player.getServer());
    if (tick > 100) TIMES.add(runtime.lastTickNanos());
    // Start is range checked on drain, so load fixtures start each sequencer on separate ticks.
    if (tick >= 42 && tick < 42 + PATCHES.size()) {
      ModuleEntity seq = PATCHES.get(tick - 42).getFirst();
      player.connection.teleport(
          seq.getBlockPos().getX() + .5,
          seq.getBlockPos().getY(),
          seq.getBlockPos().getZ() - 1,
          0,
          8);
      ServerRuntime.enqueue(
          player,
          new Messages.Control(
              seq.state().id(),
              seq.state().revision(),
              Messages.Action.SEQUENCER_START,
              Parameter.TEMPO,
              0));
    }
    if (!Boolean.getBoolean("modularcraft.qa.load") && (tick == 60 || tick == 200 || tick == 300)) {
      ModuleEntity entity = PATCHES.getFirst().get(tick == 60 ? 1 : tick == 200 ? 5 : 12);
      BlockPos pos = entity.getBlockPos();
      player.connection.teleport(pos.getX() + .5, pos.getY(), pos.getZ() - 2, 0, 8);
      runtime.inspect(player, entity, true);
    }
    if (Boolean.getBoolean("modularcraft.qa.load") && tick == 60) {
      BlockPos pos = PATCHES.getFirst().get(15).getBlockPos();
      player.connection.teleport(pos.getX() + 1, pos.getY(), pos.getZ() + 1, 0, 8);
    }
    if (tick == Integer.getInteger("modularcraft.qa.ticks", 1200) - 20) {
      long[] times = TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
      long p95 = times[(int) (times.length * .95)];
      System.out.println(
          "MODULARCRAFT_QA lfo server samples="
              + times.length
              + " p95Ns="
              + p95
              + " maxNs="
              + times[times.length - 1]
              + " target2ms="
              + (p95 < 2000000 ? "PASS" : "FAIL"));
      for (List<ModuleEntity> patch : PATCHES) {
        var snapshot = runtime.currentPatch(patch.getFirst().state().id());
        if (!snapshot.sequencers().get(patch.getFirst().state().id()).running())
          throw new IllegalStateException("LFO load clock stopped");
      }
    }
  }

  private static void set(
      ServerRuntime runtime, ServerPlayer player, ModuleEntity module, Parameter p, double value) {
    var state = runtime.state().get(module.state().id());
    runtime.state().edit(player.getUUID(), false, state.id(), state.revision(), p, value);
  }

  private static void connect(
      ServerRuntime runtime,
      ServerPlayer player,
      List<ModuleEntity> modules,
      int from,
      Port out,
      int to,
      Port in) {
    var a = runtime.state().get(modules.get(from).state().id());
    var b = runtime.state().get(modules.get(to).state().id());
    runtime
        .state()
        .connect(
            player.getUUID(),
            false,
            new com.malevolentgods.modularcraft.domain.Cable(a.id(), out, b.id(), in),
            a.revision(),
            b.revision());
  }
}
