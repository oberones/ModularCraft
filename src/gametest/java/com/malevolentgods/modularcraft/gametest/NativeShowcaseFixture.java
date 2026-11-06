package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Documentation-only scene in a fresh QA world, excluded from distributed mod JARs. */
public final class NativeShowcaseFixture {
  private static final Map<ModuleKind, ModuleEntity> MODULES = new EnumMap<>(ModuleKind.class);
  private static BlockPos origin;

  private NativeShowcaseFixture() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void build(ServerPlayer player, int x, int y, int z) {
    origin = new BlockPos(x, y, z);
    var level = player.serverLevel();
    var runtime = ServerRuntime.get(player.getServer());
    player.setGameMode(GameType.CREATIVE);
    level.setDayTime(6000);
    level.setWeatherParameters(24000, 0, false, false);
    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer());
    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.getServer());
    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.getServer());
    for (int dx = -14; dx <= 24; dx++)
      for (int dz = -18; dz <= 16; dz++)
        level.setBlock(origin.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
    ModuleKind[] order = {
      ModuleKind.OSCILLATOR, ModuleKind.SQUARE_OSCILLATOR, ModuleKind.MIXER,
      ModuleKind.FILTER, ModuleKind.ENVELOPE, ModuleKind.SPEAKER,
      ModuleKind.KEYBOARD, ModuleKind.EUCLIDEAN_SEQUENCER, ModuleKind.LFO
    };
    for (int i = 0; i < order.length; i++) {
      int row = i / 3;
      BlockPos pos = origin.offset(4 - i % 3 * 2, row, row * 2);
      for (int height = 0; height < row; height++)
        level.setBlock(pos.below(row - height), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
      MODULES.put(order[i], place(player, pos, order[i]));
    }
    set(runtime, player, Parameter.CUTOFF, 500);
    set(runtime, player, Parameter.AMOUNT, 30);
    connect(player, ModuleKind.OSCILLATOR, Port.AUDIO_OUT, ModuleKind.MIXER, Port.AUDIO_IN_1);
    connect(
        player, ModuleKind.SQUARE_OSCILLATOR, Port.AUDIO_OUT, ModuleKind.MIXER, Port.AUDIO_IN_2);
    connect(player, ModuleKind.MIXER, Port.AUDIO_OUT, ModuleKind.FILTER, Port.AUDIO_IN);
    connect(player, ModuleKind.FILTER, Port.AUDIO_OUT, ModuleKind.SPEAKER, Port.AUDIO_IN);
    connect(player, ModuleKind.KEYBOARD, Port.CV_OUT, ModuleKind.OSCILLATOR, Port.PITCH_IN);
    connect(player, ModuleKind.KEYBOARD, Port.CV_OUT, ModuleKind.SQUARE_OSCILLATOR, Port.PITCH_IN);
    connect(
        player, ModuleKind.EUCLIDEAN_SEQUENCER, Port.TRIGGER_A, ModuleKind.ENVELOPE, Port.GATE_IN);
    connect(player, ModuleKind.ENVELOPE, Port.ENVELOPE_OUT, ModuleKind.SPEAKER, Port.GAIN_IN);
    connect(player, ModuleKind.EUCLIDEAN_SEQUENCER, Port.CLOCK_OUT, ModuleKind.LFO, Port.CLOCK_IN);
    connect(player, ModuleKind.LFO, Port.CONTROL_OUT, ModuleKind.FILTER, Port.CUTOFF_IN);
    var speaker = runtime.state().get(MODULES.get(ModuleKind.SPEAKER).state().id());
    runtime.state().enable(player.getUUID(), false, speaker.id(), speaker.revision(), true);
    MODULES.values().forEach(runtime::attach);
    player.connection.teleport(x + 2.5, y + 2, z + 2, 0, 0);
  }

  @SuppressWarnings("PMD.CloseResource")
  public static void tick(ServerPlayer player, int tick) {
    if (origin == null) return;
    var runtime = ServerRuntime.get(player.getServer());
    if (tick == 40) {
      var seq = runtime.state().get(MODULES.get(ModuleKind.EUCLIDEAN_SEQUENCER).state().id());
      ServerRuntime.enqueue(
          player,
          new Messages.Control(
              seq.id(), seq.revision(), Messages.Action.SEQUENCER_START, Parameter.TEMPO, 0));
    }
    if (tick == 60) {
      player.setGameMode(GameType.SPECTATOR);
      view(player, 7.5, 5.2, -9, 2.5, 1.7, 2);
    }
    if (tick >= 180 && tick <= 820 && (tick - 180) % 80 == 0) {
      ModuleKind kind = ModuleKind.values()[(tick - 180) / 80];
      BlockPos stage = origin.offset(12, 1, -6);
      player.serverLevel().setBlock(stage, Blocks.AIR.defaultBlockState(), 3);
      player.serverLevel().setBlock(stage.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
      place(player, stage, kind);
      view(player, 10.9, 2.2, -8.8, 12.5, 1.5, -5.5);
    }
  }

  @SuppressWarnings("PMD.CloseResource")
  private static ModuleEntity place(ServerPlayer player, BlockPos position, ModuleKind kind) {
    player
        .serverLevel()
        .setBlock(position, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
    var entity = (ModuleEntity) player.serverLevel().getBlockEntity(position);
    entity.initialize(player.getUUID());
    ServerRuntime.get(player.getServer()).attach(entity);
    return entity;
  }

  private static void set(
      ServerRuntime runtime, ServerPlayer player, Parameter parameter, double value) {
    var filter = runtime.state().get(MODULES.get(ModuleKind.FILTER).state().id());
    runtime.state().edit(player.getUUID(), false, filter.id(), filter.revision(), parameter, value);
  }

  private static void connect(
      ServerPlayer player, ModuleKind from, Port out, ModuleKind to, Port in) {
    var state = ServerRuntime.get(player.getServer()).state();
    var a = state.get(MODULES.get(from).state().id());
    var b = state.get(MODULES.get(to).state().id());
    state.connect(
        player.getUUID(), false, new Cable(a.id(), out, b.id(), in), a.revision(), b.revision());
  }

  private static void view(
      ServerPlayer player,
      double x,
      double y,
      double z,
      double targetX,
      double targetY,
      double targetZ) {
    double dx = targetX - x, dy = targetY - y, dz = targetZ - z;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    player.connection.teleport(
        origin.getX() + x,
        origin.getY() + y - player.getEyeHeight(),
        origin.getZ() + z,
        yaw,
        pitch);
  }
}
