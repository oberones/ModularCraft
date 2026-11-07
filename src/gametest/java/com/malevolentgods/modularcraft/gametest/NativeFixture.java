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
  private static final List<BlockPos> SEQUENCERS = new ArrayList<>();
  private static BlockPos cableOrigin, keyboardPosition, oscillatorPosition;

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
          if (!System.getProperty("modularcraft.qa.readmeExample", "").isEmpty()) {
            NativeReadmeFixture.tick(performer, ticks);
            return;
          }
          if (Boolean.getBoolean("modularcraft.qa.showcase")) {
            NativeShowcaseFixture.tick(performer, ticks);
            return;
          }
          if (Boolean.getBoolean("modularcraft.qa.amp")) NativeAmpFixture.tick(performer, ticks);
          if (Boolean.getBoolean("modularcraft.qa.step")) NativeStepFixture.tick(performer, ticks);
          if (Boolean.getBoolean("modularcraft.qa.mixer"))
            NativeMixerFixture.tick(performer, ticks);
          if (Boolean.getBoolean("modularcraft.qa.lfo")) NativeLfoFixture.tick(performer, ticks);
          if (!SEQUENCERS.isEmpty()) {
            var runtime = ServerRuntime.get(event.getServer());
            if (ticks == 40)
              for (BlockPos position : SEQUENCERS) {
                var entity = (ModuleEntity) performer.serverLevel().getBlockEntity(position);
                ServerRuntime.enqueue(
                    performer,
                    new com.malevolentgods.modularcraft.network.Messages.Control(
                        entity.state().id(),
                        entity.state().revision(),
                        com.malevolentgods.modularcraft.network.Messages.Action.SEQUENCER_START,
                        Parameter.TEMPO,
                        0));
              }
            if (ticks == 60) {
              BlockPos first = SEQUENCERS.getFirst();
              performer.connection.teleport(
                  first.getX() + .5, first.getY(), first.getZ() - 2, 0, 8);
              runtime.inspect(
                  performer,
                  (ModuleEntity) performer.serverLevel().getBlockEntity(SEQUENCERS.getFirst()),
                  true);
            }
            if (ticks == 245 || ticks == 285) {
              performer
                  .serverLevel()
                  .setBlock(
                      SEQUENCERS.getFirst().above(),
                      (ticks == 245 ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState(),
                      3);
            }
            if (ticks == 360 || ticks == 400 || ticks == 440 || ticks == 480) {
              int view = (ticks - 360) / 40;
              BlockPos position = SEQUENCERS.get(view);
              double x = position.getX() + .5, z = position.getZ() + .5;
              if (view == 0) performer.connection.teleport(x, position.getY() - .6, z - 2, 0, 10);
              if (view == 1)
                performer.connection.teleport(x + 1.3, position.getY() - .4, z, 90, 25);
              if (view == 2) performer.connection.teleport(x, position.getY() - .6, z + 2, 180, 10);
              if (view == 3)
                performer.connection.teleport(x - 1.3, position.getY() - .4, z, -90, 25);
            }
          }
          if (cableOrigin != null && (ticks == 220 || ticks == 400)) cableView(performer, ticks);
          if (ticks == 60 && keyboardPosition != null)
            ServerRuntime.get(event.getServer())
                .inspect(
                    performer,
                    (ModuleEntity) performer.serverLevel().getBlockEntity(keyboardPosition),
                    true);
          if (ticks == 60 && oscillatorPosition != null)
            ServerRuntime.get(event.getServer())
                .inspect(
                    performer,
                    (ModuleEntity) performer.serverLevel().getBlockEntity(oscillatorPosition),
                    true);
          if (keyboardPosition == null && ticks >= 20 && (ticks - 20) % 40 == 0) {
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
    if (!System.getProperty("modularcraft.qa.readmeExample", "").isEmpty()) {
      NativeReadmeFixture.build(player, originX, y, originZ);
      return;
    }
    if (Boolean.getBoolean("modularcraft.qa.showcase")) {
      NativeShowcaseFixture.build(player, originX, y, originZ);
      return;
    }
    boolean cableVisuals = Boolean.getBoolean("modularcraft.qa.cables");
    for (int x = -2; x < 18; x++)
      for (int z = -2; z < 18; z++)
        level.setBlock(
            new BlockPos(originX + x, y - 1, originZ + z), Blocks.STONE.defaultBlockState(), 3);
    if (Boolean.getBoolean("modularcraft.qa.amp")) {
      NativeAmpFixture.build(player, originX, y, originZ);
      return;
    }
    if (Boolean.getBoolean("modularcraft.qa.step")) {
      NativeStepFixture.build(player, originX, y, originZ);
      return;
    }
    if (Boolean.getBoolean("modularcraft.qa.mixer")) {
      NativeMixerFixture.build(player, originX, y, originZ);
      return;
    }
    if (Boolean.getBoolean("modularcraft.qa.lfo")) {
      NativeLfoFixture.build(player, originX, y, originZ);
      return;
    }
    if (Boolean.getBoolean("modularcraft.qa.sequencer")) {
      buildSequencer(player, runtime, originX, y, originZ);
      return;
    }
    int patchCount = Boolean.getBoolean("modularcraft.qa.load") ? 4 : 1;
    for (int patch = 0; patch < patchCount; patch++) {
      List<ModuleEntity> modules = new ArrayList<>();
      int count = patchCount == 4 ? 16 : Boolean.getBoolean("modularcraft.qa.keyboard") ? 6 : 5;
      for (int i = 0; i < count; i++) {
        ModuleKind kind =
            patchCount == 4
                ? (i == 0
                    ? ModuleKind.OSCILLATOR
                    : i <= 10
                        ? ModuleKind.FILTER
                        : i <= 13 ? ModuleKind.ENVELOPE : ModuleKind.SPEAKER)
                : i == 3
                    ? ModuleKind.AMP
                    : i == count - 1 ? ModuleKind.SPEAKER : ModuleKind.values()[i];
        if (kind == ModuleKind.OSCILLATOR && Boolean.getBoolean("modularcraft.qa.square"))
          kind = ModuleKind.SQUARE_OSCILLATOR;
        BlockPos pos =
            new BlockPos(
                originX + (patch % 2) * 8 + (cableVisuals ? 3 - i % 4 : (i % 4) * 2),
                y,
                originZ + (patch / 2) * 8 + (i / 4) * 2);
        if (kind == ModuleKind.KEYBOARD) pos = new BlockPos(originX + 2, y, originZ - 2);
        level.setBlock(pos, ModularCraft.MODULES.get(kind).get().defaultBlockState(), 3);
        ModuleEntity entity = (ModuleEntity) level.getBlockEntity(pos);
        entity.initialize(player.getUUID());
        modules.add(entity);
        if (kind == ModuleKind.ENVELOPE) ENVELOPES.add(pos);
        if (kind == ModuleKind.KEYBOARD) keyboardPosition = pos;
        if (kind.isOscillator()
            && (Boolean.getBoolean("modularcraft.qa.detune")
                || Boolean.getBoolean("modularcraft.qa.square"))) oscillatorPosition = pos;
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
              modules.get(destinations[i] < 14 ? destinations[i] : destinations[i] - 13),
              destinations[i] < 14 ? Port.CUTOFF_IN : Port.RESONANCE_IN);
      } else {
        connect(runtime, player, modules.get(0), Port.AUDIO_OUT, modules.get(1), Port.AUDIO_IN);
        connect(runtime, player, modules.get(1), Port.AUDIO_OUT, modules.get(3), Port.AUDIO_IN);
        connect(runtime, player, modules.get(2), Port.ENVELOPE_OUT, modules.get(3), Port.GAIN_IN);
        connect(
            runtime, player, modules.get(3), Port.AUDIO_OUT, modules.get(count - 1), Port.AUDIO_IN);
        if (keyboardPosition != null) {
          connect(runtime, player, modules.get(4), Port.CV_OUT, modules.get(0), Port.PITCH_IN);
          connect(runtime, player, modules.get(4), Port.GATE_OUT, modules.get(2), Port.GATE_IN);
        }
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
    if (Boolean.getBoolean("modularcraft.qa.cableRemoval")) {
      player.setGameMode(GameType.SURVIVAL);
      player.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND,
          new net.minecraft.world.item.ItemStack(ModularCraft.TOOL.get()));
      player.setItemInHand(
          net.minecraft.world.InteractionHand.OFF_HAND,
          new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
      player.connection.teleport(originX + 1.5, y, originZ - 2, 0, 30);
    }
    if (keyboardPosition != null) player.connection.teleport(originX + 2.5, y, originZ - 5, 0, 8);
    if (oscillatorPosition != null) player.connection.teleport(originX + .5, y, originZ - 3, 0, 8);
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

  // The server owns this borrowed level.
  @SuppressWarnings("PMD.CloseResource")
  private static void buildSequencer(
      ServerPlayer player, ServerRuntime runtime, int x, int y, int z) {
    var level = player.serverLevel();
    List<ModuleEntity> modules = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      ModuleKind kind =
          i < 4
              ? ModuleKind.EUCLIDEAN_SEQUENCER
              : i < 6
                  ? ModuleKind.OSCILLATOR
                  : i < 10
                      ? ModuleKind.ENVELOPE
                      : i < 12 ? ModuleKind.FILTER : i < 14 ? ModuleKind.AMP : ModuleKind.SPEAKER;
      BlockPos position =
          i < 4
              ? new BlockPos(x + (i == 1 || i == 2 ? 6 : 0), y, z + (i >= 2 ? 6 : 0))
              : new BlockPos(x + 2 + (i - 4) % 2 * 2, y, z + 2 + (i - 4) / 2 * 2);
      var block = ModularCraft.MODULES.get(kind).get().defaultBlockState();
      if (i < 4)
        block =
            block.setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties
                    .HORIZONTAL_FACING,
                new net.minecraft.core.Direction[] {
                      net.minecraft.core.Direction.NORTH,
                      net.minecraft.core.Direction.EAST,
                      net.minecraft.core.Direction.SOUTH,
                      net.minecraft.core.Direction.WEST
                    }
                    [i]);
      level.setBlock(position, block, 3);
      ModuleEntity entity = (ModuleEntity) level.getBlockEntity(position);
      entity.initialize(player.getUUID());
      modules.add(entity);
      if (i < 4) SEQUENCERS.add(position);
      if (kind == ModuleKind.ENVELOPE) {
        set(runtime, player, entity, Parameter.ATTACK, .001);
        set(runtime, player, entity, Parameter.RELEASE, .08);
      }
      if (kind == ModuleKind.FILTER) set(runtime, player, entity, Parameter.AMOUNT, 70);
      if (i == 5) set(runtime, player, entity, Parameter.FREQUENCY, 330);
    }
    for (int i = 1; i < 4; i++)
      connect(runtime, player, modules.get(0), Port.CLOCK_OUT, modules.get(i), Port.CLOCK_IN);
    for (int i = 0; i < 4; i++)
      connect(
          runtime,
          player,
          modules.get(0),
          new Port[] {Port.TRIGGER_A, Port.TRIGGER_B, Port.TRIGGER_C, Port.TRIGGER_D}[i],
          modules.get(6 + i),
          Port.GATE_IN);
    for (int i = 0; i < 2; i++) {
      connect(
          runtime, player, modules.get(4 + i), Port.AUDIO_OUT, modules.get(10 + i), Port.AUDIO_IN);
      connect(
          runtime, player, modules.get(10 + i), Port.AUDIO_OUT, modules.get(12 + i), Port.AUDIO_IN);
      connect(
          runtime,
          player,
          modules.get(6 + i),
          Port.ENVELOPE_OUT,
          modules.get(12 + i),
          Port.GAIN_IN);
      connect(
          runtime,
          player,
          modules.get(8 + i),
          Port.ENVELOPE_OUT,
          modules.get(10 + i),
          Port.CUTOFF_IN);
      connect(
          runtime, player, modules.get(12 + i), Port.AUDIO_OUT, modules.get(14 + i), Port.AUDIO_IN);
      var speaker = runtime.state().get(modules.get(14 + i).state().id());
      runtime.state().enable(player.getUUID(), false, speaker.id(), speaker.revision(), true);
    }
    for (ModuleEntity module : modules) runtime.attach(module);
    player.connection.teleport(x + 3.5, y, z + 3.5, 0, 8);
    level.setDayTime(6000);
    System.out.println("MODULARCRAFT_QA sequencer fixture modules=16 cables=17");
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
