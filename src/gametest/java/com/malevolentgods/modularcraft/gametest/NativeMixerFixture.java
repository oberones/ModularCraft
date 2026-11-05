package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Disposable native six-module panel patch or four 16-module/24-cable stress patches. */
public final class NativeMixerFixture {
  private static final List<List<ModuleEntity>> PATCHES = new ArrayList<>();
  private static final List<Long> TIMES = new ArrayList<>();

  private NativeMixerFixture() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void build(ServerPlayer player, int x, int y, int z) {
    var runtime = ServerRuntime.get(player.getServer());
    var level = player.serverLevel();
    boolean load = Boolean.getBoolean("modularcraft.qa.load");
    for (int patch = 0; patch < (load ? 4 : 1); patch++) {
      List<ModuleEntity> modules = new ArrayList<>();
      int count = load ? 16 : 6;
      for (int i = 0; i < count; i++) {
        ModuleKind kind =
            i < 4
                ? (i % 2 == 0 ? ModuleKind.OSCILLATOR : ModuleKind.SQUARE_OSCILLATOR)
                : i < (load ? 14 : 5) ? ModuleKind.MIXER : ModuleKind.SPEAKER;
        BlockPos pos =
            new BlockPos(x + patch % 2 * 8 + i % 4 * 2, y, z + patch / 2 * 8 + i / 4 * 2);
        level.setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
        ModuleEntity entity = (ModuleEntity) level.getBlockEntity(pos);
        entity.initialize(player.getUUID());
        modules.add(entity);
        if (kind.isOscillator())
          set(runtime, player, entity, Parameter.FREQUENCY, new double[] {110, 220, 330, 440}[i]);
        if (load && kind == ModuleKind.OSCILLATOR)
          set(runtime, player, entity, Parameter.DETUNE, 100);
        if (load && kind == ModuleKind.MIXER)
          set(runtime, player, entity, MixerSettings.level(4), 100);
      }
      for (int i = 0; i < 4; i++)
        connect(runtime, player, modules, i, 4, MixerSettings.INPUTS.get(i));
      if (load)
        for (int i = 5; i < 14; i++)
          for (int input = 0; input < 2; input++)
            connect(runtime, player, modules, i - 1, i, MixerSettings.INPUTS.get(input));
      for (int i = load ? 14 : 5; i < count; i++) {
        connect(runtime, player, modules, load ? 13 : 4, i, Port.AUDIO_IN);
        var speaker = runtime.state().get(modules.get(i).state().id());
        runtime.state().enable(player.getUUID(), false, speaker.id(), speaker.revision(), true);
      }
      for (var module : modules) runtime.attach(module);
      PATCHES.add(modules);
    }
    System.out.println(
        "MODULARCRAFT_QA mixer fixture patches="
            + PATCHES.size()
            + " modules="
            + (load ? 64 : 6)
            + " cables="
            + (load ? 96 : 5));
  }

  public static void tick(ServerPlayer player, int tick) {
    if (PATCHES.isEmpty()) return;
    var runtime = ServerRuntime.get(player.getServer());
    boolean load = Boolean.getBoolean("modularcraft.qa.load");
    ModuleEntity mixer = PATCHES.getFirst().get(4);
    if (tick > 100) TIMES.add(runtime.lastTickNanos());
    if (tick == 60) {
      BlockPos pos = mixer.getBlockPos();
      player.connection.teleport(pos.getX() + .5, pos.getY(), pos.getZ() - 2, 0, 8);
      if (!load) runtime.inspect(player, mixer, true);
    }
    if (!load && tick == 455) {
      BlockPos pos = mixer.getBlockPos();
      player.connection.teleport(pos.getX() + .5, pos.getY() + .2, pos.getZ() - 1.5, 0, 30);
    }
    if (!load && tick == 450) {
      var tag = mixer.saveWithFullMetadata(player.registryAccess());
      var restored =
          (ModuleEntity)
              BlockEntity.loadStatic(
                  mixer.getBlockPos(), mixer.getBlockState(), tag, player.registryAccess());
      if (restored == null || !restored.state().values().equals(mixer.state().values()))
        throw new IllegalStateException("Mixer NBT roundtrip failed");
      System.out.println("MODULARCRAFT_QA mixer saved controls roundtrip PASS");
    }
    if (!load && tick == 480) {
      var id = mixer.state().id();
      player.serverLevel().setBlock(mixer.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
      if (runtime.state().modules().stream().anyMatch(m -> m.id().equals(id))
          || runtime.state().cables().stream()
              .anyMatch(c -> c.source().equals(id) || c.destination().equals(id)))
        throw new IllegalStateException("Mixer removal retained state");
      System.out.println("MODULARCRAFT_QA mixer removal cleanup PASS");
    }
    if (tick == Integer.getInteger("modularcraft.qa.ticks", 600) - 20) {
      long[] times = TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
      long p95 = times[(int) (times.length * .95)];
      System.out.println(
          "MODULARCRAFT_QA mixer server samples="
              + times.length
              + " p95Ns="
              + p95
              + " maxNs="
              + times[times.length - 1]
              + " target2ms="
              + (p95 < 2_000_000 ? "PASS" : "FAIL"));
    }
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
      List<ModuleEntity> modules,
      int from,
      int to,
      Port input) {
    var a = runtime.state().get(modules.get(from).state().id());
    var b = runtime.state().get(modules.get(to).state().id());
    runtime
        .state()
        .connect(
            player.getUUID(),
            false,
            new Cable(a.id(), Port.AUDIO_OUT, b.id(), input),
            a.revision(),
            b.revision());
  }
}
