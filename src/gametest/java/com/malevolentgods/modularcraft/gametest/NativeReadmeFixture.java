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
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Three opt-in documentation scenes, built only in fresh native QA worlds. */
public final class NativeReadmeFixture {
  private static BlockPos origin;
  private static ModuleEntity keyboard, sequencer;

  private NativeReadmeFixture() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void build(ServerPlayer player, int x, int y, int z) {
    String example = System.getProperty("modularcraft.qa.readmeExample");
    if (!List.of("drone", "lead", "rhythms").contains(example))
      throw new IllegalArgumentException("qaReadmeExample must be drone, lead, or rhythms");
    origin = new BlockPos(x, y, z);
    var level = player.serverLevel();
    player.setGameMode(GameType.CREATIVE);
    level.setDayTime(6000);
    level.setWeatherParameters(24000, 0, false, false);
    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer());
    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.getServer());
    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.getServer());
    for (int dx = -18; dx <= 28; dx++)
      for (int dz = -24; dz <= 22; dz++)
        level.setBlock(origin.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
    if (example.equals("rhythms")) rhythms(player);
    else single(player, example.equals("lead"));
  }

  private static void single(ServerPlayer player, boolean lead) {
    var source =
        place(player, lead ? ModuleKind.SQUARE_OSCILLATOR : ModuleKind.OSCILLATOR, 6, 0, 0);
    var filter = place(player, ModuleKind.FILTER, 4, 0, 0);
    var amp = place(player, ModuleKind.AMP, 2, 0, 0);
    var speaker = place(player, ModuleKind.SPEAKER, 0, 0, 0);
    set(player, filter, Parameter.CUTOFF, lead ? 2000 : 1000);
    set(player, filter, Parameter.RESONANCE, 25);
    set(player, amp, Parameter.DRONE, lead ? 0 : 1);
    connect(player, source, Port.AUDIO_OUT, filter, Port.AUDIO_IN);
    connect(player, filter, Port.AUDIO_OUT, amp, Port.AUDIO_IN);
    connect(player, amp, Port.AUDIO_OUT, speaker, Port.AUDIO_IN);
    if (lead) {
      keyboard = place(player, ModuleKind.KEYBOARD, 4, 2, 2);
      connect(player, keyboard, Port.CV_OUT, source, Port.PITCH_IN);
      connect(player, keyboard, Port.GATE_OUT, amp, Port.GATE_IN);
    }
  }

  private static void rhythms(ServerPlayer player) {
    sequencer = place(player, ModuleKind.EUCLIDEAN_SEQUENCER, 10, 2, 2);
    var mixer = place(player, ModuleKind.MIXER, 2, 1, 2);
    var speaker = place(player, ModuleKind.SPEAKER, 0, 1, 2);
    for (int i = 0; i < 4; i++) {
      var source =
          place(
              player,
              i % 2 == 0 ? ModuleKind.OSCILLATOR : ModuleKind.SQUARE_OSCILLATOR,
              8,
              i,
              i * 2);
      var filter = place(player, ModuleKind.FILTER, 6, i, i * 2);
      var amp = place(player, ModuleKind.AMP, 4, i, i * 2);
      set(player, source, Parameter.FREQUENCY, 110 * (i + 1));
      set(player, filter, Parameter.RESONANCE, 25);
      set(player, amp, Parameter.ATTACK, .001);
      set(player, amp, Parameter.RELEASE, .080);
      connect(player, source, Port.AUDIO_OUT, filter, Port.AUDIO_IN);
      connect(player, filter, Port.AUDIO_OUT, amp, Port.AUDIO_IN);
      connect(player, amp, Port.AUDIO_OUT, mixer, MixerSettings.INPUTS.get(i));
      connect(
          player,
          sequencer,
          List.of(Port.TRIGGER_A, Port.TRIGGER_B, Port.TRIGGER_C, Port.TRIGGER_D).get(i),
          amp,
          Port.GATE_IN);
    }
    connect(player, mixer, Port.AUDIO_OUT, speaker, Port.AUDIO_IN);
  }

  public static void tick(ServerPlayer player, int tick) {
    if (origin == null) return;
    var runtime = ServerRuntime.get(player.getServer());
    if (tick == 40 && sequencer != null) {
      approach(player, sequencer);
      var state = runtime.state().get(sequencer.state().id());
      ServerRuntime.enqueue(
          player,
          new Messages.Control(
              state.id(), state.revision(), Messages.Action.SEQUENCER_START, Parameter.TEMPO, 0));
    }
    if (tick == 60 && keyboard != null) {
      approach(player, keyboard);
      runtime.inspect(player, keyboard, true);
    }
    if (tick == 280) {
      player.setGameMode(GameType.SPECTATOR);
      if (sequencer != null) view(player, 8.5, 6.2, -9.6, 5, 2, 3);
      else
        view(player, 5.3, keyboard == null ? 3.0 : 4.0, -7.5, 3.5, keyboard == null ? .5 : 1, .5);
    }
  }

  private static void approach(ServerPlayer player, ModuleEntity entity) {
    var pos = entity.getBlockPos();
    player.connection.teleport(pos.getX() + .5, pos.getY(), pos.getZ() - 2, 0, 10);
  }

  @SuppressWarnings("PMD.CloseResource")
  private static ModuleEntity place(ServerPlayer player, ModuleKind kind, int x, int y, int z) {
    var pos = origin.offset(x, y, z);
    var level = player.serverLevel();
    for (int dy = 1; dy <= y; dy++)
      level.setBlock(pos.below(dy), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
    level.setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
    var entity = (ModuleEntity) level.getBlockEntity(pos);
    entity.initialize(player.getUUID());
    ServerRuntime.get(player.getServer()).attach(entity);
    return entity;
  }

  private static void set(
      ServerPlayer player, ModuleEntity entity, Parameter parameter, double value) {
    var state = ServerRuntime.get(player.getServer()).state();
    var module = state.get(entity.state().id());
    state.edit(player.getUUID(), false, module.id(), module.revision(), parameter, value);
  }

  private static void connect(
      ServerPlayer player, ModuleEntity from, Port out, ModuleEntity to, Port in) {
    var state = ServerRuntime.get(player.getServer()).state();
    var a = state.get(from.state().id());
    var b = state.get(to.state().id());
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
